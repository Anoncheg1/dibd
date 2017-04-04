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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;

import javax.net.ssl.SSLPeerUnverifiedException;

import dibd.config.Config;
import dibd.daemon.DaemonThread;
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
	public static final int QUEUE_SIZE = 128;
	
	private static class MissingThread{
		Group g; //groupt to query to
		String messageId;
		String messageIdHostPath;
		/**
		 * @param g
		 * @param t
		 * @param host_with_path for log output
		 */
		MissingThread(Group g, String messageId, String string_for_log){
			this.g = g;
			this.messageId = messageId;
			this.messageIdHostPath = string_for_log;
		}
		
		@Override
		public int hashCode() {
			return this.messageId.hashCode();
		}
		
		@Override
		public boolean equals(Object obj) {
			 // If the object is compared with itself then return true  
	        if (obj == this)
	            return true;

	        /* Check if o is an instance of Complex or not
	          "null instanceof [type]" also returns false */
	        if (!(obj instanceof MissingThread)) {
	        	return false;
	        }else if (((MissingThread)obj).messageId.equals(this.messageId))
	        	return true;
	        else
	        	return false;
		}
	}
	
	//IHAVE, TAKETHIS Queue for waiting: put, take
	private final static LinkedBlockingQueue<MissingThread> waitingQueue = new LinkedBlockingQueue<>(QUEUE_SIZE);
	//working list: add, remove, contains
	private final static Set<String> workingSet = Collections.synchronizedSet(new HashSet<String>(5)); //amount of processes
	
	private static volatile boolean running = false;

	
	
	
	/**
	 * The problem is
	 * 1) place object to queue if 1. space available and 2. not in queue yet 3. there is not working thread with such object
	 * 2) take object from queue with waiting.
	 * 
	 * 
	 * @param group
	 * @param message_id_thread
	 * @param messageIdHostPath
	 */
	public static void queueForPull(Group group, String message_id_thread, String messageIdHostPath) {
		assert(group != null);
		if (running){
			try {
				MissingThread mt; 
				//synchronized(workingSet){ //we must guarantee during checking waitingQueue workingQueue will not be changed.
				synchronized(workingSet){
					if(workingSet.contains(message_id_thread)){
						Log.get().finer(message_id_thread+" already in requested as missing thread");
						return;
					}else{
						mt = new MissingThread(group, message_id_thread, messageIdHostPath);
						if (waitingQueue.contains(mt)){
							Log.get().finer(message_id_thread+" already in waiting queue as missing thread");
							return;
						}
					}	
				}
				
				//If queue is full, this call blocks until the queue has free space;
				// This is probably a bottleneck for article posting
				waitingQueue.put(mt);
				
				
			} catch (InterruptedException ex) {
				Log.get().log(Level.WARNING, null, ex);
			}
		}
	}
	
	
	
	/**
	 * @param mthread
	 * @param proxy
	 * @param host
	 * @param port
	 * @param retries
	 * @param sleep
	 * @return true if we success false if was error
	 * @throws StorageBackendException
	 */
	private boolean pull(MissingThread mthread, Proxy proxy, String host, int port, int retries, int sleep) throws StorageBackendException {
		String gname = mthread.g.getName();
    	for(int retry =1; retry<retries;retry++){
    		ArticlePuller ap = null;
    		try {
    			boolean TLSenabled = Config.inst().get(Config.TLSENABLED, false);
    			//Connecting
    			try {
					ap = new ArticlePuller(proxy, host, port, TLSenabled);
    			} catch (IOException ex) { //second try
    				Log.get().warning(ex.toString()+" to host "+host);
					break;
				}
    			Log.get().log(
    					Level.INFO, "Pull missing thread  {0} group {1} from {2}", //his groups {1}",
    					new Object[]{mthread.messageIdHostPath, gname, host});

    			
    			return ap.getThread(mthread.messageId, gname);

    		}catch (IOException ex) {
    			Log.get().log(Level.INFO,"{0}: try {1} for host:{2}:{3} {4}",
    					new Object[]{Thread.currentThread().getName(), retry, host, port, ex.toString()});
    		}catch (NullPointerException e) {
    			Log.get().log(Level.WARNING,"No error if it is shutdown {0}", e);
    			return false;
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
    	return false;
    }
	
    

    
    /////////    Getting missing thread    /////////
    @Override
    public void run() {
    	PullDaemon.running = isRunning(); 
    	while (isRunning()) {
    		MissingThread mthr = null;
    		try{
    			mthr = PullDaemon.waitingQueue.take();
    			if(! PullDaemon.workingSet.add(mthr.messageId))
    					return;//if already have

    			Thread.sleep(20000);//20sec wait for missing thread be spread to peer network we connected

    			List<Subscription> subs = new ArrayList<>(); 
    			for (Subscription sub : StorageManager.peers.getAll())
    				if(mthr.g.getHosts().contains(sub.getHost()) //case sensitive group and peer 
    						&& sub.getFeedtype() != FeedType.PUSH)
    					subs.add(sub);

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
    					//if was error we will try another sub
    					if(pull(mthr, proxy, sub.getHost(), sub.getPort(), 5, 30*1000)) //0 add days, 5 retries, 30 sec. 
    						break;
							
					} catch (StorageBackendException e) {
						Log.get().log(Level.WARNING, e.getLocalizedMessage(), e);
					}
    			}
    			
    		} catch (InterruptedException e1) {
    			Log.get().log(Level.FINEST, "PullDaemon interrupted: {0}", e1.getLocalizedMessage());
    			return;
			}catch (Exception e) {
	    		Log.get().log(Level.SEVERE, e.getLocalizedMessage(), e);
	    	}finally{
	    		if (mthr != null) //if interupted
	    			PullDaemon.workingSet.remove(mthr.messageId);//synchronized
    			
	    	}
    	}
    }
}