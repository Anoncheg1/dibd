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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.mail.internet.MimeUtility;

import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.mockito.verification.VerificationMode;

import dibd.daemon.NNTPInterface;
import dibd.daemon.command.IhaveCommand;
import dibd.daemon.command.OverCommand;
import dibd.storage.GroupsProvider;
import dibd.storage.ScrapLine;
import dibd.storage.StorageManager;
import dibd.storage.StorageNNTP;
import dibd.storage.GroupsProvider.Group;
import dibd.storage.article.Article;
import dibd.test.unit.storage.TestingStorageProvider;
import dibd.storage.StorageBackendException;

@Ignore
public class OverTest {
	
	private NNTPInterface conn; //mock
	private Constructor<?> groupC;
	private StorageNNTP storage; //mock

	public OverTest() throws NoSuchMethodException, SecurityException, IOException {
		//conn
		conn = Mockito.mock(NNTPInterface.class);
		when(conn.getCurrentCharset()).thenReturn(Charset.forName("UTF-8"));
		
		//group
		Class<?> cg = Group.class;
		groupC = cg.getDeclaredConstructor(new Class[]{String.class, Integer.TYPE, Integer.TYPE, Set.class});
		groupC.setAccessible(true);
		
		//storage
		storage = Mockito.mock(StorageNNTP.class);
		StorageManager.enableProvider(new TestingStorageProvider(storage));
		
		/*Mockito.doAnswer(new Answer() {
		      public Object answer(InvocationOnMock invocation) {
		          Object[] args = invocation.getArguments();
		          System.out.println(args[0]);
		          return null;
		      }})
		  .when(conn).println(Mockito.anyString());*/
	}

	@Test
	public void xoverTest() throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, UnsupportedEncodingException, IOException, StorageBackendException, ParseException{
		Set<String> host = new HashSet<String>(Arrays.asList("hschan.ano","host.com"));
		Group group = (Group) groupC.newInstance("local.test",23,0, host);
		when(conn.getCurrentGroup()).thenReturn((Group) groupC.newInstance("local.test",23,0, host));
		
		//we sort in jdbc
		List<ScrapLine> slist = new ArrayList<>();
		slist.add(new ScrapLine(1, "<mid@host>", 4));
		slist.add(new ScrapLine(1, "<mid2@host>", 5));
		slist.add(new ScrapLine(3, "<mid3@host>", 2));
		slist.add(new ScrapLine(3, "<mid4@host>", 3));
		when(storage.scrapGroup(group, Integer.MAX_VALUE)).thenReturn(slist);
		
		/*Article art = new Article(1, 1, "<aa@host.com>", "host.com", "петрик <foo@bar.ano>", "subject", "message", 
				154456767, "hschan.ano!dontcare", "local.test", null, null);
		Article art2 = new Article(2, 1, "<aa2@host.com>", "host.com", "петрик <foo@bar.ano>", "ыффывфыв фыв фыв фыв фыв фыв фыв фыв фыв фыв фыв фы", "message", 
				154456767, "hschan.ano!dontcare", "local.test", null, null);
				//new Article(1, "<aa@host.com>", "host.com", "петрик <foo@bar.ano>", "ыффывфыв фыв фыв фыв фыв фыв фыв фыв фыв фыв фыв фы", "message",
				//"Thu, 02 May 2013 12:16:44 +0000", "hschan.ano!dontcare", "local.test", 23, 0);
		when(storage.getOneThread(1, group.getName(), 0)).thenReturn(Arrays.asList(art, art2));*/
		
		
		
		OverCommand c = new OverCommand();
		
		c.processLine(conn, "XOVER 0", "XOVER 0".getBytes("UTF-8"));
		verify(conn, atLeastOnce()).println(startsWith("224"));
		verify(conn, atLeastOnce()).println("1\t\t\tThu, 01 Jan 1970 00:00:04 +0000\t<mid@host>");
		verify(conn, atLeastOnce()).println("2\t\t\tThu, 01 Jan 1970 00:00:05 +0000\t<mid2@host>");
		verify(conn, atLeastOnce()).println("3\t\t\tThu, 01 Jan 1970 00:00:02 +0000\t<mid3@host>");
		verify(conn, atLeastOnce()).println(".");
		
	}

}
