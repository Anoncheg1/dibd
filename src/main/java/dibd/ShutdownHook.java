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
package dibd;

import java.util.Set;
import java.util.logging.Level;

import dibd.daemon.DaemonThread;
import dibd.util.Log;

/**
 * Will force all other threads to shutdown cleanly.
 * 
 * @author Christian Lins
 * @since sonews/0.5.0
 */
class ShutdownHook implements Runnable {
	public ShutdownHook(){};//default constructor for testing
	
	
    /**
     * Called when the JVM exits.
     * Two steps shutdown
     */
    @Override
    public void run() {
    	Log.get().log(Level.INFO, "Clean shutdown of daemon threads initiated");
    	
    	Set<Thread> threads = Thread
    			.getAllStackTraces().keySet();
    	try {
    		for (Thread thread : threads) { //1)
    			/*if((thread.getName().equals("PushDaemon") || thread.getName().equals("PullDaemon"))
    					&& thread.isAlive()){
    				((DaemonThread) thread).requestShutdown();*/
    			if ((thread.getName().equals("PullFeeder") || thread.getName().equals("PushDaemon") || thread.getName().equals("PullDaemon"))
    					&& thread.isAlive()){
    				thread.interrupt();
    			}
    		}
    		for (Thread thread : threads) { //2)
    			if((thread.getName().equals("ChannelReader") || thread.getName().equals("ConnectionWorker"))
    					&& thread.isAlive()){
    				((DaemonThread) thread).requestShutdown();
    			}
    		}
    		Thread.sleep(1000);
    		threads = Thread
    				.getAllStackTraces().keySet();
    		for (Thread thread : threads) {  //3) ChannelWriter NNTPDaemon  Connections 
    			if (thread instanceof DaemonThread && thread.isAlive()) {

    				System.err.println("sonews: Waiting for " + thread.getName()
    				+ " to exit...");
    				((DaemonThread) thread).requestShutdown();
    				((DaemonThread) thread).join(500);

    			}
    		}
    	} catch (InterruptedException ex) {
    		System.err.println(ex.getLocalizedMessage());
    	}
    	
        // We have notified all not-sleeping AbstractDaemons of the shutdown;
        // all other threads can be simply purged on VM shutdown

        System.err.println("sonews: Clean shutdown."); //we use "standard" error output stream because Log and STDOUT is terminated already  
    }
}
