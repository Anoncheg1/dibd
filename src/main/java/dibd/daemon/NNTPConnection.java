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

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;

import dibd.daemon.command.Command;
import dibd.storage.StorageBackendException;
import dibd.storage.GroupsProvider.Group;
import dibd.storage.article.Article;
import dibd.util.Log;

/**
 * For every SocketChannel (so TCP/IP connection) there is an instance of this
 * class.
 *
 * @author Christian Lins
 * @since sonews/0.5.0
 */
public class NNTPConnection implements NNTPInterface{

    public static final String NEWLINE = "\r\n"; // RFC defines this as newline
    //public static final String MESSAGE_ID_PATTERN = "<[^(>|@)]+@[^(>|@)]+>";//"<[^>]+>";
    public static final String MESSAGE_ID_PATTERN = "<[\\w.)]+@[\\w.-]+>";
    private static final Timer cancelTimer = new Timer(true); // Thread-safe?
                                                              // True for run as
                                                              // daemon
    /** SocketChannel is generally thread-safe */
    private SocketChannel channel;
    private Charset charset = Charset.forName("UTF-8");
    private Command command = null;
    
    private Article currentArticle = null;
    private Group currentGroup = null;
    private volatile long lastActivity = System.currentTimeMillis();
    private final ChannelLineBuffers lineBuffers = new ChannelLineBuffers();
    private int readLock = 0;
    private final Object readLockGate = new Object();
    private SelectionKey writeSelKey = null;
    private final LineEncoder lineEncoder = new LineEncoder(charset, lineBuffers);
    
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
        	this.channel.keyFor(readSelector).cancel();
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

    public Article getCurrentArticle() {
        return this.currentArticle;
    }

    public Charset getCurrentCharset() {
        return this.charset;
    }
    
    public Group getCurrentGroup() {
        return this.currentGroup;
    }

    public void setCurrentArticle(final Article article) {
        this.currentArticle = article;
    }

    public void setCurrentGroup(final Group group) {
        this.currentGroup = group;
    }

    public long getLastActivity() {
        return this.lastActivity;
    }

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

        // There might be a trailing \r, but trim() is a bad idea
        // as it removes also leading spaces from long header lines.
        if (line.endsWith("\r")) { //collaboration with ChannelLineBuffer.nextInputLine
            line = line.substring(0, line.length() - 1);
            raw = Arrays.copyOf(raw, raw.length - 1);
        }

        Log.get().log(Level.FINE, "<< {0}", line);

        if (command == null) {
            command = parseCommandLine(line);
            assert command != null;
        }

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
				close(); //no need to close if connection broken
			} catch (IOException e) {} //already disconnected no need to do anything.

        	// Should we end the connection here?
        	// RFC says we MUST return 400 before closing the connection
        	
        }

        if (command == null || command.hasFinished()) {
            command = null;
            charset = Charset.forName("UTF-8"); // Reset to default
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
    	while ((count = fs.read(tmp)) != -1) {
    		ByteBuffer buf = ChannelLineBuffers.newLineBuffer();
    		assert buf.position() == 0;
            assert buf.capacity() >= ChannelLineBuffers.BUFFER_SIZE;
            buf.put(tmp,0,count).flip();
            
            try {
				lineBuffers.addOutputBuffer(buf);
			} catch (ClosedChannelException e) {
				Log.get().log(Level.WARNING, "NNTPConnection.printArticle(): {0}", e);
			}
            //System.out.println(new String(tmp, charset));
            enableWriteEvents("Article "+mId+" processed: "+len);
            len += count;
    	}
    	fs.close();
    	
    	
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
            Log.get().log(Level.FINE, ">> {0}", debugLine);
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
		
		System.out.println("NNTPConnection.getHost(): tls enabled? "+tlsenabled+" "+host);
		return host;
	}
}