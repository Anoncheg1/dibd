/**
 * 
 */
package dibd.test;

import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import org.mockito.Mockito;
import dibd.storage.GroupsProvider;
import dibd.storage.StorageBackendException;
import dibd.storage.StorageManager;
import dibd.storage.StorageNNTP;
import dibd.storage.SubscriptionsProvider;
import dibd.storage.GroupsProvider.Group;
import dibd.storage.article.Article;
import dibd.storage.impl.JDBCDatabase;
import dibd.storage.web.StorageWeb;

/**
 * @author user
 *
 */
public class JDBCDatabaseTest {

	/**
	 * @param args
	 * @throws SQLException 
	 * @throws ParseException 
	 * @throws StorageBackendException 
	 * @throws SecurityException 
	 * @throws NoSuchMethodException 
	 * @throws InvocationTargetException 
	 * @throws IllegalArgumentException 
	 * @throws IllegalAccessException 
	 * @throws InstantiationException 
	 */
	public static void main(String[] args) throws SQLException, ParseException, StorageBackendException, NoSuchMethodException, SecurityException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		
		SubscriptionsProvider sp = Mockito.mock(SubscriptionsProvider.class);  
		StorageManager.enableSubscriptionsProvider(sp);
		when(sp.has("hschan.ano")).thenReturn(true);
		
		//mocking Group
		GroupsProvider gp = Mockito.mock(GroupsProvider.class);  
		StorageManager.enableGroupsProvider(gp);
		
		Class<?> cg = Group.class;
		Constructor<?> groupC = cg.getDeclaredConstructor(new Class[]{GroupsProvider.class, String.class, Integer.TYPE, Integer.TYPE, Set.class});
		groupC.setAccessible(true);
		
		
		Set<String> host = new HashSet<String>(Arrays.asList("hschan.ano","host.com"));
		//name id flags hosts
		when(StorageManager.groups.get("local.test")).thenReturn(
				(Group) groupC.newInstance(StorageManager.groups,"local.test",23,0,host));
		
		
		JDBCDatabase db0 = new JDBCDatabase();
		db0.arise();
		StorageNNTP db = db0;
		Article art = new Article(null, "23458@host.com", "host.com", "петрик <foo@bar.ano>", "ффывфыв фыв фыв фыв фыв фыв фыв фыв фыв фыв фыв фы", "message2",
				"Thu, 02 May 2013 12:37:44 +0000", null, "local.test", 23);
		//db0.createThread(art, Base64.getDecoder().decode("R0lGODlhAQABAIAAAP///wAAACH5BAAAAAAALAAAAAABAAEAAAICRAEAOw=="), "image/gif");
		Article art2 = db0.getArticle("<23458@host.com>", null);
		System.out.println(null+"!");
	}

}
