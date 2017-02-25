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
import dibd.daemon.command.IhaveCommand.PostState;
import dibd.storage.StorageBackendException;
import dibd.util.Log;

/**
 * Implementation of the POST command. This command requires multiple lines from
 * the client, so the handling of asynchronous reading is a little tricky to
 * handle.
 * UTF-8 only
 * Content-Transfer-Encoding: 8bit (by default)
 * Content-Type: text/plain or 
 * multipart/mixed
 * Content-Transfer-Encoding: base64 only
 * 
 * unlimited lines splits to 512 lines, (' ' + next_line).
 * when line length 512 it will add next_line to it without first (' ') symbol.
 * 
 * 
   Initial responses
     340    Send article to be posted
     440    Posting not permitted

   Subsequent responses
     240    Article received OK
     441    Posting failed
 *
 * @author Christian Lins
 * @since sonews/0.5.0
 */
public class PostCommand implements Command {
	//TODO: проверить на работу с другим сервером.
	//TODO: максимизировать соответствие стандартам, убрать ручные способы
	/**
	 * States of the POST command's finite state machine.
	 */
	enum PostState { WaitForLineOne, ReadingHeaders, ReadingBody, Finished }

 	private PostState state = PostState.WaitForLineOne;
	
	@Override
	public String[] getSupportedCommandStrings() {
		return new String[] { "POST" };
	}

	@Override
	public boolean hasFinished() {
		return this.state == PostState.Finished;
	}

	@Override
	public String impliedCapability() {
		return null;
	}

	@Override
	public boolean isStateful() {
		return true;
	}

	private ReceivingService rs = null;
	
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
	// TODO: Refactor this method to reduce complexity!
	public void processLine(NNTPInterface conn, String line, byte[] raw) throws IOException, StorageBackendException {
		switch (state) {
		case WaitForLineOne: {
			
			if (Config.inst().get(Config.NNTPALLOW_UNAUTORIZED, false) || conn.isTLSenabled()){


				if (line.equalsIgnoreCase("POST")) { //ok
					host = conn.getHost();
					conn.println("340 send article to be posted. End with <CR-LF>.<CR-LF>");
					rs = new ReceivingService("POST", conn);
					state = PostState.ReadingHeaders;
					return;
				} else 
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
				isHeadersOK = false;
				conn.println("441 No date or path or messageId in headers");
				break;
			case 2: //ok
				break;
			case 3:
				conn.println("441 No body for article.");
				state = PostState.Finished;
				return;
			case 4:
				conn.println("441 No such news group.");
				isHeadersOK = false;
				break;
			case 5:
				conn.println("441 headers is too large.");
				isHeadersOK = false;
				break;
			}
			
			
			/*if (r == 1){//error
				state = PostState.Finished;
			}else if (r == 2){
				state = PostState.ReadingBody;
			}else if (r == 3){
				postArticle(conn);
				state = PostState.Finished;
			}*/
			state = PostState.ReadingBody;
			break;
		}
		case ReadingBody: {
			int r = rs.readingBody(line, raw); 
			if (r == 1 || r == 2){
				state = PostState.Finished;
				postArticle(conn);
			}else if (r == 3)
				state = PostState.Finished;
			break;
		}
		default: {
			// Should never happen
			Log.get().severe("PostCommand::processLine(): already finished...");
		}
		}
	}

	
	private void postArticle(NNTPInterface conn) throws IOException {
		if (isHeadersOK)
			try{
				rs.process(conn.getCurrentCharset());
				String res = rs.process(conn.getCurrentCharset());//, 437, 235);
				if (res == null)
					conn.println("240 article posted ok");
				else
					conn.println("441 "+res);
			} catch (UnsupportedEncodingException e) {
				conn.println("441 Something wrong with message");
			} catch (StorageBackendException ex) {
				ex.printStackTrace();
				conn.println("500 Internal server error");
			} catch (MessagingException|ParseException e) {
				conn.println("441 Wrong MIME headers");
				Log.get().log(Level.INFO, "POST there was error in headers, from {0}", new Object[]{host});
			}
	}
	
	
}
