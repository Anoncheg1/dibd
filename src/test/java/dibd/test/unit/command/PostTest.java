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
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;
import org.mockito.Mockito;
import dibd.daemon.NNTPInterface;
import dibd.daemon.TLS;
import dibd.daemon.command.PostCommand;
import dibd.storage.GroupsProvider;
import dibd.storage.NNTPCacheProvider;
import dibd.storage.StorageBackendException;
import dibd.storage.StorageManager;
import dibd.storage.StorageNNTP;
import dibd.storage.SubscriptionsProvider;
import dibd.storage.GroupsProvider.Group;

/**
 * RecievingService class test too.
 * @author user
 *
 */
public class PostTest {
	
	private StorageNNTP storage; //mock
	private NNTPInterface conn; //mock
	private Constructor<?> groupC;
	
	public PostTest() throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, StorageBackendException, NoSuchMethodException, SecurityException {
		storage = Mockito.mock(StorageNNTP.class);
		StorageManager.enableProvider(new dibd.test.unit.storage.TestingStorageProvider(storage));
		conn = Mockito.mock(NNTPInterface.class);
		when(conn.isTLSenabled()).thenReturn(true);
		when(conn.getCurrentCharset()).thenReturn(Charset.forName("UTF-8"));
		TLS tls= Mockito.mock(TLS.class);
		when(conn.getTLS()).thenReturn(tls);
		when(tls.getPeerNames()).thenReturn(new String[]{"host"});
		
		
		StorageManager.enableNNTPCacheProvider(Mockito.mock(NNTPCacheProvider.class));
		
		//mocking peers
		SubscriptionsProvider sp = Mockito.mock(SubscriptionsProvider.class);  
		StorageManager.enableSubscriptionsProvider(sp);
		when(sp.has("hschan.ano")).thenReturn(true);
		
		//mocking Group
		GroupsProvider gp = Mockito.mock(GroupsProvider.class);  
		StorageManager.enableGroupsProvider(gp);
		
		Class<?> cg = Group.class;
		groupC = cg.getDeclaredConstructor(new Class[]{String.class, Integer.TYPE, Integer.TYPE, Set.class});
		groupC.setAccessible(true);
		//System.out.println(""+groupC);
		//for( Constructor<?> c : cg.getConstructors() )
			//System.out.println("asd"+c);*/
		//Log.get().setLevel(java.util.logging.Level.WARNING);
		//message-id: ???@sender - sender must be in group peers to receive the message
		
/*		Article artforpush = Mockito.mock(Article.class);
		when(artforpush.getGroupName()).thenReturn()
		when(storage.createReplay(Mockito.any(),Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(artforpush);*/
	}

	
	
	
	
	
	
	@Test
	public void postThreadTest() throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, UnsupportedEncodingException, IOException, StorageBackendException, ParseException {

		Set<String> host = new HashSet<String>(Arrays.asList("hschan.ano","host.com"));
		//name id flags hosts
		when(StorageManager.groups.get("local.test")).thenReturn(
				(Group) groupC.newInstance("local.test",23,0,host));
		when(StorageManager.groups.getName(23)).thenReturn("local.test");
				
		
		String[] send2 = { //thread
				"X-Mozilla-Status: 0001",
				"X-Mozilla-Status2: 00800000",
				"X-Mozilla-Keys:                                                                                 ",
				"Newsgroups: local.test",
				"X-Mozilla-News-Host: news://localhost:119",
				"From: user <user@localhost>",
				"Subject: newmessage",
				"Date: Sat, 3 Dec 2016 08:57:22 +0300",
				"User-Agent: Mozilla/5.0 (X11; Linux i686; rv:45.0) Gecko/20100101",
				" Thunderbird/45.5.1",
				"MIME-Version: 1.0",
				"Content-Type: text/plain; charset=utf-8; format=flowed",
				"Content-Transfer-Encoding: base64",
				"",
				"ZGRkZGRkZGRkZGRkZGRkZGRkZGRkZGRkZGRkZGRkZGRkZGRkZGRkZGRkZGRkZGRkZGRkZGRk",
				"ZGRkZGRkZGRkZGRkZGRkZGRkZGRkZGRkZGRkZGRkZGRkZGRkZGRkZGRkZGRkZGRkZGRkZGRk",
				"DQo=",
				"."
		};
		
		PostCommand c = new PostCommand();
		
		c.processLine(conn, "POST", "POST".getBytes("UTF-8"));
		verify(conn, atLeastOnce()).println(startsWith("340"));//OK
		for(int i = 1; i < send2.length; i++)
			c.processLine(conn, send2[i], send2[i].getBytes("UTF-8"));
		
		//Article art = new Article(thread_id, mId[0], mId[1], from, subjectT, message,
		//date[0], path[0], groupHeader[0], group.getInternalID());
		//Article art = new Article(null, "23456", "host.com", null, "subj", "message",
			//	"Thu, 02 May 2013 12:16:44 +0000", "hschan.ano", "local.test", 23);
		//verify(this.storage, atLeastOnce()).createThread(
			//	art, Base64.getDecoder().decode("R0lGODlhAQABAIAAAP///wAAACH5BAAAAAAALAAAAAABAAEAAAICRAEAOw=="), "image/gif"); //ReceivingService here
		verify(conn, atLeastOnce()).println(startsWith("240")); //article is accepted
	}
	
	@Test
	public void postmultipart() throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, UnsupportedEncodingException, IOException, StorageBackendException, ParseException {

		Set<String> host = new HashSet<String>(Arrays.asList("hschan.ano","host.com"));
		//name id flags hosts
		when(StorageManager.groups.get("local.test")).thenReturn(
				(Group) groupC.newInstance("local.test",23,0,host));
		when(StorageManager.groups.getName(23)).thenReturn("local.test");
				
		
		String[] send2 = { //thread
				"Content-Transfer-Encoding: 8bit",
				//"References: <koa2q6$r28$1@t1r10n.localhost>",
				"Date: Thu, 06 Mar 2014 21:08:19 +0000",
				"From: Anonymous <nobody@no.where>",
				"Content-Type: text/plain; charset=UTF-8",
				"Newsgroups: local.test",
				"Path: ev7fnjzjdbtu3miq.onion!changolia!ucavviu7wl6azuw7.onion!nntp.nsfl.tk!backdoor.nsa.gov!nntp.middlebox.tld!pNewss.Core.UCIS.nl!sfor-SRNd!sfor-SRNd!sfor-SRNd!web.overchan.imoutochan",
				"Message-Id: <dkwqfzowhm1394140099@web.overchan.imoutochan>",
				"Subject: None",
				"",
				"t",
				"."
		};
		
		PostCommand c = new PostCommand();
		
		c.processLine(conn, "POST", "POST".getBytes("UTF-8"));
		verify(conn, atLeastOnce()).println(startsWith("340"));//OK
		for(int i = 0; i < send2.length; i++)
			c.processLine(conn, send2[i], send2[i].getBytes("UTF-8"));
		
		verify(conn, atLeastOnce()).println(startsWith("240")); //article is accepted
	}

}
