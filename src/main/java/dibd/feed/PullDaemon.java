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

package dibd.feed;

import java.io.IOException;
import java.net.Proxy;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;

import javax.net.ssl.SSLPeerUnverifiedException;

import dibd.config.Config;
import dibd.daemon.DaemonThread;
import dibd.daemon.command.IhaveCommand;
import dibd.storage.StorageBackendException;
import dibd.storage.StorageManager;
import dibd.storage.GroupsProvider.Group;
import dibd.storage.SubscriptionsProvider.FeedType;
import dibd.storage.SubscriptionsProvider.Subscription;
import dibd.util.Log;

/**
 * The PullFeeder class pull articles from given peers.
 *
 * @author Christian Lins
 * @since sonews/0.5.0
 */
public class PullDaemon extends DaemonThread {

	// TODO Make configurable
	public static final int QUEUE_SIZE = 256;
	
	private static class MissingThread{
		Group g; //groupt to query to
		String messageId;
		String string_for_log;
		/**
		 * @param g
		 * @param t
		 * @param host_with_path for log output
		 */
		MissingThread(Group g, String messageId, String string_for_log){
			this.g = g;
			this.messageId = messageId;
			this.string_for_log = string_for_log;
		}
	}
	
	//IHAVE, TAKETHIS when no reference
	private final static LinkedBlockingQueue<MissingThread> groupQueue = new LinkedBlockingQueue<>(QUEUE_SIZE);
	
	private static volatile boolean running = false;

	public static void queueForPush(Group group, String message_id_thread, String string_for_log) {
		assert(group != null);
		if (running){
			try {
				//If queue is full, this call blocks until the queue has free space;
				// This is probably a bottleneck for article posting
				groupQueue.put(new MissingThread(group, message_id_thread, string_for_log));
			} catch (InterruptedException ex) {
				Log.get().log(Level.WARNING, null, ex);
			}
		}
	}
	
	
	
	private int pull(MissingThread mthread, Proxy proxy, String host, int port, int retries, int sleep) throws StorageBackendException {
		String gname = mthread.g.getName();
    	for(int retry =1; retry<retries;retry++){
    		ArticlePuller ap = null;
    		try {
    			boolean TLSenabled = Config.inst().get(Config.TLSENABLED, false);
    			//Connecting
    			try {
					ap = new ArticlePuller(proxy, host, port, TLSenabled);
				} catch (SSLPeerUnverifiedException e) {
					Log.get().log(Level.WARNING, "For host {0} TLS did not present a valid certificate",
		        			host);
					break;
				}
    			Log.get().log(
    					Level.INFO, "Pull missing thread  {0}  from {1} group {2}", //his groups {1}",
    					new Object[]{mthread.messageId, host, gname});

    			
    			ap.getThread(mthread.messageId, gname);
    			

    		}catch (IOException ex) {
    			Log.get().log(Level.INFO,"{0}: try {1} for host:{2}:{3} {4}",
    					new Object[]{Thread.currentThread().getName(), retry, host, port, ex.toString()});
    		}catch (NullPointerException e) {
    			Log.get().log(Level.WARNING,"No error if it is shutdown {0}", e);
    			return -1;
    		}finally{
    			//Log.get().log(Level.WARNING, "Finally host: {0}:{1} can not pull from.", new Object[]{host, port});
    			if (ap != null)
    				ap.close();
    		}

    		try {
    			Thread.sleep(sleep*retry*retry);//geometric progression 
    		} catch (InterruptedException e) {
    			break;
    		}
    	}
    	return -1;
    }
	
    

    
    /////////    Getting missing thread    /////////
    @Override
    public void run() {
    	PullDaemon.running = isRunning(); 
    	while (isRunning()) {
    		try{

    			MissingThread mthr = PullDaemon.groupQueue.take();
    			Thread.sleep(30000);//30sec wait for missing thread be spread to peer network we connected

    			List<Subscription> subs = new ArrayList<>(); 
    			for (Subscription sub : StorageManager.peers.getAll())
    				if(mthr.g.getHosts().contains(sub.getHost()) //case sensitive group and peer 
    						&& sub.getFeedtype() != FeedType.PUSH)
    					subs.add(sub);

    			int res = 0;
    			for (Subscription sub : subs){ //we query every peer in group
    				
    				//TODO: make proxy configurable for every peer
    				Proxy proxy;
    				try {
    					proxy = FeedManager.getProxy(sub);
    				} catch (NumberFormatException | UnknownHostException e) {
    					Log.get().log(Level.SEVERE, "Wrong proxy configuration: {0}", e);
    					return;
    				}
    				
    				try {
    					int r = pull(mthr, proxy, sub.getHost(), sub.getPort(), 5, 30*1000); //0 add days, 5 retries, 30 sec. 
    					if (r != -1)
    						res += r;
							
					} catch (StorageBackendException e) {
						Log.get().log(Level.WARNING, e.getLocalizedMessage(), e);
					}
    			}
    			
    			if(res == 0)
    				Log.get().log(Level.WARNING, "No thread was found for missing thread: {0} at {1} peers", new Object[] {mthr.string_for_log, subs.size()});
    		
    		} catch (InterruptedException e1) {
    			Log.get().log(Level.FINEST, "PullDaemon interrupted: {0}", e1.getLocalizedMessage());
    			return;
			}catch (Exception e) {
	    		Log.get().log(Level.SEVERE, e.getLocalizedMessage(), e);
	    	}
    	}
    }
}