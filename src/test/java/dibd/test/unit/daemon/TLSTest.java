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
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import javax.net.ssl.SSLContext;

import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.mockito.Mockito;
import dibd.daemon.ChannelLineBuffers;
import dibd.daemon.LineEncoder;
import dibd.daemon.NNTPConnection;
import dibd.daemon.TLS;

/**
 * @author user
 *
 */

public class TLSTest {

	static ChannelLineBuffers clBuffers;
	static List<ByteBuffer> freeSmallBuffers; //static field of ChannelLineBuffers
	static Method getInputLines;
	static Method getInputBuffer;
	static Object nothing[] = new Object[]{};
	
	
	//brilliant!
	 /*static void setFinalStatic(Field field, Object newValue) throws Exception {
	        field.setAccessible(true);        
	        Field modifiersField = Field.class.getDeclaredField("modifiers");
	        modifiersField.setAccessible(true);
	        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
	        field.set(null, newValue);
	    }*/
	
	@SuppressWarnings("unchecked")
	@BeforeClass
	public static void init() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, NoSuchAlgorithmException{
		Field sslContextf = TLS.class.getDeclaredField("sslContext");
		sslContextf.setAccessible(true);
		
		//conn
		NNTPConnection conn = Mockito.mock(NNTPConnection.class);
		
		//SSLContext sslContext = Mockito.mock(SSLContext.class);
		sslContextf.set(TLS.class, SSLContext.getDefault());
		
		//buffers
		ChannelLineBuffers.allocateDirect();
		clBuffers = new ChannelLineBuffers();
		Mockito.when(conn.getBuffers()).thenReturn(clBuffers);
		
		SocketChannel sch = null;
		
		//socketchannel
		Mockito.when(conn.getSocketChannel()).thenReturn(sch);
		
		TLS tls = new TLS(conn);
		
		
		//System.out.println(s);
		/*
		
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
		*/
	}
	
	@Test
	@Ignore
	public void Test(){
		
	}
}