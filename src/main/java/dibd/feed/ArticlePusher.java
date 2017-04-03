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
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

import javax.net.ssl.SSLSocket;

import dibd.daemon.ChannelLineBuffers;
import dibd.daemon.LineEncoder;
import dibd.daemon.NNTPConnection;
import dibd.storage.StorageManager;
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
	private final Charset charset = StandardCharsets.UTF_8;
	private final ChannelLineBuffers lineBuffers = new ChannelLineBuffers();
	private final LineEncoder lineEncoder = new LineEncoder(charset, lineBuffers);
	//private final String host; //for log messages

	public ArticlePusher(Socket socket, boolean TLSEnabled, String host) throws IOException{
		//this.host = host;
		
		this.socket = FeedManager.getHelloFromServer(socket, TLSEnabled, host, charset);
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

	//true no have
	//false already have
	private boolean prepareIHAVE(String messageId) throws IOException {
		String ihave = "IHAVE "+messageId+"\r\n";
		this.out.write(ihave.getBytes(charset));
		this.out.flush();

		//lastActivity = System.currentTimeMillis();
		String line = this.inr.readLine();
		if (line == null)
			throw new IOException(line);
		else if (line.startsWith("435") || line.startsWith("437")){ 
			return false;
		}else if (!line.startsWith("335"))
			throw new IOException(line);
		else
			return true;
			
	}
	

	
	//true - do not retry. break.
	private boolean checkErrors () throws IOException{
		String line = inr.readLine();

		if (line == null){
			throw new IOException(line);
		}else if (line.startsWith("437")){
			return true;
		}else if (!line.startsWith("235"))
			throw new IOException(line);
		
		return false;
	}
	
	

	
	
	/*private void splitToBuffers(FileInputStream fis) throws ClosedChannelException{
		int copy = fis.length < ChannelLineBuffers.BUFFER_SIZE ?
				fis.length : ChannelLineBuffers.BUFFER_SIZE; //lesser of two
		for (int pos = 0; pos < fis.length; ){

			ByteBuffer buf = ChannelLineBuffers.newLineBuffer();
			buf.put(fis, pos, copy);
			buf.flip();
			lineBuffers.addOutputBuffer(buf);

			int remain = fis.length - (pos+copy);
			pos += copy;
			copy = remain < ChannelLineBuffers.BUFFER_SIZE ? 
					remain : ChannelLineBuffers.BUFFER_SIZE;//lesser of two
		}
	}*/

	private void pushFileStream(FileInputStream fis) throws IOException{
		BufferedInputStream bfis = new BufferedInputStream(fis, 1024*200);//200KB buffer
		try{
			byte[] tmp = new byte[ChannelLineBuffers.BUFFER_SIZE];
			int count;
			int buffered = 0;

			while ((count = bfis.read(tmp)) != -1) {
				ByteBuffer buf = ChannelLineBuffers.newLineBuffer();
				assert buf.position() == 0;
				assert buf.capacity() >= ChannelLineBuffers.BUFFER_SIZE;
				buf.put(tmp,0,count).flip();

				lineBuffers.addOutputBuffer(buf);

				buffered += count;
				if (buffered > 100*1024){
					writeLines(); //we output every 100KB.
					buffered = 0;
				}
			}
			if (buffered != 0)
				writeLines();
		}finally{
			if (bfis!= null)
				bfis.close();
		}
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
	
	
	/**
	 * Write article
	 * for local - build nntp message
	 * for remote - from Raw private field of Article
	 * 
	 * 
	 * @param art
	 * @throws IOException
	 * @return false already have true success
	 */
	public boolean writeArticle(Article art) throws IOException{
		if (! prepareIHAVE(art.getMessageId()))
			return false;
		
		
		//byte[] rawArticle = art.getRaw();
		FileInputStream fis = StorageManager.nntpcache.getFileStream(art);
		try{
			if(fis == null){//local
				//header
				this.lineEncoder.encode(CharBuffer.wrap(art.buildNNTPMessage(this.charset, 1)));//(), this.charset).encode(lineBuffers);
				writeLines();




				//good working for diboard
				try {Thread.sleep(2000);} catch (InterruptedException e) {} //2 sec is enough 
				if(inr.ready()){	//(My invention)
					if (checkErrors())
						return false;
				}

				//body
				this.lineEncoder.encode(CharBuffer.wrap(art.buildNNTPMessage(charset, 2)));
				writeLines();
				this.out.write((NNTPConnection.NEWLINE).getBytes(charset));//it's add "\r\n" at the end of the body
			}else //NNTP cache
				pushFileStream(fis);
		}finally{
			if (fis != null)
				fis.close();
		}
		
		this.out.write(".\r\n".getBytes(charset));
		this.out.flush();
		
		if ( ! checkErrors())
			return true;
		else
			return false;
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
