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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import dibd.storage.StorageBackendException;
import dibd.storage.StorageManager;
import dibd.config.Config;
import dibd.storage.GroupsProvider.Group;
import dibd.storage.SubscriptionsProvider.FeedType;
import dibd.storage.SubscriptionsProvider.Subscription;
import dibd.util.Log;

/**
 * The PullFeeder thread for one subscription.
 *
 * @author Vitalij
 * @since dibd/0.0.1
 */
public class PullAtStart extends Thread {

    
    //TODO: make proxy configurable for every peer
    //private final Proxy proxy;

    public PullAtStart(){
    	super();
    	super.setName(getClass().getSimpleName());
    	
    	;
    }
    
    /**
     * Share function to pull at start.
     * 
     * @param groupsTime group and last post_time
     * @param host
     * @param port
     * @param retries
     * @param sleep starting value of sleep between retries
     * @throws StorageBackendException
     * @return -1 if error or articles pulled
     */
    private int pullLoop(Set<Group> groups, String host, int port, Proxy proxy, int retries, int sleep) throws StorageBackendException {
    	for(int retry =1; retry<retries;retry++){
    		ArticlePuller ap = null;

    		boolean TLSenabled = Config.inst().get(Config.TLSENABLED, false);
    		//Connecting
    		try{
    			try{
    				ap = new ArticlePuller(proxy, host, port, TLSenabled);
    			} catch (IOException ex) { //second try
    				Log.get().warning(ex.toString()+" to host "+host);
    				break;
    			}
    			Log.get().log(
    					Level.INFO, "{0}: pulling from {1} groups:{2}", //his groups {1}",
    					new Object[]{Thread.currentThread().getName(), host, groups.size()});
    			//new Object[]{host, "["+groupsTime.keySet().stream().map(g -> g.getName()).collect(Collectors.joining(","))+"]"});

    			//Scrap message-ids
    			int ret = 0;
    			List<String> ca = ap.getCapabilities();
    			for (Group group : groups){
    				Map<String, List<String>> mIDs = ap.scrap(group, ca);
    				if (mIDs.isEmpty()){
    					Log.get().log(Level.FINE,"{0}: no new articles in {1} at host:{2}:{3}",
    							new Object[]{Thread.currentThread().getName(), group.getName(), host, port});
    				}else{
    					ret += ap.toItself(mIDs.entrySet().iterator(), 0); //return received number
    				}
    			}

    			return ret;

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
    
    
    @Override
    public void run() {
    	while(true){
    		for (Subscription sub : StorageManager.peers.getAll()) {
    			if (sub.getFeedtype() != FeedType.PUSH){
    				String host = sub.getHost();
    				int port = sub.getPort();
    				Proxy proxy;
    				try {
    					proxy = FeedManager.getProxy(sub);
    				} catch (NumberFormatException | UnknownHostException e3) {
    					Log.get().log(Level.SEVERE, "Wrong proxy configuration: {0}", e3);
    					continue;
    				}

    				Set<Group> set = StorageManager.groups.groupsPerPeer(sub);
    				if (set== null || set.isEmpty())
    					return;

    				Set<Group> groups = new HashSet<Group>(set); //modifiable

    				try {

    					for (Iterator<Group> iterator = groups.iterator(); iterator.hasNext();) {
    						Group g = iterator.next();
    						assert(g != null);
    						if(g.isDeleted())
    							iterator.remove();
    					}


    					int res = pullLoop(groups, host, port, proxy, 21, 60*1000);
    					Log.get().log(Level.INFO, "Pull {0} from {1} sucessfully completed, {2} articles reseived.",
    							new Object[]{
    									groups.stream().map(e -> e.getName()).reduce( (e1, e2) -> e1+", "+e2).get(), host, res});
    				}catch (StorageBackendException e) {
    					Log.get().log(Level.WARNING, e.getLocalizedMessage(), e);
    				}catch (Exception e) {
    					Log.get().log(Level.SEVERE, e.getLocalizedMessage(), e);
    				}
    				try {
    					//TODO:make configurable
    					Thread.sleep(1000*60*60*Config.inst().get(Config.PULLINTERVAL, 5));//xover every 5 hours. nntpchan do not relay at push so 1 will be better.
    				} catch (InterruptedException e) {
    					Log.get().log(Level.FINEST, "PullAtStart "+sub.getHost()+" interrupted: {0}", e.getLocalizedMessage());
    				}
    			}
    		}
    	}
    }
}