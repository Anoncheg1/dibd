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

import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.List;

/**
 * Class holding ByteBuffers for SocketChannels/NNTPConnection. Due to the
 * complex nature of AIO/NIO we must properly handle the line buffers for the
 * input and output of the SocketChannels.
 * 
 * Static methods:
 *   allocateDirect
 *   newLineBuffer input-local out-remote
 *   recycleBuffer input-remote out-local 
 * 
 * Non-static input methods
 *   getInputBuffer
 *   nextInputLine
 *   recycleBuffer
 * 
 * Non-static output methods
 * 	 addOutputBuffer
 *   getOutputBuffer (very tricky)
 *   isOutputBufferEmpty
 *   
 *   recycleBuffers remote out and in
 *
 * @author Christian Lins
 * @since sonews/0.5.0
 */
public class ChannelLineBuffers {

    /**
     * Size of one small buffer; per default this is 512 bytes to fit one
     * standard line.
     */
    public static final int BUFFER_SIZE = 990; //size of one line in line buffers
    public static final int INPUT_BUFFER_SIZE = 1024*100; //large one input buffer 100KB per connection (must be not less BUFFER_SIZE)
    private static final int maxCachedBuffers = 1024*7; //Cached buffers maximum 1024*1024*2 B = 7 MB
    private static final List<ByteBuffer> freeSmallBuffers = new ArrayList<>(
            maxCachedBuffers);

    /**
     * Allocates a predefined number of direct ByteBuffers (allocated via
     * ByteBuffer.allocateDirect()). This method is Thread-safe, but should only
     * called at startup.
     */
    public static void allocateDirect() {
        synchronized (freeSmallBuffers) {
            for (int n = 0; n < maxCachedBuffers; n++) {
                ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
                freeSmallBuffers.add(buffer);
            }
        }
    }

    /**
     * Returns a at least 512 bytes long ByteBuffer ready for usage. The method
     * first try to reuse an already allocated (cached) buffer but if that fails
     * returns a newly allocated direct buffer. Use recycleBuffer() method when
     * you do not longer use the allocated buffer.
     * MUST BE ADDED TO OUTPUT BUFFERS OR RECYCLED
     */
    public static ByteBuffer newLineBuffer() { //ref nextInputLine() and daemons.LineEncoder.encode()
        ByteBuffer buf = null;
        synchronized (freeSmallBuffers) {
            if (!freeSmallBuffers.isEmpty()) {
                buf = freeSmallBuffers.remove(0);
            }
        }

        if (buf == null) {
            // Allocate a non-direct buffer
            buf = ByteBuffer.allocate(BUFFER_SIZE);
        }

        assert buf.position() == 0;
        assert buf.limit() >= BUFFER_SIZE;

        return buf;
    }
    
    
    		////////////	INSTANCE	////////////
    
    
    
    // Both input and output buffers should be final as we synchronize on them,
    // but the buffers are set somewhere to another object or null. We should
    // investigate if this is an issue
    //
    // inputBuffer - ChannelReader(1 thread) write. ConnectionWorker( many threads) read 
    
    private final ByteBuffer inputBuffer = ByteBuffer.allocateDirect(INPUT_BUFFER_SIZE); //separated from freeSmallBuffers. For TLS we need larger buffer maybe. 
    private final List<ByteBuffer> outputBuffers = new ArrayList<>();
    private boolean outputBuffersClosed = false;
    private boolean inputBuffersClosed = false;


    
    /**
     * Add the given ByteBuffer to the list of buffers to be send to the client.
     * This method is Thread-safe.
     * MUST BE USED WITH newLineBuffer ONLY 
     *
     * @param buffer
     * @throws java.nio.channels.ClosedChannelException
     *             If the client channel was already closed.
     */
    public void addOutputBuffer(ByteBuffer buffer)
            throws ClosedChannelException {
        synchronized(outputBuffers) {
            if (outputBuffersClosed) {
                throw new ClosedChannelException();
            }
            outputBuffers.add(buffer);
        }
    }

    /**
     * Currently a channel has only one input buffer. This *may* be a bottleneck
     * and should investigated in the future.
     * Always ready for input to this buffer.[inputBuffer.put(src),  sec.get(inputBuffer)]
     *
     * @return The input buffer associated with given channel.
     */
    ByteBuffer getInputBuffer() { //ChannelReader 1 thread
        return inputBuffer;
    }

    /**
     * Returns the current output buffer for writing(!) to SocketChannel.
     *  Spent (exhaust) buffer is recycled and loop for not spent buffer
     *
     * @return The next input buffer that contains unprocessed data or null if
     *         the connection was closed or there are no more unprocessed
     *         buffers.
     */
    public ByteBuffer getOutputBuffer() {
        synchronized (outputBuffers) {
            if (outputBuffers.isEmpty()) {
                return null;
            } else {
                ByteBuffer buffer = outputBuffers.get(0);//get first but not delete until it flush.
                if (buffer.remaining() == 0) { //it signals that all data was writen by ChannelWriter and nothing to return
                    outputBuffers.remove(0);
                    // Add old buffers to the list of free buffers
                    recycleBuffer(buffer);
                    buffer = getOutputBuffer(); //loop
                }
                return buffer;
            }
        }
    }

    /**
     * @return false if there are output buffers pending to be written to the
     *         client.
     */
    boolean isOutputBufferEmpty() {
        synchronized (outputBuffers) {
            return outputBuffers.isEmpty();
        }
    }

    /**
     * Goes through the input buffer of the given channel and searches for next
     * line terminator. If a '\n' is found, the bytes up to the line terminator
     * are returned as array of bytes (the line terminator is omitted). If none
     * is found the method returns null otherwise returned line written to 1
     * removed buffer from freeSmallBuffers.
     * Return List<ByteBuffer> never null 
     * RETURNED BUFFERS MUST BE RECYCLED WITH recycleBuffer METHOD  
     *
     * @param channel
     * @return A List of ByteBuffers each wrapping the line.
     */
    List<ByteBuffer> getInputLines() {

    	List<ByteBuffer> lines = new ArrayList<>(); //return value

    	synchronized (inputBuffer) {
    		if (inputBuffersClosed) {
    			return new ArrayList<>(0);
    		}

    		ByteBuffer lineBuffer = newLineBuffer();
    		assert(lineBuffer.position() == 0);
    		assert(lineBuffer.limit() == BUFFER_SIZE);

    		// Set position to 0 and limit to current position
    		inputBuffer.flip();

    		int lim = inputBuffer.limit();
    		while (inputBuffer.position() < lim) {

    			int plim = inputBuffer.position() + BUFFER_SIZE;
    			plim = plim < lim ? plim : lim;// lesser of two 

    			while (inputBuffer.position() < plim) {
    				byte b = inputBuffer.get();
    				if (b == 10) //='\n' (UTF-8) need CRLF \r\n  -'\r' left at the end. NNTPConnection.lineReceived(line) deal with it.
    				{
    					lineBuffer.flip(); // limit to position, position to 0
    					lines.add(lineBuffer);
    					lineBuffer = newLineBuffer();
    					break;
    				} else {
    					lineBuffer.put(b);
    				}
    			}
    			if( !lineBuffer.hasRemaining()){
    				lineBuffer.flip(); // limit to position, position to 0
    				lines.add(lineBuffer);
    				lineBuffer = newLineBuffer();
    			}	
    		}//pos == lim

    		//1) full no end
    		//2) short line without end (partial)
    		//3) full with end


    		inputBuffer.position(inputBuffer.position()-lineBuffer.position());
    		inputBuffer.compact();//copy bytes between position and limit to the begining. lim =cap
    		recycleBuffer(lineBuffer);

    	}

    	return lines;

    }
    
    //another full working version
    /*List<ByteBuffer> getInputLines() {
    	synchronized (inputBuffer) {
    		if (inputBuffersClosed) {
    			return new ArrayList<>(0);
    		}

    		// Mark the current write position
    		int mark = inputBuffer.position();

    		// Set position to 0 and limit to current position
    		inputBuffer.flip();
    		
    		List<ByteBuffer> lines = new ArrayList<>(); //return value
    		ByteBuffer lineBuffer = newLineBuffer();

    		
    		int lim = inputBuffer.limit();
    		while (inputBuffer.position() < lim) {
    			if(!lineBuffer.hasRemaining()){
    				lineBuffer.flip(); // limit to position, position to 0
    				lines.add(lineBuffer);
    				lineBuffer = newLineBuffer();
    			}
    				
    			byte b = inputBuffer.get();
    			if (b == 10) //='\n' (UTF-8) need CRLF \r\n  -'\r' left at the end. NNTPConnection.lineReceived(line) deal with it.
    			{
    				lineBuffer.flip(); // limit to position, position to 0
    				lines.add(lineBuffer);
    				lineBuffer = newLineBuffer();
    			} else {
    				lineBuffer.put(b);
    			}
    		}//inputBuffer position=limit
    		//1) full no end
    		//2) short line without end (partial)
    		//3) full with end
    		
    		if (!lines.isEmpty()){//if we has successfully read lines therefore we don't care about (lim == cap) 
    			if(lines.contains(lineBuffer)){//last buffer
    				
    				//we readed all data
    				inputBuffer.clear();
    			}else{// we got lines but last line was partial(without \r\n)
    				 //we left last partial-line in inputBuffer
    				inputBuffer.position(inputBuffer.position()-lineBuffer.position());
    				inputBuffer.compact();//copy bytes between position and limit to the begining. lim =cap
    				recycleBuffer(lineBuffer);
    				
    			}
    		}else{//we didn't found any line so we must read more.
    			if(inputBuffer.limit() == inputBuffer.capacity()){ 
    				// overflow without \r\n
    				// no newline found, so the input is not standard compliant.
    				throw new Error("ChannelLineBuffers.getInputLines() INPUT BUFFER IS OVERFLOW WITHOUT NEW LINE");
    			}
    			
    			recycleBuffer(lineBuffer);
    			inputBuffer.limit(inputBuffer.capacity());//restore
        		inputBuffer.position(mark);//restore
    		}
    		
    		assert(inputBuffer.position() < inputBuffer.limit());
    		return lines;    		
    	}
    }
    */
        

    /**
     * Adds the given buffer to the list of free buffers if it is a valuable
     * direct allocated buffer.
     *
     * @param buffer
     */
    public static void recycleBuffer(ByteBuffer buffer) {
        assert buffer != null;

        //if (buffer.isDirect()) {// why we need it?
            assert buffer.capacity() == BUFFER_SIZE;

            // Add old buffers to the list of free buffers
            synchronized (freeSmallBuffers) {
                buffer.clear(); // Set position to 0 and limit to capacity
                freeSmallBuffers.add(buffer);
            }
        //} // if(buffer.isDirect())
    }

    /**
     * Recycles all buffers of this ChannelLineBuffers object.
     */
    public void recycleBuffers() {
        synchronized (inputBuffer) {
            //recycleBuffer(inputBuffer);
            //this.inputBuffer = null;
            inputBuffersClosed = true;
        }

        synchronized (outputBuffers) {
            for (ByteBuffer buf : outputBuffers) {
                recycleBuffer(buf);
            }
            outputBuffers.clear();
            outputBuffersClosed = true;
        }
    }


    /**
     * TLS
     */
    /*private ByteBuffer inNetBB = null; //	-> inAppBB
    private ByteBuffer inAppBB = null; //	-> inputBuffer
	
	public void setInNetBB(ByteBuffer inNetBB) {
		this.inNetBB = inNetBB;
	}
	
	public ByteBuffer getInNetBB() {
		return inNetBB;
	}
	
	public void setInAppBB(ByteBuffer inAppBB) {
		this.inAppBB = inAppBB;
	}
	
	public ByteBuffer getInAppBB() {
		return inAppBB;
	}*/
    
	/**
	 * InAppBB to inputBuffer
	 */
    /*
	public void clearInAppBB() {
		synchronized (inputBuffer) {
			if (!inputBuffersClosed) {
				try{
					inputBuffer.put(inAppBB);
					inAppBB.clear(); // if no exception bytes transferred
				}catch(BufferOverflowException ex){
					//System.out.println("line buffers clearInAppBB BufferOverflowException "+ new String(inAppBB.array(), Charset.forName("UTF-8")));
					System.out.println("line buffers clearInAppBB BufferOverflowException ");
					//System.out.println("inputbuffer "+ new String(inputBuffer.array(), Charset.forName("UTF-8")));
					inAppBB.compact();
				}
    		}
		}
	}*/
}
