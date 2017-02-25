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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.stream.Stream;

import javax.net.ssl.SSLSocket;

import dibd.config.Config;
import dibd.daemon.ChannelLineBuffers;
import dibd.daemon.LineEncoder;
import dibd.daemon.NNTPConnection;
import dibd.daemon.NNTPInterface;
import dibd.storage.StorageManager;
import dibd.storage.SubscriptionsProvider.FeedType;
import dibd.storage.article.Article;
import dibd.util.Log;

/**
 * Posts an Article to a NNTP server using the IHAVE command with 1 sec waiting between header and body.
 *
 * @author Christian Lins
 * @since sonews/0.5.0
 */
public class ArticlePusher {

	private final OutputStream out;
	private final BufferedReader inr;
	private final Socket socket;
	private final Charset charset = Charset.forName("UTF-8");
	private final ChannelLineBuffers lineBuffers = new ChannelLineBuffers();
	private final LineEncoder lineEncoder = new LineEncoder(charset, lineBuffers);
	private final String host; //for log messages

	public ArticlePusher(Socket socket, boolean TLSEnabled, String host) throws IOException{
		this.host = host;
		
		this.socket = FeedManager.getHelloFromServer(socket, TLSEnabled, host, FeedType.PUSH, charset);
		if (TLSEnabled){
			SSLSocket sslsocket = (SSLSocket) this.socket;
			this.out = sslsocket.getOutputStream();//->Output to remote NNTP server
			this.inr = new BufferedReader(new InputStreamReader( //<-INPUT from remote NNTP server
					sslsocket.getInputStream(), charset));
		}else{
			this.out = socket.getOutputStream();//->Output to remote NNTP server
			this.inr = new BufferedReader(new InputStreamReader( //<-INPUT from remote NNTP server
					socket.getInputStream(), charset));
		}
		//TODO:check capabilities for IHAVE
	}

	public void close() {
		try {
			this.out.write("QUIT\r\n".getBytes(charset));
			this.out.flush();
			this.inr.readLine();//we are polite
			this.socket.close();
		} catch (IOException e) {} 
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

	//TODO:add dont want to anc check time out
	private void prepareIHAVE(String messageId) throws IOException {
		String ihave = "IHAVE "+messageId+"\r\n";
		this.out.write(ihave.getBytes(charset));
		this.out.flush();

		//lastActivity = System.currentTimeMillis();
		String line = this.inr.readLine();
		if (line == null || line.startsWith("435")) {
			return;
		}else if (!line.startsWith("335"))
			throw new IOException(line);
			
	}
	
	private void writeLines() throws IOException{
		ByteBuffer buf = this.lineBuffers.getOutputBuffer();

		 while (buf != null) // There is data to be send
		 {
			 byte[] b = new byte[buf.remaining()];
			 buf.get(b);

			 this.out.write(b);
			 buf = this.lineBuffers.getOutputBuffer();

		 }	
	}
	
	private void checkErrors () throws IOException{
		//lastActivity = System.currentTimeMillis();
		String line = inr.readLine();

		if (line == null || !line.startsWith("235"))
			throw new IOException(line);
	}
	
	
	private void splitToBuffers(byte[] rawArticle) throws ClosedChannelException{
		int copy = rawArticle.length < ChannelLineBuffers.BUFFER_SIZE ?
				rawArticle.length : ChannelLineBuffers.BUFFER_SIZE; //lesser of two
		for (int pos = 0; pos < rawArticle.length; ){

			ByteBuffer buf = ChannelLineBuffers.newLineBuffer();
			buf.put(rawArticle, pos, copy);
			buf.flip();
			lineBuffers.addOutputBuffer(buf);

			int remain = rawArticle.length - (pos+copy);
			pos += copy;
			copy = remain < ChannelLineBuffers.BUFFER_SIZE ? 
					remain : ChannelLineBuffers.BUFFER_SIZE;//lesser of two
		}
	}
	
	
	/**
	 * Write article
	 * for local - build nntp message
	 * for remote - from Raw private field of Article
	 * 
	 * 
	 * @param art
	 * @throws IOException
	 */
	public void writeArticle(Article art) throws IOException {
		
		prepareIHAVE(art.getMessageId());
		
		
		byte[] rawArticle = art.getRaw();
		
		if(rawArticle == null){//local
			//header
			this.lineEncoder.encode(CharBuffer.wrap(art.buildNNTPMessage(this.charset, 1)));//(), this.charset).encode(lineBuffers);
			writeLines();
			
			try {Thread.sleep(2000);} catch (InterruptedException e) {} //2 sec is enough 
			if(inr.ready()){	//(My invention)
				checkErrors();
			}
			
			//body
			this.lineEncoder.encode(CharBuffer.wrap(art.buildNNTPMessage(charset, 2)));
			writeLines();
			this.out.write((NNTPConnection.NEWLINE).getBytes(charset));//it's add "\r\n" at the end of the body
		}else{ //NNTP cache
			//fast version
			splitToBuffers(rawArticle);	
			//slow version
			/*ByteBuffer rawBuffer = ByteBuffer.wrap(rawArticle);
			while (rawBuffer.hasRemaining()){
				ByteBuffer buf = ChannelLineBuffers.newLineBuffer();
				while (buf.hasRemaining()){
					if (rawBuffer.hasRemaining())
						buf.put(rawBuffer.get());
					else
						break;
				}
				buf.flip();
				lineBuffers.addOutputBuffer(buf);
			}*/
			
			writeLines();
		}
		this.out.write(".\r\n".getBytes(charset));
		this.out.flush();
		
		checkErrors();
	}
/*
	public Socket getSocket() {
		return this.socket;
	}

	public long getLastActivity() {
		return this.lastActivity;
	}	
	*/
}
