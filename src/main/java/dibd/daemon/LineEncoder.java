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
import java.nio.CharBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.logging.Level;

import dibd.util.Log;

/**
 * Encodes a line to output buffers using the correct charset.
 * 
 * 
 * @author Christian Lins
 * @since sonews/0.5.0
 */
public class LineEncoder {
	
	

    private final Charset charset;
    private final CharsetEncoder encoder;
    private final ChannelLineBuffers buffers;

    /**
     * Constructs new LineEncoder.
     * 
     * @param characters
     * @param charset
     */
    public LineEncoder(Charset charset, ChannelLineBuffers buffers) {
        this.charset = charset;
        this.encoder = charset.newEncoder();
        this.buffers = buffers;
        
        if (!this.charset.canEncode()) {
            Log.get().log(Level.SEVERE, "FATAL: Charset {0} cannot encode!",
                    this.charset);
            return;
        }
    }
    
    
    /**
     * Encodes the characters of this instance to the given ChannelLineBuffers
     * using the Charset of this instance.
     * 
     * @param buffer
	 * @param characters
	 * @param charset
	 * @throws ClosedChannelException
     */
	public void encode(CharBuffer characters) throws ClosedChannelException {
        
        while (characters.hasRemaining()) {
            ByteBuffer buf = ChannelLineBuffers.newLineBuffer();
            assert buf.position() == 0;
            assert buf.capacity() >= ChannelLineBuffers.BUFFER_SIZE;

//            CoderResult res = 
            this.encoder.encode(characters, buf, true);

            // Set limit to current position and current position to 0;
            // means make ready for read from buffer
            buf.flip();
            this.buffers.addOutputBuffer(buf);

            //Do we need it?
            //if (res.isUnderflow()) // All input processed
              //  break;
        }
    }
	
	/*
	public void encode(InputStream fs) throws IOException {
		BufferedReader in = new BufferedReader(new InputStreamReader(fs, Charset.forName("UTF-8")), ChannelLineBuffers.BUFFER_SIZE);
    	String line;
    	while ((line = in.readLine()) != null) {//we remove \r\n here
    		//TODO: becouse of nntpchan, line may be any size. It will low performance
    		encode(CharBuffer.wrap(line+NNTPConnection.NEWLINE));
        }
    }*/

    /**
     * Encodes the characters of this instance to the given ChannelLineBuffers
     * using the Charset of this instance.
     * 
     * @param buffer
     * @throws java.nio.channels.ClosedChannelException
     */
    /*public void encode(ChannelLineBuffers buffer) throws ClosedChannelException {
        CharsetEncoder encoder = charset.newEncoder();
        while (characters.hasRemaining()) {
            ByteBuffer buf = ChannelLineBuffers.newLineBuffer();
            assert buf.position() == 0;
            assert buf.capacity() >= 512;

//            CoderResult res = 
            encoder.encode(characters, buf, true);

            // Set limit to current position and current position to 0;
            // means make ready for read from buffer
            buf.flip();
            buffer.addOutputBuffer(buf);

            //Do we need it?
            //if (res.isUnderflow()) // All input processed
              //  break;
        }
    }*/
}
