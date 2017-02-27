package dibd.test.unit.feed;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.Proxy;
import java.net.Socket;
import java.nio.charset.Charset;

import org.junit.Test;

import dibd.feed.FeedManager;

import static org.mockito.Mockito.*;
//import static org.mockito;

public class FeedManagerTest{

	private final Socket rSocket;

	public FeedManagerTest() {
		rSocket = mock(Socket.class);
	}
	
	@Test
	public void getHelloFromServerTest() throws IOException{
		//preparing remote connection Socket First parameter for ArticlePuller
		//and pipeline fot testing
		
		PipedInputStream inForOut = new PipedInputStream();
		PipedOutputStream outForIn = new PipedOutputStream();
		BufferedReader rIn = new BufferedReader(new InputStreamReader(inForOut, "UTF-8"));
		PrintWriter rOut = new PrintWriter(new OutputStreamWriter(outForIn, "UTF-8"));

		when(rSocket.getOutputStream()).thenReturn(new PipedOutputStream(inForOut));
		when(rSocket.getInputStream()).thenReturn(new PipedInputStream(outForIn));
		rOut.println("200 hello");
		rOut.flush();
		Socket socket = FeedManager.getHelloFromServer(rSocket, false, "testhost", Charset.forName("UTF-8"));
		
	}

}
