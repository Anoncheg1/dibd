/*
 *   SONEWS News Server
 *   Copyright (C) 2009-2015  Christian Lins <christian@lins.me>
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package dibd.test.unit;


import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;
import static org.mockito.Mockito.*;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.junit.Assert.*;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import dibd.daemon.ChannelLineBuffers;
import dibd.daemon.DaemonThread;
import dibd.daemon.NNTPInterface;
import dibd.daemon.NNTPConnection;
import dibd.storage.StorageManager;
import dibd.util.Log;


/**
 *
 * @author Christian Lins
 */
public class ShutdownHookTest {

	private NNTPConnection conn; //mock
	
	

	public ShutdownHookTest() throws NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
		conn = mock(NNTPConnection.class);
	}

	@BeforeClass
	public static void setUpClass() {
	}

	@AfterClass
	public static void tearDownClass() {
	}

	@Before
	public void setUp() {
	}

	@After
	public void tearDown() {
	}

	/**
	 * Test of run method, of class ShutdownHook.
	 * @throws IOException 
	 * @throws ClassNotFoundException 
	 * @throws IllegalAccessException 
	 * @throws InstantiationException 
	 * @throws InterruptedException 
	 * @throws NoSuchMethodException 
	 * @throws SecurityException 
	 * @throws InvocationTargetException 
	 * @throws IllegalArgumentException 
	 * @throws NoSuchFieldException 
	 */
	@Test
	public void testRun() throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException, InterruptedException, SecurityException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException, NoSuchFieldException {
		try {
			Log.get().setLevel(java.util.logging.Level.SEVERE);
			Class<?> sh = Class.forName("dibd.ShutdownHook");
			Constructor<?> csh = sh.getConstructor();
			csh.setAccessible(true);
			Object shutdownHook = csh.newInstance();
			
			Thread instance = new Thread((Runnable) shutdownHook);
			instance.start();
			//Thread.sleep(5000);
			instance.join();
			
			instance = new Thread((Runnable) shutdownHook);
			

			DaemonThread daemon;
			daemon = new DaemonThread(){
				@Override
				public void run() {
					try {
						this.setName("daemon");
						Thread.sleep(1);
						//System.out.println("interapted");
					} catch (InterruptedException ex) {
						System.out.println("Sleep interrupted");
					}
				}
				@Override
				public void requestShutdown() {
					super.requestShutdown();
					try {
						Thread.sleep(1);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			};

			//Hook conn.println(String)
			Mockito.doAnswer(new Answer<Object>() {
				public Object answer(InvocationOnMock invocation) {
					Object[] args = invocation.getArguments();
					System.out.println(args[0]);
					return null;
				}})
			.when(conn).println(Mockito.anyString());
			//Log.get().setLevel(java.util.logging.Level.ALL);

			

			daemon.start();
			instance.start();
			instance.join();
			assertTrue(daemon.isAlive() == false);
		} catch(InterruptedException ex) {
			fail("Interrupted while shutting down all AbstractDaemon");
		}
	}

}
