package dibd.test.unit.feed;

import static org.mockito.Mockito.when;
import static org.junit.Assert.*;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Stream;

import javax.net.ssl.SSLPeerUnverifiedException;

import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

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


@Ignore
public class ArticlePullerTest {
	
	private Method oldFashionScrap;
	private Method getIS;
	private Constructor<?> groupC;
	private Field out;
	
	public ArticlePullerTest() throws NoSuchMethodException, SecurityException, NoSuchFieldException{
		Class<?> ap = ArticlePuller.class;
		
		/*for (Method aa: ap.getDeclaredMethods()){
			System.out.println(aa);
		}*/
		
		oldFashionScrap = ap.getDeclaredMethod("oldFashionScrap", new Class[]{Group.class});
		oldFashionScrap.setAccessible(true);
		
		Class<?> cg = Group.class;
		groupC = cg.getDeclaredConstructor(new Class[]{String.class, Integer.TYPE, Integer.TYPE, Set.class});
		groupC.setAccessible(true);
		
		out = ap.getDeclaredField("out");
		out.setAccessible(true);
		
		getIS = ap.getDeclaredMethod("getIS");
		getIS.setAccessible(true);
		/*for ( Method aa: ap.getDeclaredMethods())
			System.out.println(aa);*/
		
	}
	
	String ggg(){
		return"34";
	}
	
	int i = 0;
	@Test
	public void oldFashionScrapTest() throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, InstantiationException, FileNotFoundException{
		
		ArticlePuller ap = mock(ArticlePuller.class);
		out.set(ap, mock(PrintWriter.class));
		Set<String> host = new HashSet<String>(Arrays.asList("hschan.ano","host.com"));
		Group g =  (Group) groupC.newInstance("local.test",23,0,host);
		List<String> resp = new ArrayList<>();
		resp.add("211");
		resp.add("224");
		File xfile = new File("src/test/java/dibd/test/unit/feed/xover");
		if(xfile.exists()){
			
			try (Stream<String> stream = Files.lines(xfile.toPath())) {

				stream.forEach(e -> resp.add(e));

			} catch (IOException e) {
				e.printStackTrace();
			}
			
			//when(getIS.invoke(ap)).thenReturn("211");
			when(getIS.invoke(ap)).thenAnswer(new Answer<String>() {
				public String answer(InvocationOnMock invocation) throws Throwable {
					return resp.get(i++);
				}});

			Map<String, List<String>> res = (Map<String, List<String>>) oldFashionScrap.invoke(ap, g);
			System.out.println(res.get("<a8ed01491388629@web.oniichan.onion>"));
		}
	}


}
