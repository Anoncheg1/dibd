package dibd.test.unit.feed;

import static org.mockito.Mockito.when;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;
import dibd.config.Config;
import dibd.daemon.ChannelLineBuffers;
import dibd.daemon.NNTPInterface;
import dibd.daemon.command.IhaveCommand;
import dibd.feed.ArticlePuller;
import dibd.feed.ArticlePusher;
import dibd.storage.AttachmentProvider;
import dibd.storage.GroupsProvider;
import dibd.storage.NNTPCacheProvider;
import dibd.storage.StorageBackendException;
import dibd.storage.StorageManager;
import dibd.storage.StorageNNTP;
import dibd.storage.SubscriptionsProvider;
import dibd.storage.GroupsProvider.Group;
import dibd.storage.article.Article;
import dibd.util.Log;



//@Ignore //fail sometimes because of complexity
public class ArticlePusherTest {
	
	private final Socket rSocket = mock(Socket.class); //mock
	private BufferedReader rIn; //communication with ArticlePusher instance
	private PrintWriter rOut;//communication with ArticlePusher instance
	private StorageNNTP storage = mock(StorageNNTP.class); //mock
	private NNTPInterface conn = mock(NNTPInterface.class); //mock
	private AttachmentProvider aprov = mock(AttachmentProvider.class);//mock
	private Constructor<?> groupC;
	private Charset charset = Charset.forName("UTF-8");
	
	String[] send2 = {
			"MIME-Version: 1.0",
			"Date: Thu, 02 May 2013 12:16:44 +0000",
			"Message-ID: <foobar@hschan.ano>",
			"Newsgroups: local.test",
			"Subject: subj",
			"Path: host.com!hschan.ano",
			"Content-Transfer-Encoding: 8bit",
			"Content-Type: multipart/mixed; boundary=\"=-=-=_8QnOE1Q0yb8Ke10FkeMNnWYqloKscZWU_=-=-=\"",
			"",
			"--=-=-=_8QnOE1Q0yb8Ke10FkeMNnWYqloKscZWU_=-=-=",
			"Content-Type: text/plain; charset=utf-8",
			"Content-Transfer-Encoding: base64",
			"",
			"bWVzc2FnZQ==",
			"--=-=-=_8QnOE1Q0yb8Ke10FkeMNnWYqloKscZWU_=-=-=",
			"Content-Type: image/gif",
			"Content-Disposition: attachment; filename=\"Blank.gif\"",
			"Content-Transfer-Encoding: base64",
			"",
			"R0lGODlhAQABAIAAAP///wAAACH5BAAAAAAALAAAAAABAAEAAAICRAEAOw==",
			"--=-=-=_8QnOE1Q0yb8Ke10FkeMNnWYqloKscZWU_=-=-=--"
	};
	
	
	
	
	public ArticlePusherTest() throws NoSuchMethodException, SecurityException, StorageBackendException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, IOException {
        PipedInputStream inForOut = new PipedInputStream();
        PipedOutputStream outForIn = new PipedOutputStream();
        rIn = new BufferedReader(new InputStreamReader(inForOut, "UTF-8"));
        rOut = new PrintWriter(new OutputStreamWriter(outForIn, "UTF-8"));
        
        when(rSocket.getOutputStream()).thenReturn(new PipedOutputStream(inForOut));
        when(rSocket.getInputStream()).thenReturn(new PipedInputStream(outForIn));
        /*Mockito.doAnswer(new Answer<Object>() {
    		public Object answer(InvocationOnMock invocation) throws IOException {
    			inForOut.close();
    			outForIn.close();
    			//System.out.println(invocation.getArguments()[1]);
    			//assertEquals(send2l.poll(), invocation.getArguments()[1]);
    			return null;
    		}})
    	.when(rSocket).close();*///depricated
    	
        
		//Storage
		StorageManager.enableProvider(new dibd.test.unit.storage.TestingStorageProvider(storage));
		Article art = Mockito.mock(Article.class);
		when(storage.createThread((Article)Mockito.any(), (byte[])Mockito.any(), (String)Mockito.any())).thenReturn(art);
		when(storage.createReplay((Article)Mockito.any(), (byte[])Mockito.any(), (String)Mockito.any())).thenReturn(art);
		
		when(conn.getCurrentCharset()).thenReturn(Charset.forName("UTF-8"));
		
		//attachments and host name
		StorageManager.enableAttachmentProvider(aprov);
		Config.inst().set(Config.HOSTNAME, "host.com"); //added to path

		//mocking peers
		SubscriptionsProvider sp = Mockito.mock(SubscriptionsProvider.class);  
		StorageManager.enableSubscriptionsProvider(sp);
		when(sp.has("host.com")).thenReturn(true);

		//mocking Group
		GroupsProvider gp = Mockito.mock(GroupsProvider.class);  
		StorageManager.enableGroupsProvider(gp);
		
		StorageManager.enableNNTPCacheProvider(Mockito.mock(NNTPCacheProvider.class));
		
		//group mocking part 1
		Class<?> cg = Group.class;
		groupC = cg.getDeclaredConstructor(new Class[]{GroupsProvider.class, String.class, Integer.TYPE, Integer.TYPE, Set.class});
		groupC.setAccessible(true);
		
		Set<String> host = new HashSet<String>(Arrays.asList("hschan.ano","host.com"));
		//name id flags hosts
		when(StorageManager.groups.get("local.test")).thenReturn(
				(Group) groupC.newInstance(StorageManager.groups,"local.test",23,0,host));
		
		Log.get().setLevel(java.util.logging.Level.SEVERE);
	}
	
	private class MyThread extends Thread{ //ArticlePusher thread
		private Socket rSocket;
		private Article article;
		MyThread(Socket rSocket, Article art){
			this.rSocket = rSocket;
			this.article=art;
		}
		public ArticlePusher ap;
		public void run() {
			try {
				ap = new ArticlePusher(rSocket, false, "");
			//	FeedConnectionCollector.getInstance().add(ap);
				ap.writeArticle(article);
			} catch (IOException e) {
				//if(!FeedConnectionCollector.getInstance().isAlive())//PushCollectorClose
					e.printStackTrace();
			}
		}
	}

	@Ignore
	@Test
    public void PushLocalTest() throws IOException, StorageBackendException {
		
		when(this.aprov.readFile("local.test", "Blank.gif")).thenReturn(Base64.getDecoder().decode("R0lGODlhAQABAIAAAP///wAAACH5BAAAAAAALAAAAAABAAEAAAICRAEAOw=="));//file contains fileName
        
        Article art = new Article(0, null, "<foobar@hschan.ano>", "hschan.ano", null, "subj", "message", 1367497004, "hschan.ano", "local.test", "Blank.gif", "image/gif");
        
        MyThread myT = new MyThread(rSocket, art);
        myT.start();
        rOut.println("200 hello");
        rOut.flush();
        
        IhaveCommand ihave = new IhaveCommand();
        
        String ihavestr = rIn.readLine();
        
        assertEquals("IHAVE <foobar@hschan.ano>",ihavestr);
        
        ihave.processLine(conn, ihavestr, ihavestr.getBytes(charset));
        verify(conn, atLeastOnce()).println(startsWith("335")); //Send article
        
        rOut.println("335 send");
        rOut.flush();
        do{
        	String s = rIn.readLine();
        	ihave.processLine(conn, s, s.getBytes(charset));
        	//System.out.println(s);	
        }while(rIn.ready());
        do{
        	String s = rIn.readLine();
        	ihave.processLine(conn, s, s.getBytes(charset));
        	//System.out.println(s);
        }while(rIn.ready());
        verify(conn, atLeastOnce()).println(startsWith("235"));
	}
	
	@Ignore
	@Test
	public void PushRemoteTest() throws IOException, StorageBackendException, InterruptedException{
		when(this.aprov.readFile("local.test", "Blank.gif")).thenReturn(Base64.getDecoder().decode("R0lGODlhAQABAIAAAP///wAAACH5BAAAAAAALAAAAAABAAEAAAICRAEAOw=="));//file contains fileName
		Article art = new Article(0, null, "<foobar@hschan.ano>", "hschan.ano", null, "subj", "message", 1367497004, "hschan.ano", "local.test", "Blank.gif", "image/gif"); //not used actually.
		art.setRaw((art.buildNNTPMessage(charset, 0)+"\r\n").getBytes(charset)); //cached Raw has "\r\n" an the end.
		
		MyThread myT = new MyThread(rSocket, art);
        myT.start();
        
        rOut.println("200 hello");
        rOut.flush();
        
        IhaveCommand ihave = new IhaveCommand();
        
        String ihavestr = rIn.readLine();
        
        assertEquals("IHAVE <foobar@hschan.ano>",ihavestr);
        
        ihave.processLine(conn, ihavestr, ihavestr.getBytes(charset));
        verify(conn, atLeastOnce()).println(startsWith("335")); //Send article
        
        rOut.println("335 send");
        rOut.flush();
        
        do{
        	String s = rIn.readLine();
        //	System.out.println(s);
        	ihave.processLine(conn, s, s.getBytes(charset));
        }while(rIn.ready());
        verify(conn, atLeastOnce()).println(startsWith("235"));
	}
	
	@Ignore //Depricated
	@Test
	public void PushCollectorClose() throws IOException, StorageBackendException, InterruptedException{
		//FeedConnectionCollector.getInstance().start();
		
		when(this.aprov.readFile("local.test", "Blank.gif")).thenReturn(Base64.getDecoder().decode("R0lGODlhAQABAIAAAP///wAAACH5BAAAAAAALAAAAAABAAEAAAICRAEAOw=="));//file contains fileName
		Article art = new Article(0, null, "<foobar@hschan.ano>", "hschan.ano", null, "subj", "message", 1367497004, "hschan.ano", "local.test", "Blank.gif", "image/gif"); //not used actually.
		art.setRaw((art.buildNNTPMessage(charset, 0)+"\r\n").getBytes(charset)); //cached Raw has "\r\n" an the end.
		
		MyThread myT = new MyThread(rSocket, art);
        myT.start();
        
        rOut.println("200 hello");
        rOut.flush();
        
        IhaveCommand ihave = new IhaveCommand();
        
        String ihavestr = rIn.readLine();
        
        assertEquals("IHAVE <foobar@hschan.ano>",ihavestr);
        
        ihave.processLine(conn, ihavestr, ihavestr.getBytes(charset));
        verify(conn, atLeastOnce()).println(startsWith("335")); //Send article
        myT.join();
        /*rOut.println("335 send");
        rOut.flush();
        
        do{
        	String s = rIn.readLine();
        //	System.out.println(s);
        	ihave.processLine(conn, s, s.getBytes(charset));
        }while(rIn.ready());
        verify(conn, atLeastOnce()).println(startsWith("235"));*/
	}
	
	@Test
	public void splitToBuffersTest() throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, NoSuchFieldException{
		String string = "12345678910123456789101234567891012345678910123456789101234567891012345678910123456789101234567891012345678910123456789101234567891012345678910123456789101234567891012345678910123456789101234567891012345678910"
				+"123456789101234567891012345678910123456789101234567891012345678910123456789101234567891012345678910123456789101234567891012345678910123456789101234567891012345678910123456789101234567891012345678910123456789105"
				+"12345678910123456789101234567891012345678910123456789101234567891012345678910123456789101234567891012345678910123456789101234567891012345678910123456789101234567891012345678910123456789101234567891012345678910"
				+"123456789101234567891012345678910123456789101234567891012345678910123456789101234567891012345678910123456789101234567891012345678910123456789101234567891012345678910123456789101234567891012345678910123456789106"
				+"12345678910123456789101234567891012345678910123456789101234567891012345678910123456789101234567891012345678910123456789101234567891012345678910123456789101234567891012345678910123456789101234567891012345678910"
				+"123456789101234567891012345678910123456789101234567891012345678910123456789101234567891012345678910123456789101234567891012345678910123456789101234567891012345678910123456789101234567891012345678910123456789107d"
				+"12345678910123456789101234567891012345678910123456789101234567891012345678910123456789101234567891012345678910123456789101234567891012345678910123456789101234567891012345678910123456789101234567891012345678910";
		Object nothing[] = new Object[]{};
		Method splitToBuffers = ArticlePusher.class.getDeclaredMethod("splitToBuffers", new Class<?>[]{byte[].class});
		splitToBuffers.setAccessible(true);
		ChannelLineBuffers clBuffers = new ChannelLineBuffers();
		
		Field lineBuffers = ArticlePusher.class.getDeclaredField("lineBuffers");
		lineBuffers.setAccessible(true);
		
		ArticlePusher articlePusher = mock(ArticlePusher.class);
		lineBuffers.set(articlePusher, clBuffers);
		splitToBuffers.invoke( articlePusher, string.getBytes()); //"buf" is a part of ChannelLineBuffers
		
		ByteBuffer b = clBuffers.getOutputBuffer();
		assertTrue(b.limit() == ChannelLineBuffers.BUFFER_SIZE);
		b.position(b.limit());
		b = clBuffers.getOutputBuffer();
		assertTrue(b.limit() == (string.getBytes().length - ChannelLineBuffers.BUFFER_SIZE));
		b.position(b.limit());
		b = clBuffers.getOutputBuffer();
		assertTrue(b == null);

		
	}


}
