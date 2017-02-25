/**
 * 
 */
package dibd.test.unit.storage;

import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

import org.junit.Test;
import org.mockito.Mockito;

import dibd.storage.StorageBackendException;
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
public class WebShortRefsTest {
	
	StorageWeb db;//mock
	
	public WebShortRefsTest() {
		super();
		
		db = Mockito.mock(StorageWeb.class);
		

	}

	@Test
    public void getGlobalRefsTest() throws StorageBackendException{
		
		Article art = new Article(17, 16, "<aaa@hos.com>", "host.com", null, "сабджект", "фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы\nфывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы\nфывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы\nфывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы\nфывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы\nфывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы", 
				1466589502, "host!host2", "group", null, null); //without image
		Mockito.when(db.getArticle("<aaa@hos.com>", null)).thenReturn(art);
		
		Map<String, WebRef> a = ShortRefParser.getGlobalRefs(db, "<aaa@hos.com>");
		
		WebRef wr = a.get("<aaa@hos.com>");
		assertTrue(wr != null);
		assertTrue(wr.getThread_id() == 17);
		assertTrue(wr.getReplay_id() == 16);
	}

	@Test
    public void getShortRefsTest() throws StorageBackendException{
		
		Article art = new Article(16*16*16, 2, "<aaa@hos.com>", "host.com", null, "сабджект", "фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы\nфывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы\nфывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы\nфывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы\nфывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы\nфывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы", 
				1466589502, "host!host2", "group", null, null); //without image
		Mockito.when(db.getArticle(null, 16*16*16)).thenReturn(art);
		
		String a = ShortRefParser.getShortRefs(db, ">>1000jh>>1000");
		
		assertTrue(a.equals("<aaa@hos.com>jh<aaa@hos.com>"));
	}


}
