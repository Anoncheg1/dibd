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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.net.Proxy.Type;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.stream.Collectors;

import dibd.config.Config;
import dibd.daemon.DaemonThread;
import dibd.daemon.command.IhaveCommand;
import dibd.storage.StorageBackendException;
import dibd.storage.StorageManager;
import dibd.storage.GroupsProvider.Group;
import dibd.storage.SubscriptionsProvider.FeedType;
import dibd.storage.SubscriptionsProvider.Subscription;
import dibd.storage.article.Article;
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
	
	private static class Pair{
		Group g;
		long t;
		Pair(Group g, long t){
			this.g = g;
			this.t = t;
		}
	}
	
	//IHAVE, TAKETHIS when no reference
	private final static LinkedBlockingQueue<Pair> groupQueue = new LinkedBlockingQueue<>(QUEUE_SIZE);
	
	private static volatile boolean running = false;

	public static void queueForPush(Group group, long post_time) {
		assert(group != null);
		if (running){
			try {
				//If queue is full, this call blocks until the queue has free space;
				// This is probably a bottleneck for article posting
				groupQueue.put(new Pair(group, post_time));
			} catch (InterruptedException ex) {
				Log.get().log(Level.WARNING, null, ex);
			}
		}
	}
	
    /**
     * Pull groups at start or pull one thread.
     * 
     * @param groupsTime group and last post_time
     * @param proxy
     * @param host
     * @param port
     * @param retries
     * @param sleep
     * @throws StorageBackendException
     */
    static void pull(Map<Group, Long> groupsTime, Proxy proxy, String host, int port, int retries, int sleep) throws StorageBackendException {
    	for(int retry =1; retry<retries;retry++){
    		ArticlePuller ap = null;
    		try {
    			boolean TLSenabled = Config.inst().get(Config.TLSENABLED, false);
    			ap = new ArticlePuller(FeedManager.createSocket(proxy, host, port), TLSenabled, host); //connection to subscription
    			Log.get().log(
    					Level.INFO, "{0}: pulling from {1} groups:{2}", //his groups {1}",
    					new Object[]{Thread.currentThread().getName(), host, groupsTime.size()});
    					//new Object[]{host, "["+groupsTime.keySet().stream().map(g -> g.getName()).collect(Collectors.joining(","))+"]"});

    			List<String> mIDs = ap.check(groupsTime, Config.inst().get(Config.PULLDAYS, 1));
    			if (mIDs.isEmpty()){
    				Log.get().log(Level.FINE,"{0}: no new articles found at host:{1}:{2}",
    						new Object[]{Thread.currentThread().getName(), host, port});
    				return;
    			}else{
    				int reseived = 0;
    				for (String mId : mIDs){
    					if (ap.transferToItself(new IhaveCommand(), mId))//IhaveCommand can't be reused.
    						reseived ++;
    				}
    				Log.get().log(Level.FINE,"{0}: {1} articles of {2} successful reseived from host {3}:{4}",
    						new Object[]{Thread.currentThread().getName(), reseived, mIDs.size(), host, port});
    					return;
    			}

    		}catch (IOException ex) {
    			Log.get().log(Level.INFO,"{0}: try {1} for host:{2}:{3} {4}",
    					new Object[]{Thread.currentThread().getName(), retry, host, port, ex.toString()});
    		}catch (NullPointerException e) {
    			Log.get().log(Level.WARNING,"No error if it is shutdown {0}", e);
    			return;
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
    }

    
    @Override
    public void run() {
    	PullDaemon.running = isRunning(); 
    	while (isRunning()) {
    		try{	
    			Pair p = PullDaemon.groupQueue.take();
    			List<Subscription> subs = new ArrayList<>(); 
    			for (Subscription sub : StorageManager.peers.getAll())
    				if(p.g.getHosts().contains(sub.getHost()) //case sensitive group and peer 
    						&& sub.getFeedtype() != FeedType.PUSH)
    					subs.add(sub);

    			for (Subscription sub : subs){
    				//TODO: make proxy configurable for every peer
    				Proxy proxy;
    				try {
    					proxy = FeedManager.getProxy(sub);
    				} catch (NumberFormatException | UnknownHostException e) {
    					Log.get().log(Level.SEVERE, "Wrong proxy configuration: {0}", e);
    					return;
    				}
    				Map<Group, Long> gr = new HashMap<>(); //one entry map
    				gr.put(p.g, p.t);
    				
    				try {
						pull(gr, proxy, sub.getHost(), sub.getPort(), 5, 60*1000);
					} catch (StorageBackendException e) {
						Log.get().log(Level.WARNING, e.getLocalizedMessage(), e);
					}
    			}
    		
    		} catch (InterruptedException e1) {
    			Log.get().log(Level.WARNING, "PullFeeder interrupted: {0}", e1);
    			return;
			}
    	}
    }
}