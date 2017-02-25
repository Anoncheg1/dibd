/*
 *   SONEWS News Server
 *   see AUTHORS for the list of contributors
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

package dibd.test.unit.daemon;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import junit.framework.TestCase;

import dibd.daemon.CommandSelector;
import dibd.daemon.NNTPConnection;
import dibd.daemon.command.ArticleCommand;
import dibd.daemon.command.CapabilitiesCommand;
import dibd.daemon.command.Command;
import dibd.daemon.command.GroupCommand;
import dibd.daemon.command.UnsupportedCommand;
import dibd.util.Log;

/**
 * Unit test for class NNTPConnection.
 * 
 * @author Christian Lins
 * @since sonews/0.5.0
 */

public class NNTPConnectionTest extends TestCase {

	public void testLineReceived() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
		NNTPConnection conn = null;

		SocketChannel socket = null;
		try {
			try {
				conn = new NNTPConnection(null);
				fail("Should have raised an IllegalArgumentException");
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		} catch (IllegalArgumentException ex) {
		}

		try {
			socket = SocketChannel.open();
			conn = new NNTPConnection(socket);
		} catch (IOException ex) {
			ex.printStackTrace();
		}

		assertNotNull(conn);

		// Make interesting methods accessible
		
		@SuppressWarnings("unchecked")
		Class<NNTPConnection> clazz = (Class<NNTPConnection>) conn.getClass();
		
		Method methTryReadLock = clazz.getDeclaredMethod("tryReadLock", new Class<?>[]{});
		methTryReadLock.setAccessible(true);

		Method methLineReceived = clazz.getDeclaredMethod("lineReceived", new byte[0].getClass());
		methLineReceived.setAccessible(true);

		try {
			// conn.lineReceived(null);
			methLineReceived.invoke(conn, new Object[]{});
			fail("Should have raised an IllegalArgumentException");
		} catch (IllegalArgumentException ex) {
		}

		try {
			// conn.lineReceived(new byte[0]);
			methLineReceived.invoke(conn, new byte[0]);
			fail("Should have raised IllegalStateException");
		} catch (InvocationTargetException ex) {
		}

		boolean tryReadLock = (Boolean) methTryReadLock.invoke(conn, new Object[]{});
		assertTrue(tryReadLock);

		Log.get().setLevel(java.util.logging.Level.WARNING);
		// conn.lineReceived("MODE READER".getBytes());
/*
		methLineReceived.invoke(conn, "MODE READER".getBytes());
		System.out.println("HAHAHA");
		// conn.lineReceived("sdkfsdjnfksjfdng ksdf gksjdfngk nskfng ksndfg
		// ".getBytes());
		methLineReceived.invoke(conn, "sdkfsdjnfksjfdng ksdf ksndfg ".getBytes());

		// conn.lineReceived(new byte[1024]); // Too long
		methLineReceived.invoke(conn, new byte[1024]);
		
*/		
		Method mpcmdl = conn.getClass().getDeclaredMethod("parseCommandLine", String.class);
		mpcmdl.setAccessible(true);
		
		Object result = mpcmdl.invoke(conn, "");
		assertNotNull(result);
		assertTrue(result instanceof UnsupportedCommand);
		
		result = mpcmdl.invoke(conn, "aRtiCle");
		assertNotNull(result);
		assertTrue(result instanceof ArticleCommand);

		result = mpcmdl.invoke(conn, "capAbilItIEs");
		assertNotNull(result);
		assertTrue(result instanceof CapabilitiesCommand);
		
		result = mpcmdl.invoke(conn, "grOUp");
		assertNotNull(result);
		assertTrue(result instanceof GroupCommand);
		
	}

}