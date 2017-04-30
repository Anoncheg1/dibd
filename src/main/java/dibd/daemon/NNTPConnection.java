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
package dibd.daemon;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;

import dibd.daemon.command.Command;
import dibd.storage.StorageBackendException;
import dibd.storage.article.NNTPArticle;
import dibd.storage.GroupsProvider.Group;
import dibd.util.Log;

/**
 * For every SocketChannel (so TCP/IP connection) there is an instance of this
 * class for UTF-8.
 *
 * @author Christian Lins
 * @since sonews/0.5.0
 */
public class NNTPConnection implements NNTPInterface{

    public static final String NEWLINE = "\r\n"; // RFC defines this as newline
    //public static final String MESSAGE_ID_PATTERN = "<[^(>|@)]+@[^(>|@)]+>";//"<[^>]+>";
    public static final String MESSAGE_ID_PATTERN = "<[\\w\\.$]+@[\\w.-]+>";
    private static final Timer cancelTimer = new Timer(true); // Thread-safe?
                                                              // True for run as
                                                              // daemon
    /** SocketChannel is generally thread-safe */
    private SocketChannel channel;
    private Charset charset = StandardCharsets.UTF_8;
    private Command command = null;
    
    //private Article currentArticle = null;
    private Group currentGroup = null;
    private volatile long lastActivity = System.currentTimeMillis();
    private final ChannelLineBuffers lineBuffers = new ChannelLineBuffers();
    private int readLock = 0;
    private final Object readLockGate = new Object();
    private SelectionKey writeSelKey = null;
    private final LineEncoder lineEncoder = new LineEncoder(charset, lineBuffers);
    private final CharsetEncoder ascii = StandardCharsets.US_ASCII.newEncoder(); 
    private TLS tls = null;
    private boolean tlsenabled = false;


	public NNTPConnection(final SocketChannel channel) throws IOException {
        if (channel == null) {
            throw new IllegalArgumentException("channel is null");
        }
        
        this.channel = channel;
    }

    /**
     * Tries to get the read lock for this NNTPConnection. This method is
     * Thread- safe and returns true of the read lock was successfully set. If
     * the lock is still hold by another Thread the method returns false.
     */
    boolean tryReadLock() {
        // As synchronizing simple types may cause deadlocks,
        // we use a gate object.
        synchronized (readLockGate) {
            if (readLock != 0) {
                return false;
            } else {
                readLock = Thread.currentThread().hashCode();
                return true;
            }
        }
    }

    /**
     * Releases the read lock in a Thread-safe way.
     *
     * @throws IllegalMonitorStateException
     *             if a Thread not holding the lock tries to release it.
     */
    void unlockReadLock() {
        synchronized (readLockGate) {
            if (readLock == Thread.currentThread().hashCode()) {
                readLock = 0;
            } else {
                throw new IllegalMonitorStateException();
            }
        }
    }

    /**
     * For ChannelReader
     * 
     * @return Current input buffer of this NNTPConnection instance.
     */
    public ByteBuffer getInputBuffer() {
        return this.lineBuffers.getInputBuffer();
    }

    /**
     * @return Output buffer of this NNTPConnection which has at least one byte
     *         free storage.
     */
    public ByteBuffer getOutputBuffer() {
        return this.lineBuffers.getOutputBuffer();
    }

    /**
     * @return ChannelLineBuffers instance associated with this NNTPConnection.
     */
    public ChannelLineBuffers getBuffers() {
        return this.lineBuffers;
    }

    /**
     * @return true if this connection comes from a local remote address.
     */
    public boolean isLocalConnection() {
        return "localhost".equalsIgnoreCase(((InetSocketAddress) this.channel.socket()
                .getRemoteSocketAddress()).getHostName());
    }

    void setWriteSelectionKey(SelectionKey selKey) {
        this.writeSelKey = selKey;
    }

    public void shutdownInput() {
        //try {
            // Closes the input line of the channel's socket, so no new data
            // will be received and a timeout can be triggered.
            //this.channel.socket().shutdownInput(); // this will close whole socket.
        	Selector readSelector = ChannelReader.getInstance().getSelector();
        	SelectionKey sk = this.channel.keyFor(readSelector);
        	if(sk != null)
        		sk.cancel();
        /*} catch (IOException ex) {
            Log.get().log(Level.WARNING,
                    "Exception in NNTPConnection.shutdownInput(): {0}", ex);
        }*/
    }

    public void shutdownOutput() {
        cancelTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    // Closes the output line of the channel's socket.
                    //channel.socket().shutdownOutput();
                    channel.close();
                    Log.get().log(Level.INFO,
                            "NNTPConnection connection {0} closed successfully", channel.socket().getRemoteSocketAddress());
                } catch (SocketException ex) {
                    // Socket was already disconnected
                    Log.get().log(Level.INFO,
                            "NNTPConnection.shutdownOutput(): {0}", ex);
                //} catch (ClosedChannelException ex){//we    
                } catch (IOException ex) {
                    Log.get().log(Level.WARNING,
                            "NNTPConnection.shutdownOutput(): {0}", ex);
                }
            }
        }, 3000);
    }

    public SocketChannel getSocketChannel() {
        return this.channel;
    }

    /*public Article getCurrentArticle() {
        return this.currentArticle;
    }*/

    public Charset getCurrentCharset() {
        return this.charset;
    }
    
    public Group getCurrentGroup() {
        return this.currentGroup;
    }

    /*public void setCurrentArticle(final Article article) {
        this.currentArticle = article;
    }*/

    public void setCurrentGroup(final Group group) {
        this.currentGroup = group;
    }

    public long getLastActivity() {
        return this.lastActivity;
    }

    
    static private String nntpchankeepalive = "CHECK <keepalive@dummy.tld>"; //nntpchan shit
//    static private String nntpchankeepalive2 = "500 <keepalive@dummy.tld> ok"; //nntpchan shit. he don't want it.
    
    /**
     * Due to the readLockGate there is no need to synchronize this method.
     *
     * @param raw
     * @throws IllegalArgumentException
     *             if raw is null.
     * @throws IllegalStateException
     *             if calling thread does not own the readLock.
     */
    void lineReceived(byte[] raw) {
        if (raw == null) {
            throw new IllegalArgumentException("raw is null");
        }

        if (readLock == 0 || readLock != Thread.currentThread().hashCode()) {
            throw new IllegalStateException("readLock not properly set");
        }

        this.lastActivity = System.currentTimeMillis();

        String line = new String(raw, this.charset);

        if (command == null) { //waiting for command
        	if(line.charAt(0) == '5' || ! ascii.canEncode(line)) //mistaking 5xx responses, not ascii
        		return;
        	if(line.equals(nntpchankeepalive)){ //nntpchan required
        		/*try {
        			this.println(nntpchankeepalive2);
        		} catch (IOException e) {
        			Log.get().log(Level.WARNING, e.getLocalizedMessage(), e);
        		}*/
        		return;
        	}
        	
        	Log.get().log(Level.FINER, "<< {0}", line);
        	
            command = parseCommandLine(line);
            assert command != null;
        }else if (ascii.canEncode(line))
        	Log.get().log(Level.FINEST, "<< {0}", line);

        try {
            // The command object will process the line we just received
            try {//Why we send this.conn to very processLine? Because most of commands isStateful.
            	//It is not important for execution speed. 
                command.processLine(this, line, raw);
            } catch (StorageBackendException ex) {
                Log.get()
                        .info("Retry command processing after StorageBackendException");

                // Try it a second time, so that the backend has time to recover
                command.processLine(this, line, raw);
            }
        } catch (IOException | StorageBackendException ex1) {
        	// This will catch a second StorageBackendException
        	command = null;
        	Log.get().log(Level.WARNING, ex1.getLocalizedMessage(), ex1);
        	try {
				println("403 Internal server error");
				close();
			} catch (IOException e) {} //already disconnected no need to do anything.

        	// Should we end the connection here?
        	// RFC says we MUST return 400 before closing the connection
        	
        }

        if (command == null || command.hasFinished()) {
            command = null;
            charset = StandardCharsets.UTF_8; // Reset to default
        }
    }

    /**
     * This method determines the fitting command processing class.
     *
     * @param line
     * @return
     */
    private Command parseCommandLine(String line) {
        String cmdStr = line.trim().split("\\s+")[0];
        return CommandSelector.getInstance().get(cmdStr);
    }

    /**
     * Puts the given line into the output buffer, adds a newline character and
     * returns. The method returns immediately and does not block until the line
     * was sent. Terminated sequence \r\n (NNTPConnection.NEWLINE) added at the end.
     * If several lines passed, then \r\n must be added manually after each line except the last one.
     *
     * @param line
     * @param charset
     * @throws java.io.IOException
     */
    public void println(final String line) throws IOException{
    	// Write characters to output buffers in lineBuffers
    	lineEncoder.encode(CharBuffer.wrap(line+NEWLINE));

    	enableWriteEvents(line);
    }

    //We use UTF-8 for cached articles! with \r\n at the end
    /*public void print(FileInputStream fs, String mId) throws IOException{
    	// Write characters to output buffers in lineBuffers
    	lineEncoder.encode(fs);

    	enableWriteEvents("\"Article "+mId+" here\"");
    }*/
    
  //We use UTF-8 for cached articles! with \r\n at the end
    public void print(FileInputStream fs, String mId) throws IOException{
    	byte[] tmp = new byte[ChannelLineBuffers.BUFFER_SIZE];
    	int len = 0;
    	int count;
    	int buffered = 0;
    	try{
    		while ((count = fs.read(tmp)) != -1) {
    			ByteBuffer buf = ChannelLineBuffers.newLineBuffer();
    			assert buf.position() == 0;
    			assert buf.capacity() >= ChannelLineBuffers.BUFFER_SIZE;
    			buf.put(tmp,0,count).flip();

    			lineBuffers.addOutputBuffer(buf);

    			len += count;
    			buffered += count;
    			if (buffered > 100*1024){
    				enableWriteEvents("Article "+mId+" processed: "+len); //we output every 100KB.
    				buffered = 0;
    			}
    		}
    		if (buffered != 0)
    			enableWriteEvents("Article "+mId+" processed: "+len);
    	} catch (ClosedChannelException e) {
    		Log.get().log(Level.WARNING, "NNTPConnection.printArticle(): {0}", e);
    		return;
    	}
    	
    }
    
    
    public interface BiConsumerMy{
    	public void accept(String str, String mId) throws IOException;
    }
    
    private class BiConsumit implements BiConsumerMy{
    	public void accept(String str, String mId) throws ClosedChannelException{
    		lineEncoder.encode(CharBuffer.wrap(str));
    		enableWriteEvents(mId+" attachment encoded and processed");
    	}
    }
    
    /**
     * local attachment encode to Base64 (in memory).
     * 
     * \r\n after file added in Article.build
     * 
     * Consumer cStrMid must encode from default charset to utf-8 and send
     * 
     * @param attachment
     * @param mId
     * @param cStrMid
     * @throws IOException
     */
    public static void print(File attachment, String mId, BiConsumerMy cStrMid) throws IOException{
    	FileInputStream i = new FileInputStream(attachment);
    	BufferedInputStream isb = null;
    	PipedInputStream buf = null;
    	PipedOutputStream pos = null;
    	OutputStream bos = null;
    	
    	try{
    		//input
			isb = new BufferedInputStream(i, 1024*512); //encoded to decoded
			//output buffer without file
			buf = new PipedInputStream(1024); //800b is enough
			pos = new PipedOutputStream(buf);  
			bos = Base64.getEncoder().wrap(pos);
			
			byte[] src = new byte[600];//read. encoded to 800 exactly
			byte[] pinp = new byte[980];//write
			
			int read = 0;
			while ((read = isb.read(src)) != -1) {
				bos.write(src, 0, read);
				if ((read = buf.read(pinp)) != -1){
					byte[] rd = new byte[read]; //tmp buf
					System.arraycopy(pinp, 0, rd, 0, read); 
					//we dont knew which encoding is rd, we create string and pass to encoder to UTF-8
					cStrMid.accept(new String(rd), mId); //write
				}	
			}
			
			bos.close();
			if (buf.available() > 0 && (read = buf.read(pinp)) != -1){
				byte[] rd = new byte[read]; //tmp buf
				System.arraycopy(pinp, 0, rd, 0, read);
				cStrMid.accept(new String(rd), mId); //write
			}
			
    	}finally{
			if (i != null)
				i.close();
			if (isb != null)
				isb.close();
			if (buf != null)
				buf.close();
			if (pos != null)
				pos.close();
		}
    }
  
    //must be with \r\n at the end.
    public void print(NNTPArticle nart, String mId) throws IOException{
    	if (nart.attachment != null){
    		lineEncoder.encode(CharBuffer.wrap(nart.before_attach));
        	enableWriteEvents(mId+" before attachment sent");
        	
        	//in default need to encode to Base64 and utf-8
        	print(nart.attachment, mId, new BiConsumit());
        	
        	lineEncoder.encode(CharBuffer.wrap(nart.after_attach));
        	enableWriteEvents(mId+" after attachment sent");
    			
    	}else{
    		lineEncoder.encode(CharBuffer.wrap(nart.before_attach));
    		enableWriteEvents(mId+" article sent");
    	}
    		
    }

    private void enableWriteEvents(CharSequence debugLine) {
        // Enable OP_WRITE events so that the buffers are processed
        try {
            this.writeSelKey.interestOps(SelectionKey.OP_WRITE);
            ChannelWriter.getInstance().getSelector().wakeup();
        } catch (Exception ex){ // CancelledKeyException and
                               // ChannelCloseException
            Log.get().log(Level.WARNING, "NNTPConnection.writeToChannel(): {0}", ex);
            return;
        }

        // Update last activity timestamp
        this.lastActivity = System.currentTimeMillis();
        if (debugLine != null) {
            Log.get().log(Level.FINEST, ">> {0}", debugLine);
        }
    }

    public void setCurrentCharset(final Charset charset) {
        this.charset = charset;
    }

    void updateLastActivity() {
        this.lastActivity = System.currentTimeMillis();
    }
    
    public void close(){
        shutdownInput();
        shutdownOutput();
    }
    
	/**
	 * @return null if it is not TLS connection.
	 */
	public TLS getTLS() {
		return this.tls;
	}
	
	public boolean isTLSenabled() {
		return this.tlsenabled;
	}

	public void setTLS(TLS tls) {
		this.tls = tls;
		if (tls != null)
			this.tlsenabled = true;
		else
			this.tlsenabled = false;
	}
	
	public String getHost(){
		String host;
		if (this.tlsenabled)
			host = this.tls.getPeerNames()[0];
		else
			host = channel.socket().getRemoteSocketAddress().toString();
		
		return host;
	}
}