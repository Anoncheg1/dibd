/**
 * 
 */
package dibd.test.unit.daemon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.Charset;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import dibd.daemon.ChannelLineBuffers;
import dibd.daemon.LineEncoder;

/**
 * @author user
 *
 */

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ChannelLineBuffersTest {

	static ChannelLineBuffers clBuffers;
	static List<ByteBuffer> freeSmallBuffers; //static field of ChannelLineBuffers
	static Method getInputLines;
	static Method getInputBuffer;
	static Object nothing[] = new Object[]{};
	
	@SuppressWarnings("unchecked")
	@BeforeClass
	public static void init() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, NoSuchMethodException{
		//App
		ChannelLineBuffers.allocateDirect();
		//daemons.NNTPConnection
		clBuffers = new ChannelLineBuffers();//InputBuffer = newLineBuffer()

		Field field = ChannelLineBuffers.class.getDeclaredField("freeSmallBuffers");
		field.setAccessible(true);
		freeSmallBuffers = (List<ByteBuffer>) field.get(ChannelLineBuffers.class);
		
		getInputLines = clBuffers.getClass().getDeclaredMethod("getInputLines", new Class<?>[]{});
		getInputLines.setAccessible(true);
		getInputBuffer = clBuffers.getClass().getDeclaredMethod("getInputBuffer", new Class<?>[]{});
		getInputBuffer.setAccessible(true);
		
	}
	
	final int size = freeSmallBuffers.size(); //initial value for testing
	
	
	
	
	@Test
	public void aReadTest() throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, UnsupportedEncodingException, NoSuchMethodException, SecurityException{
		String ts [] = {"Hello from remote server","Article please Article pleaseArticle pleaseArticle please"+
	"Article pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle please"
				+"Article pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle please"
				+"Article pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle please"
				+"Article pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle please"
				+"Article pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle please"
				+"Article pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle please"
				+"Article pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle please"
				+"Article pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle please"
				+"Article pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle please"
				+"Article pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle pleaseArticle please", "unfinished line"}; int i = 0;
		
		
		//daemon.ChannelReader
		byte[] src = (ts[0]+"\r\n"+ts[1]+"\r\n"+ts[2]).getBytes(Charset.forName("UTF-8"));
		ByteBuffer InputBuffer = ((ByteBuffer)getInputBuffer.invoke(clBuffers, nothing)).put(src);
		//daemon.ConnectionWorker
		@SuppressWarnings("unchecked")
		List<ByteBuffer> buffers = (List<ByteBuffer>) getInputLines.invoke(clBuffers, nothing); //"buf" is a part of ChannelLineBuffers
		boolean part1processed = true;
		for(ByteBuffer buf : buffers) // Complete line was received
        {
			
            byte[] line = new byte[buf.limit()];
            buf.get(line);
            ChannelLineBuffers.recycleBuffer(buf); //recycle is done outside

            //daemon.NNTPConnection.lineReceived
               //System.out.println(ts[i].replaceAll("\r", "\\\\r"));
            
            if ((ts[i].length() > 990) && part1processed){ //no \r\n at 990 line test
            	assertEquals(ts[i].substring(0, ChannelLineBuffers.BUFFER_SIZE),new String(line, "UTF-8")); //"\r" removed in lineReceived, but here we leave it as is
            	part1processed = false;
            }else{
            	if (ts[i].length() > 990){
            		assertEquals(ts[i].substring(ChannelLineBuffers.BUFFER_SIZE, ts[i++].length())+"\r",new String(line, "UTF-8")); //"\r" removed in lineReceived, but here we leave it as is
            	}else{
            		assertEquals(ts[i++]+"\r",new String(line, "UTF-8")); //"\r" removed in lineReceived, but here we leave it as is
            	}
            }
        
        }
        
        //System.out.println(this.size+" "+ChannelLineBuffersTest.freeSmallBuffers.size());
        assertTrue(this.size == ChannelLineBuffersTest.freeSmallBuffers.size()); //before == after
        
        
        //System.out.println(ChannelLineBuffers.INPUT_BUFFER_SIZE-src.length);
        assertTrue(InputBuffer.limit() == InputBuffer.capacity());
        
        assertTrue(InputBuffer.position() == ts[2].length());
        
        InputBuffer.flip();
        byte[] unfinLine = new byte[InputBuffer.limit()];
        InputBuffer.get(unfinLine);
        assertTrue(new String(unfinLine, "UTF-8").equals(ts[2]));
        
        InputBuffer.clear();
	}
	
	
	@Test
	public void abReadRemainTest() throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, UnsupportedEncodingException, NoSuchMethodException, SecurityException{
		
		byte[] src = ("hello\r\n").getBytes(Charset.forName("UTF-8"));
		ByteBuffer InputBuffer = ((ByteBuffer)getInputBuffer.invoke(clBuffers, nothing)).put(src);
		//daemon.ConnectionWorker
		@SuppressWarnings("unchecked")
		List<ByteBuffer> buffers = (List<ByteBuffer>) getInputLines.invoke(clBuffers, nothing); //"buf" is a part of ChannelLineBuffers
		boolean part1processed = true;
		
		ByteBuffer buf = buffers.get(0);
		byte[] line = new byte[buf.limit()];
		buf.get(line);
		ChannelLineBuffers.recycleBuffer(buf); //recycle is done outside

		assertEquals("hello\r",new String(line, "UTF-8")); //"\r" removed in lineReceived, but here we leave it as is

        
        //System.out.println(this.size+" "+ChannelLineBuffersTest.freeSmallBuffers.size());
        assertTrue(this.size == ChannelLineBuffersTest.freeSmallBuffers.size()); //before == after
        
        
        //System.out.println(ChannelLineBuffers.INPUT_BUFFER_SIZE-src.length);
        assertTrue(InputBuffer.limit() == InputBuffer.capacity());
        assertTrue(InputBuffer.position() == 0);
        
        
        InputBuffer.clear();
        
        
	}
	
	
	
	
	
	
	@Test
	public void bWriteTest() throws ClassNotFoundException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, UnsupportedEncodingException, ClosedChannelException{
		//Class<?> clazz = Class.forName("dibd.daemon.LineEncoder");
    	//Constructor<?> con = clazz.getConstructors()[0];
		//Constructor<?> con = clazz.getConstructor(new Class<?>[]{CharBuffer.class, Charset.class});
    	/*for( Class<?> p : con.getParameterTypes() )
			System.out.println("c: "+p);*/
    	//con.setAccessible(true);
    	//Method encode = clazz.getDeclaredMethods()[0];
    	//Method encode = clazz.getDeclaredMethod("encode", new Class<?>[]{ChannelLineBuffers.class});
    	/*for( Class<?> p : encode.getParameterTypes() )
			System.out.println(encode.getName()+": "+p);*/
    	//encode.setAccessible(true);
    	

    	
		
		
		//daemon.ConnectionWorker thread
		//daemon.command.****Command
		//  daemon.NNTPConnection.println
		//    daemon.NNTPConnection.writeToChannel
    	Charset charset = Charset.forName("UTF-8");
		String ts [] = {"blah", "\r\n", "205 cya", "\r\n"}; int i=0;
		LineEncoder lineEncoder = new LineEncoder(charset, clBuffers);
		lineEncoder.encode(CharBuffer.wrap(ts[0]));
    	//Object iLineEncoder = con.newInstance(new Object[]{CharBuffer.wrap(ts[0]), Charset.forName("UTF-8")});
    	//encode.invoke(iLineEncoder, clBuffers);
		lineEncoder.encode(CharBuffer.wrap("\r\n"));
    	//iLineEncoder = con.newInstance(new Object[]{CharBuffer.wrap("\r\n"), Charset.forName("UTF-8")});
    	//encode.invoke(iLineEncoder, clBuffers);
    	//    writeToChannel again for \r\n
		lineEncoder.encode(CharBuffer.wrap(ts[2]));
    	//iLineEncoder = con.newInstance(new Object[]{CharBuffer.wrap(ts[2]), Charset.forName("UTF-8")});
    	//encode.invoke(iLineEncoder, clBuffers);
		lineEncoder.encode(CharBuffer.wrap("\r\n"));
    	//iLineEncoder = con.newInstance(new Object[]{CharBuffer.wrap("\r\n"), Charset.forName("UTF-8")});
    	//encode.invoke(iLineEncoder, clBuffers);
    	
    	//daemon.ChannelWriter thread
    	ByteBuffer buf = clBuffers.getOutputBuffer();//recycle is inside,  "buf" is not a part of ChannelLineBuffers here
    	while (buf != null){
    		byte[] dst = new byte[buf.limit()];
    		buf.get(dst);
    		
    		//System.out.println(new String(dst, "UTF-8").replaceAll("\r\n", "\\\\r\\\\n"));
    		assertEquals(ts[i++],new String(dst, "UTF-8"));
    		buf = clBuffers.getOutputBuffer();
    	}
    	
        
    	//System.out.println(this.freeSmallBuffers.size());
    	//System.out.println(this.size);
    	assertTrue(this.size == ChannelLineBuffersTest.freeSmallBuffers.size());    
	}
	
	
	//@Ignore
	@Test
	public void cRecycleTest() throws ClassNotFoundException, NoSuchMethodException, SecurityException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, ClosedChannelException{
		//fill input buffer
		Method getInputLines = clBuffers.getClass().getDeclaredMethod("getInputLines", new Class<?>[]{});
		getInputLines.setAccessible(true);
		
		byte[] src = ("Filling inputBuffer\r\nasd\r\n").getBytes(Charset.forName("UTF-8"));
		((ByteBuffer)getInputBuffer.invoke(clBuffers, nothing)).put(src);
		//ByteBuffer buf = (ByteBuffer) nextInputLine.invoke(clBuffers, new Object[]{}); //"buf" is a part of ChannelLineBuffers
		
		//fill output buffer
		/*Class<?> clazz = Class.forName("dibd.daemon.LineEncoder");
		Constructor<?> con = clazz.getConstructor(new Class<?>[]{CharBuffer.class, Charset.class});
    	con.setAccessible(true);
    	Method encode = clazz.getDeclaredMethod("encode", new Class<?>[]{ChannelLineBuffers.class});
    	encode.setAccessible(true);
		*/
    	String s  = "Filling src buffer\r\nFilling src buffer\r\n";
    	//Object iLineEncoder = con.newInstance(new Object[]{CharBuffer.wrap(s), Charset.forName("UTF-8")});*
    	//encode.invoke(iLineEncoder, clBuffers);
    	LineEncoder lineEncoder = new LineEncoder(Charset.forName("UTF-8"), clBuffers);
		lineEncoder.encode(CharBuffer.wrap(s));
    	//System.out.println("end out "+this.freeSmallBuffers.size());
    	//recycle
    	clBuffers.recycleBuffers();
    	
    	assertTrue(this.size == ChannelLineBuffersTest.freeSmallBuffers.size()); //input buffer is closed now
    	assertTrue(((List<ByteBuffer>) getInputLines.invoke(clBuffers, new Object[]{})).isEmpty());
    	assertTrue(clBuffers.getOutputBuffer() == null);
	}
}