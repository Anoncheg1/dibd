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

import java.net.Proxy;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import dibd.storage.StorageBackendException;
import dibd.storage.StorageManager;
import dibd.config.Config;
import dibd.storage.GroupsProvider.Group;
import dibd.storage.SubscriptionsProvider.Subscription;
import dibd.util.Log;

/**
 * The PullFeeder thread for one subscription.
 *
 * @author Vitalij
 * @since dibd/0.0.1
 */
public class PullAtStart extends Thread {

    private final Subscription sub;
    //TODO: make proxy configurable for every peer
    private final Proxy proxy;

    public PullAtStart(Subscription sub) throws NumberFormatException, UnknownHostException{
    	super();
    	super.setName(getClass().getSimpleName());
    	this.sub = sub;
    	proxy = FeedManager.getProxy(sub);
    }
    
    
    @Override
    public void run() {
    	String host = sub.getHost();
    	int port = sub.getPort();
    	Set<Group> groups = StorageManager.groups.groupsPerPeer(sub);
    	if (groups == null)
    		return;
    	Map<Group, Long> groupsTime = new HashMap<Group, Long>();//groups with last post time (not ordered)
    	 
    	try {
    		for (Group g : groups){// we save post_time at the beginning to protect it from changing.
    			assert (g != null);
    			if(!g.isDeleted()){
    				long t = StorageManager.current().getLastPostOfGroup(g);
    				groupsTime.put(g, t);
    			}
        	}
    		
    		PullDaemon.pull(groupsTime, this.proxy, host, port, Config.inst().get(Config.PULLDAYS, 1), 21, 60*1000);

    	}catch (StorageBackendException e) {
    		Log.get().log(Level.WARNING, e.getLocalizedMessage(), e);
    	}
    }
}