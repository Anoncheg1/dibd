/*
 *   SONEWS News Server
 *   see AUTHORS for the list of contributors
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
 * Syntax
 *     TAKETHIS message-id
 *
 *  Responses
 *     239 message-id   Article transferred OK
 *     439 message-id   Transfer rejected; do not retry
 *
 * @author Christian Lins
 * @author Dennis Schwerdel
 * @since n3tpd/0.1
 */
public class TakeThisCommand implements Command {

	enum PostState { WaitForLineOne, ReadingHeaders, ReadingBody, Finished };
	
	private PostState state = PostState.WaitForLineOne;
	
    @Override
    public String[] getSupportedCommandStrings() {
        return new String[] { "TAKETHIS" };
    }

    @Override
    public boolean hasFinished() {
        return this.state == PostState.Finished;
    }

    @Override
    public String impliedCapability() {
    	return "STREAMING";
    }

    @Override
    public boolean isStateful() {
        return true;
    }
    
    private ReceivingService rs = null;
	
	private String cMessageId = null;
	
	private boolean error = false;
	
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
				if (command.length == 2 && command[0].equalsIgnoreCase("TAKETHIS")) {
					
					cMessageId = command[1];
					if(Headers.matchMsgId(cMessageId)){
						Article art = StorageManager.current().getArticle(cMessageId, null, 1);
						if (art == null){
							host = conn.getHost();
							rs = new ReceivingService("TAKETHIS", conn, false, cMessageId);
							state = PostState.ReadingHeaders;
							break;
						}else //ok
							conn.println("439 " + cMessageId+" already have");
					}else
						conn.println("439 " + cMessageId + " wrong message-id format");

				}else
					conn.println("500 " + cMessageId + " invalid command usage");
			}else
				conn.println("483 " + cMessageId + " TLS required");
			conn.close();
			break;
		}
		case ReadingHeaders: {
			
			String res = rs.readingHeaders(line, raw);
			if (res == null)
				return; //continue
			else if(res.equals("ok")){//ok, reseived
				//checks after headers readed
				if (!rs.circleCheck()){
					conn.println("439 "+cMessageId+" Circle detected");
					error = true;
				}else
				if(!cMessageId.equals(rs.getMessageId())){ //message-id check to be sure
					conn.println("439 "+cMessageId+" message-id in command not equal one in headers");
					error = true;
				}else
				if(!rs.checkSender1()){//1) first check of sender
					//answer or not?
					conn.println("439 "+cMessageId+" Last sender in Path do not have permission for this group; do not retry");
					error = true;// no answer
				}else
				if(!rs.checkSender2()){//2) second check of sender
					conn.println("439 "+cMessageId+" You do not have permission for this group; do not retry");
					error = true;
				}else
				//if(!rs.checkSender3()){//3) third check for new senders in group
					//isHeadersOK = false;
				//}
				if(!rs.checkRef(null)){
					conn.println(" 439 "+cMessageId+" There is no such thread for replay.");
					error = true;
				}
				
			}else{
				conn.println("439 "+res);
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
					//not error to post
					conn.println("439 "+cMessageId+" Too big attachment.");
					postArticle(conn, 1);
					error = true; //we must reach "."
					break;
				}
				case 3:{
					conn.println("439 "+cMessageId+" Too big message.");
					error = true; //we must reach "."
					break;
				}
				case 4:{
					conn.println("439 "+cMessageId+" Wrong multipart format or empty body.");
					state = PostState.Finished;
					break;
				}
				default: {
					// Should never happen
					Log.get().severe("TAKETHIS:"+cMessageId+":"+host+":processLine() case ReadingBody: already finished...");
				}
				}
				
			}else if (".".equals(line)) //idling
				state = PostState.Finished;
			break;
		}
		default: {
			// Should never happen
			Log.get().severe("TakeThis::processLine(): already finished...");
						
		}
		}
		
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
				if (status == 0)
					if (res == null)
						conn.println("239 "+cMessageId+" article posted ok");
					else
						conn.println("439 "+ cMessageId + " "+res);
		
		
			} catch (UnsupportedEncodingException e) {
				conn.println("439 "+cMessageId+" Something wrong with message");
			} catch (StorageBackendException ex) {
				conn.println("500 "+cMessageId+" Internal server error");
			} catch (MessagingException|ParseException e) {
				conn.println("439 "+cMessageId+" Wrong MIME headers");
				Log.get().log(Level.INFO, "{0} TAKETHIS there was error in headers in miltiline, from {1}", new Object[]{cMessageId, host});
			}
		
	}
	
}
