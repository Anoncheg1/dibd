package dibd.test.unit.feed;

import static org.mockito.Mockito.when;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;

import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;

import dibd.config.Config;
import dibd.daemon.NNTPInterface;
import dibd.daemon.command.CapabilitiesCommand;
import dibd.daemon.command.IhaveCommand;
import dibd.feed.ArticlePuller;
import dibd.storage.GroupsProvider;
import dibd.storage.GroupsProvider.Group;
import dibd.util.Log;
import dibd.storage.StorageBackendException;
import dibd.storage.StorageManager;
import dibd.storage.StorageNNTP;



 //in dev //WORKING! but fail sometimes because of 2 threads when only 1 must be used.
public class ArticlePullerTest {
	
	private StorageNNTP storage; //mock
	private Constructor<?> groupC;
	
	
	private String mId = "<foobar@hschan.ano>";
	private String[] send2 = { //thread
			"Mime-Version: 1.0",
			"Date: Thu, 02 May 2013 12:16:44 +0000",
			"Message-ID: <foobar@hschan.ano>",
			"Newsgroups: local.test",
			"Subject: subj",
			//"references: <foobar@hschan.ano>",
			"Path: hschan.ano",
			"Content-Type: multipart/mixed;",
			"    boundary=\"=-=-=__O8KsN2iGKO4xUESptbCjDG14G__=-=-=\"",
			"",
			"--=-=-=__O8KsN2iGKO4xUESptbCjDG14G__=-=-=",
			"Content-type: text/plain; charset=utf-8",
			"Content-Transfer-Encoding: base64",
			"",
			"bWVzc2FnZQ==",
			"",
			"--=-=-=__O8KsN2iGKO4xUESptbCjDG14G__=-=-=",
			"Content-Type: image/gif",
			"Content-Disposition: attachment; filename=\"Blank.gif\"",
			"Content-Transfer-Encoding: base64",
			"",
			"R0lGODlhAQABAIAAAP///wAAACH5BAAAAAAALAAAAAABAAEAAAICRAEAOw==",
			"",
			"--=-=-=__O8KsN2iGKO4xUESptbCjDG14G__=-=-=--",
			"."
	};
	
	/*
	private class MyThread extends Thread{ //ArticlePuller thread
		private Socket rSocket;
		private Hashtable<Group, Long> groupsTime;
		public IhaveCommand ihc;
		MyThread(Socket rSocket, Hashtable<Group, Long> groupsTime){
			this.rSocket = rSocket;
			this.groupsTime = groupsTime;
		}
    	public ArticlePuller ap;	
    	public void run() {
    		try {
    			ap = new ArticlePuller(rSocket);
    			List<String> mIDs = ap.checkNew(groupsTime);
    			assertTrue(!mIDs.isEmpty());
    			Thread.sleep(200);
				if (mIDs.isEmpty())
					return;
				else
					for (String mId : mIDs){//1 id
						ihc = mock(IhaveCommand.class);
						ap.transferToItself(ihc, mId);
					}
    		} catch (IOException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		} catch (StorageBackendException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	}
    }
	*/
	private Socket rSocket;
	private	BufferedReader rIn;
	private PrintWriter rOut;
	private IhaveCommand ihcom;
	
	public ArticlePullerTest() throws NoSuchMethodException, SecurityException, IOException {
		//Storage
		storage = mock(StorageNNTP.class);
		StorageManager.enableProvider(new dibd.test.unit.storage.TestingStorageProvider(storage));
		
		//group mocking part 1
		Class<?> cg = Group.class;
		groupC = cg.getDeclaredConstructor(new Class[]{String.class, Integer.TYPE, Integer.TYPE, Set.class});
		groupC.setAccessible(true);
		
		
		//preparing remote connection Socket First parameter for ArticlePuller
		//and pipeline fot testing
		rSocket = mock(Socket.class);
		PipedInputStream inForOut = new PipedInputStream();
		PipedOutputStream outForIn = new PipedOutputStream();
		rIn = new BufferedReader(new InputStreamReader(inForOut, "UTF-8"));
		rOut = new PrintWriter(new OutputStreamWriter(outForIn, "UTF-8"));

		when(rSocket.getOutputStream()).thenReturn(new PipedOutputStream(inForOut));
		when(rSocket.getInputStream()).thenReturn(new PipedInputStream(outForIn));

		ihcom = mock(IhaveCommand.class); //Second parameter for ArticlePuller
		
		
		
		
		Log.get().setLevel(java.util.logging.Level.WARNING);
	}

	@Test
    public void PullNEWNEWStest() throws StorageBackendException, IOException, InterruptedException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchFieldException, SecurityException{
		//group mocking part 2
		Set<String> host = new HashSet<String>(Arrays.asList("hschan.ano","host.com"));
		final Group group1 = (Group) groupC.newInstance("local.test",23,0,host);
		//final Group group2 = (Group) groupC.newInstance(StorageManager.groups,"random",24,0,host);
		//when(storage.getLastPostOfGroup(group1)).thenReturn((long) 140000);//it is inside of newnews
		//when(storage.getLastPostOfGroup(group2)).thenReturn((long) 0);//it is inside of newnews
		Hashtable<Group, Long> groupsTime = new Hashtable<Group, Long>();//groups with last post time //for ArticlePuller.check()
		groupsTime.put(group1, (long) 140000);
		//groupsTime.put(group2, (long) 0);
		
		
		
        
        
        
        //MyThread myT = new MyThread(rSocket, groupsTime);
        //myT.start();
      //Thread.sleep(100); //waiting for ArticlePuller.ap constructor
        
        // ######## ArticlePuller Constructor ########  
        rOut.println("200 hello");
        rOut.flush();
        
        ArticlePuller ap = new ArticlePuller(rSocket, false, "testhost");
        
        // ######## ap.check() ######## 
        Field connField = ArticlePuller.class.getDeclaredField("conn");
        connField.setAccessible(true);
        NNTPInterface conn = (NNTPInterface) connField.get(ap); // now we have hook field for IhaveCommand
        
        //CAPABILITIES
        rOut.println("101 Capabilities list:");
        for (String cap : CapabilitiesCommand.CAPABILITIES) {  //with NEWNEWS
        	rOut.println(cap);
        }
        rOut.println(".");
        
        //NEWNEWS resp
        rOut.println("230 List of new articles follows (multi-line)");
		
		rOut.println("<a@hh> <sad@hh>");
		rOut.println("<agh@hh> "+mId);
		rOut.println(mId);
		rOut.println(".");
		rOut.flush();
		Log.get().setLevel(Level.SEVERE);
		Map<String, Boolean> mIDs = ap.check(groupsTime, Config.inst().get(Config.PULLDAYS, 1)); //newnews request
		Log.get().setLevel(Level.WARNING);
		assertEquals(mIDs.get(mId), true); //thread = true
		assertEquals(mIDs.get("<a@hh>"), null); //no such thread
		assertEquals(mIDs.get("<agh@hh>"), false); //replay = false

		assertEquals(rIn.readLine(), "CAPABILITIES");

		String ne = "NEWTHREADS local.test "+(140000-60*60*24);
		assertEquals(rIn.readLine(), ne);

		// ######## ap.transferToItself() ########
		//Article
		rOut.println("220 " + 0 + " " + mId + " article retrieved - head and body follow");
        for(int i = 0; i < send2.length; i++){
        	rOut.println(send2[i]);
		}
        rOut.flush();
        
        //ihave emulation
        conn.println("335 send article");
        conn.println("235 article posted ok");
        
		boolean res = ap.transferToItself(ihcom, mId);//IhaveCommand can't be reused.
		assertEquals(rIn.readLine(), "ARTICLE " + mId);
		assertTrue(res);
		//######## ap.close() ########
		rOut.println("qua");
		rOut.flush();
		
		ap.close();
		
		assertEquals(rIn.readLine(), "QUIT");
		
		
		
        
        /*
        assertEquals("ARTICLE " + mId, rIn.readLine());
        verify(myT.ihc, atLeastOnce()).processLine(Mockito.eq(conn), Mockito.eq("IHAVE <foobar@hschan.ano>"), Mockito.any());
        rOut.println("220 " + 0 + " " + mId + " article retrieved - head and body follow"); rOut.flush();
        
        for(int i = 0; i < send2.length-1; i++){
        	rOut.println(send2[i]);rOut.flush();
        	
        	Thread.sleep(50);
        	verify(myT.ihc, atLeastOnce()).processLine(Mockito.eq(conn), Mockito.eq(send2[i]), Mockito.any());
        	
		}
        //there is 2 threads here, but it must be only one. We use socket to slow down thread.
        
        conn.println("235 article posted ok"); //self
        rOut.println(send2[send2.length-1]); rOut.flush(); //"."
        
        myT.join(1000);
        assertTrue(!myT.isAlive());
        */
		//Log.get
        /*
    	Mockito.doAnswer(new Answer<Object>() { //self IHAVE body
    		public Object answer(InvocationOnMock invocation) throws IOException {
    			//System.out.println(invocation.getArguments()[1]);
    			assertEquals(send2l.poll(), invocation.getArguments()[1]);
    			return null;
    		}})
    	.when(ihcom).processLine(Mockito.any(NNTPChannel.class), Mockito.anyString(), Mockito.any(byte[].class));
    	*/
	}
	
	@Test
    public void PullXOVERtest() throws StorageBackendException, IOException, InterruptedException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchFieldException, SecurityException{
		//group mocking part 2
		Set<String> host = new HashSet<String>(Arrays.asList("hschan.ano","host.com"));
		final Group group1 = (Group) groupC.newInstance("local.test",23,0,host);
		//final Group group2 = (Group) groupC.newInstance(StorageManager.groups,"random",24,0,host);
		//when(storage.getLastPostOfGroup(group1)).thenReturn((long) 140000);//it is inside of newnews
		//when(storage.getLastPostOfGroup(group2)).thenReturn((long) 0);//it is inside of newnews
		Hashtable<Group, Long> groupsTime = new Hashtable<Group, Long>();//groups with last post time //for ArticlePuller.check()
		groupsTime.put(group1, (long) 140000);
		//groupsTime.put(group2, (long) 0);
		
		
        
        // ######## ArticlePuller Constructor ########  
        rOut.println("200 hello");
        rOut.flush();
        
        ArticlePuller ap = new ArticlePuller(rSocket, false, "testhost");
        
        // ######## ap.check() ######## 
        Field connField = ArticlePuller.class.getDeclaredField("conn");
        connField.setAccessible(true);
        NNTPInterface conn = (NNTPInterface) connField.get(ap); // now we have hook field for IhaveCommand
        
        //CAPABILITIES
        rOut.println("101 Capabilities list:");
        //for (String cap : CapabilitiesCommand.CAPABILITIES) {  //with NEWNEWS
        	rOut.println("READER");
        //}
        rOut.println(".");

        //GROUP response
        rOut.println("211 bla bla bla group selected");
        
        //OVER resp
        rOut.println("224 overview information follows");
        rOut.println("\t\t\t\t<a2@hh>\t"+mId);
        rOut.println("\t\t\t\t"+mId);
		rOut.println("\t\t\t\t<a@hh>\t<sad@hh>");
		rOut.println(".");
		rOut.flush();
		Log.get().setLevel(Level.SEVERE);
		Map<String, Boolean> mIDs = ap.check(groupsTime, Config.inst().get(Config.PULLDAYS, 1)); //newnews request
		Log.get().setLevel(Level.WARNING);
		assertEquals(mIDs.get(mId), true); //thread = true
		assertEquals(mIDs.get("<a@hh>"), null); //no thread
		assertEquals(mIDs.get("<a2@hh>"), false); //replay = false
		assertEquals(mIDs.size(), 2); //replay = false

		assertEquals(rIn.readLine(), "CAPABILITIES");

		String grs = "GROUP local.test";
		assertEquals(rIn.readLine(), grs);
		
		String xo = "XOVER 0";
		assertEquals(rIn.readLine(), xo);

		// ######## ap.transferToItself() ########
		//Article
		rOut.println("220 " + 0 + " " + mId + " article retrieved - head and body follow");
        for(int i = 0; i < send2.length; i++){
        	rOut.println(send2[i]);
		}
        rOut.flush();
        
        //ihave emulation
        conn.println("335 send article");
        conn.println("235 article posted ok");
        
		boolean res = ap.transferToItself(ihcom, mId);//IhaveCommand can't be reused.
		assertEquals(rIn.readLine(), "ARTICLE " + mId);
		assertTrue(res);
		//######## ap.close() ########
		rOut.println("qua");
		rOut.flush();
		
		ap.close();
		
		assertEquals(rIn.readLine(), "QUIT");
		
	}

}
