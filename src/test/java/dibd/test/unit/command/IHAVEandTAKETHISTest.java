package dibd.test.unit.command;

import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import javax.mail.internet.MimeUtility;

import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import dibd.config.Config;
import dibd.daemon.NNTPInterface;
import dibd.daemon.TLS;
import dibd.daemon.command.IhaveCommand;
import dibd.daemon.command.TakeThisCommand;
import dibd.storage.GroupsProvider;
import dibd.storage.NNTPCacheProvider;
import dibd.storage.StorageBackendException;
import dibd.storage.StorageManager;
import dibd.storage.StorageNNTP;
import dibd.storage.SubscriptionsProvider;
import dibd.storage.GroupsProvider.Group;
import dibd.storage.article.Article;
//import dibd.test.unit.storage.TestingStorageProvider;
import dibd.util.Log;

/**
 * RecievingService class test too.
 * @author user
 *
 */
public class IHAVEandTAKETHISTest {
	
	
	private StorageNNTP storage; //mock
	private NNTPInterface conn; //mock
	private Constructor<?> groupC;
	
	public IHAVEandTAKETHISTest() throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, StorageBackendException, NoSuchMethodException, SecurityException, IOException {
		storage = Mockito.mock(StorageNNTP.class);
		StorageManager.enableProvider(new dibd.test.unit.storage.TestingStorageProvider(storage));
		conn = Mockito.mock(NNTPInterface.class);
		when(conn.getCurrentCharset()).thenReturn(Charset.forName("UTF-8"));
		when(conn.isTLSenabled()).thenReturn(true);
		TLS tls= Mockito.mock(TLS.class);
		when(conn.getTLS()).thenReturn(tls);
		when(tls.getPeerNames()).thenReturn(new String[]{"host"});
		
		Article art = Mockito.mock(Article.class);
		when(storage.createThread((Article)Mockito.any(), (byte[])Mockito.any(), (String)Mockito.any())).thenReturn(art);
		when(storage.createReplay((Article)Mockito.any(), (byte[])Mockito.any(), (String)Mockito.any())).thenReturn(art);
		
		StorageManager.enableNNTPCacheProvider(Mockito.mock(NNTPCacheProvider.class));
        
		
		//mocking peers
		SubscriptionsProvider sp = Mockito.mock(SubscriptionsProvider.class);  
		StorageManager.enableSubscriptionsProvider(sp);
		when(sp.has("hschan.ano")).thenReturn(true);
		
		//mocking Group
		GroupsProvider gp = Mockito.mock(GroupsProvider.class);  
		StorageManager.enableGroupsProvider(gp);
		
		Class<?> cg = Group.class;
		groupC = cg.getDeclaredConstructor(new Class[]{GroupsProvider.class, String.class, Integer.TYPE, Integer.TYPE, Set.class});
		groupC.setAccessible(true);
		
		//for( Class<?> c : groupC.getParameterTypes() )
			//System.out.println("asd"+c);
//		Log.get().setLevel(java.util.logging.Level.SEVERE);
		//message-id: ???@sender - sender must be in group peers to receive the message
		
		Log.get().setLevel(java.util.logging.Level.SEVERE);
		//DEBUG Hook conn.println(String)
	/*	
		 Mockito.doAnswer(new Answer() {
	      public Object answer(InvocationOnMock invocation) {
	          Object[] args = invocation.getArguments();
	          System.out.println(args[0]);
	          return null;
	      }})
	  .when(conn).println(Mockito.anyString());
	  Log.get().setLevel(java.util.logging.Level.ALL);
*/		
	}

	
	
	
	
	@Test
	public void IhaveReplayTest() throws StorageBackendException, IOException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, ParseException{
		Config.inst().set(Config.HOSTNAME, "not-in-path.com"); //added to path
		Article art0 = Mockito.mock(Article.class);//empty
		when(storage.getArticle("<ref-message-id@foo.bar>", null)).thenReturn(art0);
		when(art0.getThread_id()).thenReturn( 105 );
		
		Set<String> host = new HashSet<String>(Arrays.asList("hschan.ano","host.com"));
		//name id flags hosts
		when(StorageManager.groups.get("local.test")).thenReturn(
				(Group) groupC.newInstance(StorageManager.groups,"local.test",23,0,host));
		
		String send1 = "ihave <23456@host.com>";
		String[] send2 = {
				"Mime-Version: 1.0",
				"From: "+MimeUtility.encodeWord("петрик <foo@bar.ano>"),
				"Date: Thu, 02 May 2013 12:16:44 +0000",
				"Message-ID: <23456@host.com>",
				"Newsgroups: local.test",
				"Subject: "+MimeUtility.encodeWord("ыффывфыв фыв фыв фыв фыв фыв фыв фыв фыв фыв фыв фы"),
				"references: <ref-message-id@foo.bar>",
				"Path: hschan.ano!dontcare", //circle check
				"Content-type: text/plain; charset=utf-8",
				"X-Sage: optional",
				"",//empty line separator
				"message"
		};
		//String send3 = "bWVzc2FnZQ==";
		
		IhaveCommand c = new IhaveCommand();
		c.processLine(conn, send1, send1.getBytes("UTF-8")); //send ihave
		verify(this.storage).getArticle("<23456@host.com>", null); //ReceivingService there is no such article yet
		verify(conn, atLeastOnce()).println(startsWith("335")); //Send article
		for(int i = 0; i < send2.length; i++)
			c.processLine(conn, send2[i], send2[i].getBytes("UTF-8")); //headers
		
		c.processLine(conn, ".", ".".getBytes("UTF-8")); //the end

		//Article art = new Article(thread_id, mId[0], mId[1], from, subjectT, message,
		//date[0], path[0], groupHeader[0], group.getInternalID());
		Article art = new Article(105, "<23456@host.com>", "host.com", "петрик <foo@bar.ano>", "ыффывфыв фыв фыв фыв фыв фыв фыв фыв фыв фыв фыв фы", "message",
				"Thu, 02 May 2013 12:16:44 +0000", "hschan.ano!dontcare", "local.test", 23);
		verify(this.storage, atLeastOnce()).createReplay(art, null, null); //ReceivingService here
		verify(conn, atLeastOnce()).println(startsWith("235")); //article is accepted		

	}
	
	
	@Test
	public void IhaveThreadTest1() throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, UnsupportedEncodingException, IOException, StorageBackendException, ParseException {

		Set<String> host = new HashSet<String>(Arrays.asList("hschan.ano","host.com"));
		//name id flags hosts
		when(StorageManager.groups.get("local.test")).thenReturn(
				(Group) groupC.newInstance(StorageManager.groups,"local.test",23,0,host));
				
		String send1 = "ihave <23456@host.com>";
		String[] send2 = {
				"Mime-Version: 1.0",
				"Date: Thu, 02 May 2013 12:16:44 +0000",
				"Message-ID: <23456@host.com>",
				"Newsgroups: local.test",
				"Subject: subj",
				//"references: <23456@host.com>",
				"Path: hschan.ano",
				"Content-Type: multipart/mixed;",
				"    boundary=\"=-=-=__O8KsN2iGKO4xUESptbCjDG14G__=-=-=\"",
				"",
				"--=-=-=__O8KsN2iGKO4xUESptbCjDG14G__=-=-=",
				"Content-type: text/plain; charset=utf-8",
				"Content-Transfer-Encoding: base64",
				"",
				"bWVzc2FnZQ==",
				"--=-=-=__O8KsN2iGKO4xUESptbCjDG14G__=-=-=",
				"Content-Type: image/gif",
				"Content-Disposition: attachment; filename=\"Blank.gif\"",
				"Content-Transfer-Encoding: base64",
				"",
				"R0lGODlhAQABAIAAAP///wAAACH5BAAAAAAALAAAAAABAAEAAAICRAEAOw==",
				"--=-=-=__O8KsN2iGKO4xUESptbCjDG14G__=-=-=--",
				"."
		};
		
		IhaveCommand c = new IhaveCommand();
		
		c.processLine(conn, send1, send1.getBytes("UTF-8")); //send ihave
		verify(this.storage).getArticle("<23456@host.com>", null); //ReceivingService there is no such article yet
		verify(conn, atLeastOnce()).println(startsWith("335")); //Send article
		for(int i = 0; i < send2.length; i++){
			c.processLine(conn, send2[i], send2[i].getBytes("UTF-8"));
		}
		
		//Article art = new Article(thread_id, mId[0], mId[1], from, subjectT, message,
		//date[0], path[0], groupHeader[0], group.getInternalID());
		Article art = new Article(null, "<23456@host.com>", "host.com", null, "subj", "message",
				"Thu, 02 May 2013 12:16:44 +0000", "hschan.ano", "local.test", 23);
		verify(this.storage, atLeastOnce()).createThread(
				art, Base64.getDecoder().decode("R0lGODlhAQABAIAAAP///wAAACH5BAAAAAAALAAAAAABAAEAAAICRAEAOw=="),"image/gif"); //ReceivingService here
		verify(conn, atLeastOnce()).println(startsWith("235")); //article is accepted
	}
	
	
	@Test
	public void IhaveThreadTest2() throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, UnsupportedEncodingException, IOException, StorageBackendException, ParseException {

		Set<String> host = new HashSet<String>(Arrays.asList("hschan.ano","host.com"));
		//name id flags hosts
		when(StorageManager.groups.get("local.test")).thenReturn(
				(Group) groupC.newInstance(StorageManager.groups,"local.test",23,0,host));
				
		String send1 = "ihave <23456@host.com>";
		String[] send2 = {"MIME-Version: 1.0",
				"From: user <user@localhost>",
				"Date: Tue, 06 Dec 2016 03:59:13 +0000",
				"Message-ID: <23456@host.com>",
				"Newsgroups: local.test",
				"Subject: ads",
				"References: <23456@host.com>",
				"Path: hschan.ano",
				"Content-Transfer-Encoding: 8bit",
				"Content-Type: multipart/mixed; boundary=\"=-=-=_YuvJRCL-CTdKhieQa1YukmzO2SRtJsxK3_=-=-=\"",
				"",
				"--=-=-=_YuvJRCL-CTdKhieQa1YukmzO2SRtJsxK3_=-=-=",
				"Content-Type: text/plain; charset=utf-8",
				"Content-Transfer-Encoding: base64",
				"",
				"YXNhc2QKYXNkCmFzZA==",
				"--=-=-=_YuvJRCL-CTdKhieQa1YukmzO2SRtJsxK3_=-=-=",
				"Content-Type: image/png",
				"Content-Disposition: attachment; filename=\"705730.png\"",
				"Content-Transfer-Encoding: base64",
				"",
				"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",
				"AABJRU5ErkJggg==",
				"",
				"--=-=-=_YuvJRCL-CTdKhieQa1YukmzO2SRtJsxK3_=-=-=--",
				"."
		};
		
		IhaveCommand c = new IhaveCommand();
		
		c.processLine(conn, send1, send1.getBytes("UTF-8")); //send ihave
		verify(this.storage).getArticle("<23456@host.com>", null); //ReceivingService there is no such article yet
		verify(conn, atLeastOnce()).println(startsWith("335")); //Send article
		for(int i = 0; i < send2.length; i++){
			c.processLine(conn, send2[i], send2[i].getBytes("UTF-8"));
		}
		
		//Article art = new Article(thread_id, mId[0], mId[1], from, subjectT, message,
		//date[0], path[0], groupHeader[0], group.getInternalID());
		//Article art = new Article(null, "23456", "host.com", null, "subj", "message",
			//	"Thu, 02 May 2013 12:16:44 +0000", "hschan.ano", "local.test", 23);
		//verify(this.storage, atLeastOnce()).createThread(
			//	art, Base64.getDecoder().decode("R0lGODlhAQABAIAAAP///wAAACH5BAAAAAAALAAAAAABAAEAAAICRAEAOw=="), "image/gif"); //ReceivingService here
		verify(conn, atLeastOnce()).println(startsWith("235")); //article is accepted
	}
	
	
	@Test
	@Ignore
	public void TakeThisReplayTest() throws StorageBackendException, IOException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, ParseException{
		Article art0 = Mockito.mock(Article.class);//empty
		//when(storage.getArticle("<23456@host.com>", null)).thenReturn(art0);//ref
		when(storage.getArticle("<ref-message-id@foo.bar>", null)).thenReturn(art0);//ref
		when(art0.getThread_id()).thenReturn( 105 );
		
		Set<String> host = new HashSet<String>(Arrays.asList("hschan.ano","host.com"));
		//name id flags hosts
		when(StorageManager.groups.get("local.test")).thenReturn(
				(Group) groupC.newInstance(StorageManager.groups,"local.test",23,0,host));
		
		String send1 = "takethis <23456@host.com>";
		String[] send2 = {
				"Mime-Version: 1.0",
				"From: "+MimeUtility.encodeWord("петрик <foo@bar.ano>"),
				"Date: Thu, 02 May 2013 12:16:44 +0000",
				"Message-ID: <23456@host.com>",
				"Newsgroups: local.test",
				"Subject: "+MimeUtility.encodeWord("ыффывфыв фыв фыв фыв фыв фыв фыв фыв фыв фыв фыв фы ыффывфыв фыв фыв фыв фыв фыв фыв фыв фыв фыв фыв фы"),
				"references: <ref-message-id@foo.bar>",
				"Path: hschan.ano!dontcare",
				"Content-type: text/plain; charset=utf-8",
				"X-Sage: optional",
				"",//empty line separator
				"message"
		};
		//String send3 = "bWVzc2FnZQ==";
		
		TakeThisCommand c = new TakeThisCommand();
		c.processLine(conn, send1, send1.getBytes("UTF-8")); //send ihave
		verify(this.storage).getArticle("<23456@host.com>", null); //ReceivingService there is no such article yet
		
		for(int i = 0; i < send2.length; i++)
			c.processLine(conn, send2[i], send2[i].getBytes("UTF-8")); //headers
		
		c.processLine(conn, ".", ".".getBytes("UTF-8")); //the end

		//Article art = new Article(thread_id, mId[0], mId[1], from, subjectT, message,
		//date[0], path[0], groupHeader[0], group.getInternalID());
		Article art = new Article(105, "<23456@host.com>", "host.com", "петрик <foo@bar.ano>", "ыффывфыв фыв фыв фыв фыв фыв фыв фыв фыв фыв фыв фы ыффывфыв фыв фыв фыв фыв фыв фыв фыв фыв фыв фыв фы", "message",
				"Thu, 02 May 2013 12:16:44 +0000", "hschan.ano!dontcare", "local.test", 23);
		verify(this.storage, atLeastOnce()).createReplay(art, null, null); //ReceivingService here
		verify(conn, atLeastOnce()).println(startsWith("239")); //article is accepted		

	}

}
