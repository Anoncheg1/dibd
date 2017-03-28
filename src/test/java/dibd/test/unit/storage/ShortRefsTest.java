/**
 * 
 */
package dibd.test.unit.storage;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;

import dibd.daemon.NNTPInterface;
import dibd.storage.StorageBackendException;
import dibd.storage.StorageManager;
import dibd.storage.StorageNNTP;
import dibd.storage.GroupsProvider.Group;
import dibd.storage.article.Article;
import dibd.storage.web.ShortRefParser;
import dibd.storage.web.StorageWeb;
import dibd.storage.web.WebRef;

/**
 * Test for short reference algorithms.
 * 
 * 
 * @author user
 *
 */
public class ShortRefsTest {
	
	StorageWeb db;//mock
	
	public ShortRefsTest() {
		super();
		
		db = Mockito.mock(StorageWeb.class);
		

	}

	@Test
    public void getGlobalRefsTest() throws StorageBackendException{
		
		Article art = new Article(17, 16, "<aaa@hos.com>", "host.com", null, "сабджект", "фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы\nфывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы\nфывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы\nфывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы\nфывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы\nфывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы", 
				1466589502, "host!host2", "group", null, null); //without image
		Mockito.when(db.getArticleWeb("<aaa@hos.com>", null)).thenReturn(art);
		
		Map<String, WebRef> a = ShortRefParser.getGlobalRefs(db, "<aaa@hos.com>");
		
		WebRef wr = a.get("<aaa@hos.com>");
		assertTrue(wr != null);
		assertTrue(wr.getThread_id() == 16);
		assertTrue(wr.getReplay_id() == 17);
	}

	@Test
    public void getShortRefsTest() throws StorageBackendException{
		
		Article art = new Article(16*16*16, 2, "<aaa@hos.com>", "host.com", null, "сабджект", "фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы\nфывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы\nфывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы\nфывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы\nфывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы\nфывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы", 
				1466589502, "host!host2", "group", null, null); //without image
		Mockito.when(db.getArticleWeb(null, 16*16*16)).thenReturn(art);
		
		String a = ShortRefParser.shortRefParser(db, ">>1000jh>>1000");
		
		assertTrue(a.equals("<aaa@hos.com>jh<aaa@hos.com>"));
	}
	
	@Test
    public void nntpchanLinksTest() throws StorageBackendException{
		String mId = "<0eb8c1490413069@web.oniichan.onion>";
		String sha1trun = "7239a9807e56c0b4e2";
		Map<String, String> map= new HashMap<>();
		map.put("<0eb8c1490413069@web.oniichan.onion>", null);
		//StorageManager.current().getNewArticleIDs(group, 0)
		//preperation
		StorageNNTP storage = Mockito.mock(StorageNNTP.class);
		StorageManager.enableProvider(new TestingStorageProvider(storage));
		Group group = Mockito.mock(Group.class);
		when(storage.getNewArticleIDs(group, 0)).thenReturn(map);
		
		String message =">>7239a980 blablabla>>7239\n"
				+ "blablabla >>7239a9807e56c0b4e2 >>7239\n"
				+ "blablabla >>7239a9807e56c0b4e2 >>7239\n"
				+ "blablabla(>>7239a9807e56c0b4e2) (>>7239)";
				
		//call
		String resmes = ShortRefParser.nntpchanLinks(message, group);
		//System.out.println(resmes);
		assertEquals(resmes, "<0eb8c1490413069@web.oniichan.onion> blablabla<0eb8c1490413069@web.oniichan.onion>\n"
				+"blablabla <0eb8c1490413069@web.oniichan.onion> <0eb8c1490413069@web.oniichan.onion>\n"
				+"blablabla <0eb8c1490413069@web.oniichan.onion> <0eb8c1490413069@web.oniichan.onion>\n"
				+"blablabla(>>7239a9807e56c0b4e2) (>>7239)");
//		
		
		
	}

	@Test
    public void addToGlobalNntpchanLinksTest() throws StorageBackendException{
		
		/*String message ="<0eb8c1490413069@web.oniichan.onion> blablabla<0eb8c1490413069@web.oniichan.onion>\n"
				+"blablabla <0eb8c1490413069@web.oniichan.onion> <0eb8c1490413069@web.oniichan.onion>\n"
				+"blablabla <0eb8c1490413069@web.oniichan.onion> <0eb8c1490413069@web.oniichan.onion>\n"
				+"blablabla(>>7239a9807e56c0b4e2) (>>7239)";
				
		//call
		String resmes = ShortRefParser.addToGlobalNntpchanLinks(message);
		System.out.println(resmes);
		assertEquals(resmes, "<0eb8c1490413069@web.oniichan.onion>(>>7239a9807e56c0b4e2) blablabla<0eb8c1490413069@web.oniichan.onion>(>>7239a9807e56c0b4e2)\n"
				+"blablabla <0eb8c1490413069@web.oniichan.onion>(>>7239a9807e56c0b4e2) <0eb8c1490413069@web.oniichan.onion>(>>7239a9807e56c0b4e2)\n"
				+"blablabla <0eb8c1490413069@web.oniichan.onion>(>>7239a9807e56c0b4e2) <0eb8c1490413069@web.oniichan.onion>(>>7239a9807e56c0b4e2)\n"
				+"blablabla(>>7239a9807e56c0b4e2) (>>7239)");
		
		*/
		String message ="<0eb8c1490413069@web.oniichan.onion>";
				
		//call
		String resmes = ShortRefParser.addToGlobalNntpchanLinks(message);
		assertEquals(resmes, "<0eb8c1490413069@web.oniichan.onion>\n(>>7239a9807e56c0b4e2)");
//		
		
		
	}

}
