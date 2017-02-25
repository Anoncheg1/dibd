package dibd.test.unit.command;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.text.ParseException;

import org.junit.Test;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

import dibd.config.Config;
import dibd.daemon.NNTPInterface;
import dibd.daemon.command.ArticleCommand;
import dibd.storage.NNTPCacheProvider;
import dibd.storage.StorageBackendException;
import dibd.storage.StorageManager;
import dibd.storage.StorageNNTP;
import dibd.storage.article.Article;

public class ArticleCommandTest {
	
	private StorageNNTP storage; //mock
	private NNTPInterface conn; //mock
	
	public ArticleCommandTest(){
		storage = Mockito.mock(StorageNNTP.class);
		StorageManager.enableProvider(new dibd.test.unit.storage.TestingStorageProvider(storage));
		conn = Mockito.mock(NNTPInterface.class);
		when(conn.getCurrentCharset()).thenReturn(Charset.forName("UTF-8"));
		
		StorageManager.enableNNTPCacheProvider(Mockito.mock(NNTPCacheProvider.class));
		
		Config.inst().set(Config.HOSTNAME, "foo.bar"); //article is local no need to use nntp cache
	}

	@Test
	public void articleTest() throws UnsupportedEncodingException, IOException, StorageBackendException, ParseException{
       
		ArticleCommand a = new ArticleCommand();
		a.processLine(conn, "article", "article".getBytes("UTF-8"));
		verify(conn, atLeastOnce()).println(startsWith("420")); //Current article is invalid
		
		Article art0 = Mockito.mock(Article.class);//mock article
		when(storage.getArticle("<messageid@foo.bar>", null)).thenReturn(art0);
		when(art0.getMsgID_host()).thenReturn("foo.bar");
		
		a.processLine(conn, "article <messageid@foo.bar>", "article <messageid@foo.bar>".getBytes("UTF-8"));
		verify(conn, atLeastOnce()).println(startsWith("220")); 
		verify(conn, atLeastOnce()).println(".");
		
		a.processLine(conn, "body <messageid@foo.bar>", "body <messageid@foo.bar>".getBytes("UTF-8"));
		verify(conn, atLeastOnce()).println(startsWith("222")); 
		verify(conn, atLeastOnce()).println(".");
		
		a.processLine(conn, "head <messageid@foo.bar>", "head <messageid@foo.bar>".getBytes("UTF-8"));
		verify(conn, atLeastOnce()).println(startsWith("221")); 
		verify(conn, atLeastOnce()).println(".");
		
		when(storage.getArticle(null, 2)).thenReturn(art0);
		a.processLine(conn, "article 2", "article 2".getBytes("UTF-8"));
		verify(conn, atLeastOnce()).println(startsWith("220")); 
		verify(conn, atLeastOnce()).println(".");
		

	}

}
