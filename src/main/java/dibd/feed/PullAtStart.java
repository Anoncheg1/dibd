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
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

import dibd.config.Config;
import dibd.daemon.command.IhaveCommand;
import dibd.storage.StorageBackendException;
import dibd.storage.StorageManager;
import dibd.storage.GroupsProvider.Group;
import dibd.storage.SubscriptionsProvider.Subscription;
import dibd.util.Log;

/**
 * The PullFeeder class pull articles from given peers.
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
    	Map<Group, Long> groupsTime = new HashMap<Group, Long>();//groups with last post time
    	 
    	try {
    		for (Group g : groups){// we save post_time at the beginning to protect it from changing.
    			assert (g != null);
    			if(!g.isDeleted()){
    				long t = StorageManager.current().getLastPostOfGroup(g);
    				groupsTime.put(g, t);
    			}
        	}
    		
    		PullDaemon.pull(groupsTime, this.proxy, host, port, 21, 60*1000);

    	}catch (StorageBackendException e) {
    		Log.get().log(Level.WARNING, e.getLocalizedMessage(), e);
    	}
    }
}