/**
 * 
 */
package dibd.daemon.command;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.util.logging.Level;

import javax.mail.MessagingException;

import dibd.config.Config;
import dibd.daemon.NNTPInterface;
import dibd.storage.Headers;
import dibd.storage.StorageBackendException;
import dibd.storage.StorageManager;
import dibd.storage.article.Article;
import dibd.util.Log;

/**
 * Implementation of the IHAVE command, very similar to POST command. Differs from the POST command in that it is intended
 *  for use in transferring already-posted articles between hosts.
 *  Syntax
 *     IHAVE message-id
 *
 *   Responses
 *
 *   Initial responses
 *     335    Send article to be transferred
 *     435    Article not wanted
 *     436    Transfer not possible; try again later
 *
 *   Subsequent responses
 *     235    Article transferred OK
 *     436    Transfer failed; try again later
 *     437    Transfer rejected; do not retry
 *
 *   Parameters
 *     message-id    Article message-id
 *  
 *  
 * @author user
 *
 */
public class IhaveCommand implements Command{

	enum PostState { WaitForLineOne, ReadingHeaders, ReadingBody, Finished };
	
	private PostState state = PostState.WaitForLineOne;

	@Override
	public String[] getSupportedCommandStrings() {
		return new String[] { "IHAVE" };
	}
	
	@Override
	public boolean hasFinished() {
		return this.state == PostState.Finished;
	}

	@Override
	public String impliedCapability() {
		return "IHAVE";
	}

	@Override
	public boolean isStateful() {
		return true;
	}
	
	private ReceivingService rs = null;
	
	private String cMessageId = null;
	
	private boolean error = false;
	
	private String host; //for log ONLY
	
	public final static String noRef = "437 no such thread for replay.";
	
	//For ArticlePuller
	public boolean pullMode = false;
	
	public void setPullMode(){
		this.pullMode = true;
	}
	
	/**
	 * Process the given line String. line.trim() was called by NNTPConnection.
	 *
	 * @param conn
	 * @param line
	 * @throws java.io.IOException
	 * @throws dibd.storage.StorageBackendException
	 */
	@Override
	public void processLine(NNTPInterface conn, String line, byte[] raw) throws IOException, StorageBackendException {
		switch (state) {
		case WaitForLineOne: {

			if (Config.inst().get(Config.NNTPALLOW_UNAUTORIZED, false) || conn.isTLSenabled()){

				final String[] command = line.split("\\p{Space}+");

				if (command.length == 2) {
					if (command[0].equalsIgnoreCase("IHAVE")) {
						//335    Send article to be transferred
						//435    Article not wanted
						//436    Transfer not possible; try again later
						cMessageId = command[1];
						if(Headers.matchMsgId(cMessageId)){
							//Message-Id
							Article art = StorageManager.current().getArticle(cMessageId, null, 1);
							if (art != null){
								conn.println("435 Article already exist");
								state = PostState.Finished;
								return;
							}else{ // ok
								host = conn.getHost();
								
								conn.println("335 send article");

								rs = new ReceivingService("IHAVE", conn, this.pullMode, cMessageId);
								state = PostState.ReadingHeaders;
								return;
							}
						}
					}
				}
				conn.println("500 invalid command usage");
			}else
				conn.println("483 TLS required");

			this.state = PostState.Finished;
			break;
		}
		case ReadingHeaders: {
			String res = rs.readingHeaders(line, raw);
			if (res == null)
				return; //continue
			else if(res.equals("ok")){//ok, reseived
				//checks after headers readed
				if (!rs.circleCheck()){
					conn.println("437 Circle detected");
					error = true;
				}else
				if(!cMessageId.equals(rs.getMessageId())){ //message-id check to be sure
					conn.println("437 message-id in command not equal one in headers");
					error = true;
				}else
				if(!rs.checkSender1()){//1) first check of sender by the path
					conn.println("437 Last sender in Path do not have permission for this group; do not retry");
					error = true;// no answer
				}else
				if(!rs.checkSender2()){//2) second check of sender
					//conn.println("437 Group is unknown; do not retry"); //just like fuck off.
					conn.println("437 You do not have permission for this group; do not retry");
					error = true;
				}else
				//if(!rs.checkSender3()){//3) third check for new senders in group
					//isHeadersOK = false;
				//}
				if(!rs.checkRef()){
					conn.println(IhaveCommand.noRef); //437 no such thread for replay.
					error = true;
				}
				
			}else{
				conn.println("437 "+res);
				error = true;
			}
			
			//success or error
			state = PostState.ReadingBody;
			break;
			
		}
		case ReadingBody: {
			if(! error){
				
				int res = rs.readingBody(line, raw);
				switch(res){
				case 0: return;//continue
				case 1:{ //full seccess
					state = PostState.Finished;
					postArticle(conn, 0);
					break;
				}
				case 2:{//success without attachment
					//no error to post
					conn.println("235 Too big attachment."); //235 to signal ArticlePuller that thread accepted
					postArticle(conn, 1);
					error = true; //we must reach "."
					break;
				}
				case 3:{
					conn.println("437 Too big message.");
					error = true; //we must reach "."
					break;
				}
				case 4:{
					conn.println("437 Wrong multipart format or empty body.");
					state = PostState.Finished;
					break;
				}
				default: {
					// Should never happen
					Log.get().severe("IHAVE:"+cMessageId+":"+host+":processLine() case ReadingBody: already finished...");
				}
				}
				
			}else if (".".equals(line)) //idling
				state = PostState.Finished;
			break;
		}
		default: {
			// Should never happen
			Log.get().severe("IHAVE:"+cMessageId+":"+host+":processLine(): already finished...");
		}
		}
		
		return;
	}
	
	
	/**
	 * 
	 * For command it will parse article and decide post it or not
	 * 
	 * @param conn
	 * @param status 0 - normal, 1 - file was not readed (too large).
	 * @throws IOException
	 */
	private void postArticle(NNTPInterface conn, int status) throws IOException {
		
		if (! error)
			try{
				String res = rs.process(status);
				if (status == 0) // for 1 we work silent here
					if (res == null)
						conn.println("235 article posted ok");
					else
						conn.println("437 "+res);
			} catch (UnsupportedEncodingException e) {
				conn.println("437 Something wrong with message");
			} catch (StorageBackendException ex) {
				ex.printStackTrace();
				conn.println("500 Internal server error");
			} catch (MessagingException|ParseException e) {
				conn.println("437 Wrong MIME headers");
				Log.get().log(Level.INFO, "{0} IHAVE there was error in headers, from {1}", new Object[]{cMessageId, host});
			}
	}
	
}