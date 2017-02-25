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
import dibd.daemon.NNTPConnection;
import dibd.daemon.NNTPInterface;
import dibd.daemon.command.TakeThisCommand.PostState;
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
	
	private String CMessageId = null;
	
	private boolean isHeadersOK = true;
	
	private String host; //for log ONLY
	
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
						CMessageId = command[1];
						if(Headers.matchMsgId(CMessageId)){
							//Message-Id
							//TODO:may be better search in cache only?
							Article art = StorageManager.current().getArticle(CMessageId, null);
							if (art != null){
								conn.println("435 Article already exist");
								state = PostState.Finished;
								return;
							}else{ // ok
								host = conn.getHost();
								
								conn.println("335 send article");

								rs = new ReceivingService("IHAVE", conn);
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
			int r = rs.readingHeaders(line, raw);
			switch (r) {
			case 0: return;//continue
			case 1:
				Log.get().log(Level.WARNING, "{0} No date or path or messageId in headers from {1}", new Object[]{CMessageId, host});
				isHeadersOK = false;
				conn.println("437 No date or path or messageId in headers");
				break;
			case 2: { //ok
				if (!rs.circleCheck()){
					conn.println("437 Circle detected");
					isHeadersOK = false;
				}else
				if(!rs.getMessageId().equals(CMessageId)){ //message-id check to be sure
					conn.println("437 message-id in command not equal one in headers");
					isHeadersOK = false;
				}else
				if(!rs.checkSender1()){//1) first check of sender
					isHeadersOK = false;// no answer
				}else
				if(!rs.checkSender2()){//2) second check of sender
					//conn.println("437 Group is unknown; do not retry"); //just like fuck off.
					conn.println("437 You do not have permission for this group; do not retry");
					isHeadersOK = false;
				}else
				/*if(!rs.checkSender3()){//3) third check for new senders in group
					isHeadersOK = false;
				}*/
				if(!rs.checkRef()){
					conn.println("437 no such thread for replay will be pulled");
					isHeadersOK = false;
				}
				break;
			}
			case 3:
				conn.println("437 No body for article.");
				state = PostState.Finished;
				return;
			case 4:
				conn.println("437 No such news group.");
				isHeadersOK = false;
				break;
			case 5:
				Log.get().severe("IHAVECommand: headers is too large for "+this.CMessageId+" from host "+host);
				conn.println("437 headers is too large.");
				isHeadersOK = false;
				break;
			}
			
			
			/*
			if (r == 0)
				return;//continue
			else
			if (r == 1){//error to recognize headers
				//state = PostState.Finished;
				isHeadersOK = false;
				Log.get().log(Level.WARNING, "{0} No date or path or messageId in headers from", CMessageId);
			}else if (r == 2){//OK Normal
				if (rs.circleCheck()){
					conn.println("437 Circle detected");
					isHeadersOK = false;
				}else
				if(!rs.getMessageId().equals(CMessageId)){ //message-id check to be sure
					conn.println("437 message-id in command not equal one in headers");
					isHeadersOK = false;
				}else
				if(!rs.checkSender1()){//1) first check of sender
					isHeadersOK = false;// no answer
				}else
				if(!rs.checkSender2()){//2) second check of sender
					//conn.println("437 Group is unknown; do not retry"); //just like fuck off.
					conn.println("437 You do not have permission for this group; do not retry");
					isHeadersOK = false;
				}else
				//if(!rs.checkSender3()){//3) third check for new senders in group
					//isHeadersOK = false;
				//}
				if(!rs.checkRef()){
					conn.println("437 no such thread for replay will be pulled");
					isHeadersOK = false;
				}
				
			}else if (r == 3){
				conn.println("500 No body for article.");
				state = PostState.Finished;
			}else if (r == 4){
				conn.println("500 No such news group.");
				isHeadersOK = false;
			}else if (r == 5){
				Log.get().severe("IHAVECommand: headers is too large for "+this.CMessageId+" from host "+host);
				conn.println("500 headers is too large.");
				isHeadersOK = false;
			}else if (r == 6){
				conn.println("435 no refered thread.");
				isHeadersOK = false;
			}*/
			state = PostState.ReadingBody;
			break;
		}
		case ReadingBody: {
			if(isHeadersOK){
				int r = rs.readingBody(line, raw);
				if (r == 1){
					state = PostState.Finished;
					postArticle(conn);
				}else if (r == 2 || r == 3){
					conn.println("437 No body or body is empty");
					state = PostState.Finished;
				}
			}else if (".".equals(line))
				state = PostState.Finished;
			break;
		}
		default: {
			// Should never happen
			Log.get().severe("IHAVE:"+CMessageId+":"+host+":processLine(): already finished...");
		}
		}
		
		return;
	}
	
	
	/**
	 * 
	 * For command it will parse article and decide post it or not
	 * 
	 * @param conn
	 * @param command
	 * @throws IOException
	 */
	private void postArticle(NNTPInterface conn) throws IOException {
		//circle check is not needed why?
		
		if (isHeadersOK){
			try{
				String res = rs.process(conn.getCurrentCharset());//, 437, 235);
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
				Log.get().log(Level.INFO, "{0} IHAVE there was error in headers, from {1}", new Object[]{CMessageId, host});
			}
		}else
			Log.get().log(Level.INFO, "{0} IHAVE there was error in main headers, from {1}", new Object[]{CMessageId, host});
	}
	
}