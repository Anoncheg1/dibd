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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.util.logging.Level;

import javax.mail.MessagingException;

import dibd.config.Config;
import dibd.daemon.NNTPConnection;
import dibd.daemon.NNTPInterface;
import dibd.daemon.command.IhaveCommand.PostState;
import dibd.storage.Headers;
import dibd.storage.StorageBackendException;
import dibd.storage.StorageManager;
import dibd.storage.GroupsProvider.Group;
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
				if (command.length != 1) {
					if (command[0].equalsIgnoreCase("TAKETHIS")) {
						CMessageId = command[1];
						if(Headers.matchMsgId(CMessageId)){
							Article art = StorageManager.current().getArticle(CMessageId, null);
							if (art != null){
								conn.println("439 " + CMessageId+" already have");
								isHeadersOK = false;
								state = PostState.ReadingBody;//do nothing. we can't just halt
								return;
							}else{ //ok
								host = conn.getHost();
								rs = new ReceivingService("TAKETHIS", conn);
								state = PostState.ReadingHeaders;
								return;
							}
						}
					}
				}
				conn.println("500 invalid command usage");
			}else
				conn.println("483 TLS required");
			state = PostState.Finished;
			break;
		}
		case ReadingHeaders: {
			int r = rs.readingHeaders(line, raw);
			switch (r) {
			case 0: return;//continue
			case 1:
				conn.println("439 "+CMessageId+" posting failed - invalid header");
				isHeadersOK = false;
				break;
			case 2: { //ok
				if (!rs.circleCheck()){
					conn.println("439 "+ CMessageId+"Circle detected");
					isHeadersOK = false;
				}else
				if(!rs.getMessageId().equals(CMessageId)){ //message-id check to be sure
					conn.println("439 "+CMessageId+" message-id in command not equal one in headers");
					isHeadersOK = false;
				}else
				if(!rs.checkSender1()){//1) first check of sender
					conn.println("439 "+CMessageId+" there is no such peer in supscription list.");
					isHeadersOK = false;
				}else
				if(!rs.checkSender2()){//2) second check of sender
					//conn.println("437 Group is unknown; do not retry"); //just like fuck off.
					conn.println("439 "+CMessageId+" You do not have permission for this group; do not retry");
					isHeadersOK = false;
				}
				//if(!rs.checkSender3()){//3) third check for new senders in group
				//isHeadersOK = false;
				//}
				break;
			}
			case 3:
				conn.println("439 "+CMessageId+" No body for article.");
				state = PostState.Finished;
				return;
			case 4:
				conn.println("439 "+CMessageId+" No such news group.");
				isHeadersOK = false;
				break;
			case 5:
				Log.get().severe("TAKETHISCommand: headers is too large for"+rs.getMessageId());
				conn.println("439 "+CMessageId+" headers is too large.");
				isHeadersOK = false;
				break;
			}
			/*
			if (r == 0)
				return;//continue
			else 
			if (r == 1){//error to recognize headers
				//state = PostState.Finished;
				conn.println("500 "+CMessageId+" posting failed - invalid header");
				isHeadersOK = false;
			}else if (r == 2){//OK Normal
				if (rs.circleCheck()){
					conn.println("439 "+ CMessageId+"Circle detected");
					isHeadersOK = false;
				}else
				if(!rs.getMessageId().equals(CMessageId)){ //message-id check to be sure
					conn.println("500 "+CMessageId+" message-id in command not equal one in headers");
					isHeadersOK = false;
				}else
				if(!rs.checkSender1()){//1) first check of sender
					conn.println("500 "+CMessageId+" there is no suck peer in supscription list.");
					isHeadersOK = false;
				}else
				if(!rs.checkSender2()){//2) second check of sender
					//conn.println("437 Group is unknown; do not retry"); //just like fuck off.
					conn.println("439 "+CMessageId+" You do not have permission for this group; do not retry");
					isHeadersOK = false;
				}
				//if(!rs.checkSender3()){//3) third check for new senders in group
					//isHeadersOK = false;
				//}
			}else if (r == 3){
				conn.println("439 "+CMessageId+" No body for article.");
				state = PostState.Finished;
			}else if (r == 4){
				conn.println("439 "+CMessageId+" No such news group.");
				isHeadersOK = false;
			}else if (r == 5){
				Log.get().severe("TAKETHISCommand: headers is too large for"+rs.getMessageId());
				conn.println("500 "+CMessageId+" headers is too large.");
				isHeadersOK = false;
			}
			*/
			state = PostState.ReadingBody;
			break;
		}
		case ReadingBody: {
			if(isHeadersOK){
				int r = rs.readingBody(line, raw);

				if (r == 1){//ok
					state = PostState.Finished;
					postArticle(conn);
				}else if (r == 2 || r == 3){
					conn.println("439 "+CMessageId+" No body or body is empty");
					state = PostState.Finished;
				}
			}else if (".".equals(line))
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
	 * @param command
	 * @throws IOException
	 */
	private void postArticle(NNTPInterface conn) throws IOException {
		if (isHeadersOK){
			try{
				String res = rs.process(conn.getCurrentCharset());
				if (res ==null)
					conn.println("239 "+CMessageId+" article posted ok");
				else
					conn.println("439 "+ CMessageId + " "+res);
			} catch (UnsupportedEncodingException e) {
				conn.println("439 "+CMessageId+" Something wrong with message");
			} catch (StorageBackendException ex) {
				ex.printStackTrace();
				conn.println("500 "+CMessageId+" Internal server error");
			} catch (MessagingException|ParseException e) {
				conn.println("439 "+CMessageId+" Wrong MIME headers");
				Log.get().log(Level.INFO, "{0} TAKETHIS there was error in headers in miltiline, from {1}", new Object[]{CMessageId, host});
			}
		}else
			Log.get().log(Level.INFO, "{0} TAKETHIS there was error in main headers, from {1}", new Object[]{CMessageId, host});
	}
	
}
