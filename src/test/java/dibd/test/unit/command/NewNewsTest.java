package dibd.test.unit.command;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import dibd.daemon.NNTPInterface;
import dibd.daemon.command.NewNewsCommand;
import dibd.storage.GroupsProvider;
import dibd.storage.StorageBackendException;
import dibd.storage.StorageManager;
import dibd.storage.SubscriptionsProvider;

public class NewNewsTest {
	
	private NNTPInterface conn; //mock

	public NewNewsTest() {
		conn = Mockito.mock(NNTPInterface.class);
		when(conn.getCurrentCharset()).thenReturn(Charset.forName("UTF-8"));
		
		//group provider
		GroupsProvider gp = Mockito.mock(GroupsProvider.class);  
		StorageManager.enableGroupsProvider(gp);
		when(gp.get(Mockito.anyString())).thenReturn(null);

	}

	@Test
	public void NewNewsCommandTest() throws UnsupportedEncodingException, IOException, StorageBackendException, ParseException, NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, InstantiationException{
		Class<?> nnc = NewNewsCommand.class;
		NewNewsCommand co = (NewNewsCommand) nnc.newInstance();
		Method parseDate = nnc.getDeclaredMethod("parseDate", new Class[]{String.class, String.class});
		parseDate.setAccessible(true);
		//new
		long l = (long) parseDate.invoke(co, "1480592049", null);
		assertTrue(l == 1480592049);
		
		l = (long) parseDate.invoke(co, "1", null);
		assertTrue(l == 1);
		
		co.processLine(conn, "NEWNEWS local.test 0", "NEWNEWS local.test 0".getBytes("UTF-8"));
		verify(conn,times(1)).println(Mockito.startsWith("230"));
		verify(conn,times(1)).println(".");

		//old ones:
		//yyyymmdd hhmmss
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd HHmmss" );
		df.setTimeZone(TimeZone.getTimeZone("UTC"));
		l = (long) parseDate.invoke(co, "20130203", "120113");
		assertTrue(l == df.parse("20130203 120113").getTime()/1000);
		
		co.processLine(conn, "NEWNEWS gr 20130203 120113", "NEWNEWS gr 20130203 120113".getBytes("UTF-8"));
		verify(conn,times(2)).println(Mockito.startsWith("230"));
		verify(conn,times(2)).println(".");
		
		//mmdd hhmmss
		df = new SimpleDateFormat("MMdd HHmmss" );
		df.setTimeZone(TimeZone.getTimeZone("UTC"));
		
		SimpleDateFormat p = new SimpleDateFormat("yyyy");
		p.setTimeZone(TimeZone.getTimeZone("UTC"));
		
		long parse = df.parse("0203 120113").getTime()/1000;
		long year = p.parse(String.valueOf(Calendar.getInstance().get(Calendar.YEAR))).getTime()/1000;
		l = (long) parseDate.invoke(co, "0203", "120113");
		assertTrue(l == parse+year);
		
		co.processLine(conn, "NEWNEWS gr 0203 120113", "NEWNEWS gr 0203 120113".getBytes("UTF-8"));
		verify(conn,times(3)).println(Mockito.startsWith("230"));
		verify(conn,times(3)).println(".");
		
		//yymmdd hhmmss
		df = new SimpleDateFormat("yyMMdd HHmmss" );
		df.set2DigitYearStart(new Date(946684800)); //2000
		df.setTimeZone(TimeZone.getTimeZone("UTC"));
		l = (long) parseDate.invoke(co, "110203", "120113");
		assertTrue(l == df.parse("110203 120113").getTime()/1000);
		
		co.processLine(conn, "NEWNEWS gr 110203 120113", "NEWNEWS gr 110203 120113".getBytes("UTF-8"));
		verify(conn,times(4)).println(Mockito.startsWith("230"));
		verify(conn,times(4)).println(".");
		
		
		//Hook conn.println(String)
				/*Mockito.doAnswer(new Answer<Object>() {
					public Object answer(InvocationOnMock invocation) {
						Object[] args = invocation.getArguments();
						System.out.println(args[0]);
						Mockito.matches(null);
						return null;
					}})
				.when(conn).println(Mockito.anyString());*/
				

	}

}
