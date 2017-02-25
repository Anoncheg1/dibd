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

package dibd.feed;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import dibd.config.Config;
import dibd.daemon.NNTPConnection;
import dibd.storage.StorageBackendException;
import dibd.storage.StorageManager;
import dibd.storage.GroupsProvider.Group;
import dibd.util.Log;

/**
 * Pull an Article from a NNTP server using the NetNews and Article commands.
 *
 * @author .
 * @since nntpchan
 */
public class ArticlePullerOld {

	
	private class Endpoint {
        public PrintWriter out;
        public BufferedReader in;
        public Socket socket;
    }
	
	private Endpoint peer;
	private Endpoint looptome;
	
	/**
     * Connects to the NNTP server identified by host and port and
     * reads the first server HELLO message of the server.
     * @param inetAddress
     * @param port
     * @return
     * @throws IOException
     */
    private Endpoint connect(Socket socket) throws IOException{
        Endpoint ep = new Endpoint();

        // Connect to NNTP server
        ep.socket = socket;
        ep.out = new PrintWriter(new OutputStreamWriter(ep.socket.getOutputStream(), "UTF-8"));
        ep.in = new BufferedReader(new InputStreamReader(ep.socket.getInputStream(), "UTF-8"));
        String line = ep.in.readLine();
    	    
        if (line == null || !line.startsWith("200 ")) {
            throw new IOException("Invalid hello from server: " + line);
        }

        return ep;
    }
	
	public ArticlePullerOld(Socket socket) throws IOException{
		// Connect to NNTP server
		this.peer = connect(socket);
		this.looptome = connect(new Socket(InetAddress.getByName(null), Config.inst().get(Config.PORT, 119))); //loopback
		//TODO:check capabilities for NETNEWS
	}

	public void close() throws IOException {
		this.peer.out.print("QUIT\r\n");
        this.peer.out.flush();
        this.looptome.out.print("QUIT\r\n");
        this.looptome.out.flush();
        this.looptome.in.readLine();
        this.peer.in.readLine();
        this.peer.socket.close();
        this.looptome.socket.close();
	}
	/*
    protected void preparePOST() throws IOException {
        this.out.write("POST\r\n".getBytes(charset));
        this.out.flush();

        String line = this.inr.readLine();
        if (line == null || !line.startsWith("340 ")) {
            throw new IOException(line);
        }
    }

    protected void finishPOST() throws IOException {
        this.out.write("\r\n.\r\n".getBytes(charset));
        this.out.flush();
        String line = inr.readLine();
        if (line == null || (!line.startsWith("240 ") && !line.startsWith("441 "))) {
            throw new IOException(line);
        }
    }
	 */
	/*
	protected void prepareIHAVE(String messageId) throws IOException {
		String ihave = "IHAVE "+messageId+"\r\n";
		this.out.write(ihave.getBytes(charset));
		this.out.flush();

		String line = this.inr.readLine();
		if (line == null || !line.startsWith("335 ")) {
			throw new IOException(line);
		}
	}

	protected void finishIHAVE() throws IOException {
		this.out.write("\r\n.\r\n".getBytes(charset));
		this.out.flush();
		String line = inr.readLine();

		if (line == null || (line.startsWith("500 ") || line.startsWith("437 "))) {
			throw new IOException(line);
		}else if (line.startsWith("436 "))
			Log.get().warning("Error 436 IHAVE transfer later.");
	}
*/
	/*
    public void writeArticle(Article article) throws IOException,
            UnsupportedEncodingException {
        byte[] buf = new byte[512];
        ArticleInputStream in = new ArticleInputStream(article, charset);

        preparePOST();

        int len = in.read(buf);
        while (len != -1) {
            writeLine(buf, len);
            len = in.read(buf);
        }

        finishPOST();
    }
	 */
	/**
	 * Writes the raw content of an article to the remote server. This method
	 * does no charset conversion/handling of any kind so its the preferred
	 * method for sending an article to remote peers.
	 *
	 * @param rawArticle
	 * @throws IOException
	 */
	/*
	public void writeArticle(byte[] rawArticle, String messageId) throws IOException {
		//preparePOST();
		prepareIHAVE(messageId);
		writeLine(rawArticle, rawArticle.length);
		//finishPOST();
		finishIHAVE();
	}

	public void writeArticle(Article art) throws IOException {
		//preparePOST();
		prepareIHAVE(art.getMessageId());
		byte[] h = art.getSource(charset, 1);
		writeLine(h, h.length);//headers
		try {Thread.sleep(1000);} catch (InterruptedException e) {} //1 sec is enough
		if(inr.ready()){
			String line = inr.readLine();

			if (line == null || (!line.startsWith("500 ") && !line.startsWith("437 "))) {
				throw new IOException(line);
			}else {
				Log.get().warning("Error 436 IHAVE transfer later.");
				return;
			}	
		}
		byte[] b = art.getSource(charset, 2);
		writeLine(b, b.length);//headers

		//finishPOST();
		finishIHAVE();
	}
*/
	public void check(Group group) throws IOException, StorageBackendException {
		//TODO:what if socket is closed?
		//if(this.peer.socket.isClosed())
			
		long last_post = StorageManager.current().getLastPostOfGroup(group);
		if (last_post > 0 + 60*60*24)
			last_post -= 60*60*24; // - 1 day
		
		//NEWNEWS news,sci 1464210306
		//TODO:support for old format?
		//230 success
		StringBuilder buf = new StringBuilder();
		buf.append("NEWNEWS ").append(group.getName()).append(' ').append(last_post).append(NNTPConnection.NEWLINE);
		this.peer.out.print(buf.toString());
		this.peer.out.flush();
		
		String line = this.peer.in.readLine();
		
		if (line == null || !line.startsWith("230 ")) { //230 List of new articles follows (multi-line)
			throw new IOException(line);
		}
		
		List<String> messageIDs = new ArrayList<>();
        line = this.peer.in.readLine();
        
        while (line != null && !(".".equals(line.trim()))) {
        	messageIDs.add(line);
    //    	System.out.println(line);
            line = this.peer.in.readLine();
        }

        for (String mId : messageIDs) {
        	//System.out.println("1#"+mId+"#");
        	//System.out.println("2#"+mId.getBytes()+"#");
        	transferToItself(mId);
        }
	}
	
	
	private void transferToItself(String messageId)
            throws IOException {
        
        
        String line;

        //changeGroup(group, dst);

        this.looptome.out.print("IHAVE "+ messageId + NNTPConnection.NEWLINE);
        this.looptome.out.flush();
        line = this.looptome.in.readLine();
        if (line == null || !line.startsWith("335 ")) {
        	Log.get().fine("NEWNEWS"+ messageId + "we already have this or don't want it" );
        	return; //we don't want to receive
        }
        
        this.peer.out.print("ARTICLE " + messageId + NNTPConnection.NEWLINE);
        this.peer.out.flush();
        line = this.peer.in.readLine(); //read response for article request
        if (line == null) {
            Log.get().warning("Unexpected null reply from remote host");
            return;
        }
        if (line.startsWith("430 ")) {
            Log.get().log(Level.WARNING, "Message {0} not available at {1}",
                    new Object[]{messageId, "host"});
            return;
        }
        if (!line.startsWith("220 ")) {
            throw new IOException("Unexpected reply to ARTICLE");
        }
        
        for(;;) { //read from ARTICLE response and write to loopback IHAVE
            line = this.peer.in.readLine();
            if (line == null) {
                Log.get().warning("");
                break;
            }

            this.looptome.out.print(line);
            this.looptome.out.print(NNTPConnection.NEWLINE);

            if (".".equals(line.trim())) {
                // End of article stream reached
            	
                break;
            }
        }
        
        this.looptome.out.flush();
        
        line = this.looptome.in.readLine();//IHAVE response
        if (line != null)
        	if(line.startsWith("235 ")) {
        		Log.get().log(Level.INFO, "Message {0} successfully transmitted", messageId);
        	} else {
        		Log.get().log(Level.WARNING, "IHAVE: {0}", line);
        	}
    }
	
}
