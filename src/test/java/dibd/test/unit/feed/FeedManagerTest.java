package dibd.test.unit.feed;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.Proxy;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import dibd.feed.FeedManager;
import dibd.storage.GroupsProvider.Group;
import dibd.util.Log;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
//import static org.mockito;

public class FeedManagerTest{

	private final Socket rSocket;

	public FeedManagerTest() throws NoSuchMethodException, SecurityException {
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
		assertTrue (socket != null);
		
		
	}
	
	
	@Test
	public void sortThreadsReplaysTest() throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException{
		
		Map<String, String> replays = new LinkedHashMap<>(250);
		List<String> threads = new ArrayList<String>(250);
		
		replays.put("1@hh", "nothread@hh");
		replays.put("2@hh", "th@hh");
		
		threads.add("th@hh");
		Log.get().setLevel(Level.SEVERE);

		Group gr = Mockito.mock(Group.class);
		Map<String, List<String>> res = FeedManager.sortThreadsReplays(threads, replays, "host", gr);
		Log.get().setLevel(Level.WARNING);
		assertEquals(res.size(), 1);
		assertEquals(res.get("th@hh").size(), 1);
		assertEquals(res.get("th@hh").get(0), "2@hh");
		
	}

}
