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

package dibd.storage;

import java.net.Proxy;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

import dibd.util.Log;
import dibd.util.io.Resource;

/**
 * For every group that is synchronized with or from a remote newsserver a
 * Subscription instance exists.
 *
 * @author Christian Lins
 * @since sonews/0.5.0
 */
public class SubscriptionsProvider {
	
	public enum FeedType {	PULL, PUSH, BOTH }
	//public enum ProxyType {	HTTP, SOCKS } //or null

    private Set<Subscription> allSubs;
    private Set<String> allhosts;
    
    public class Subscription {
    	
    	private final String host; //not null
        private final int port;	//not null
        private final FeedType feedtype; //not null
        private final Proxy.Type proxytype; //not null

        //Constructor for entity
        private Subscription(String host, int port, FeedType feedtype, Proxy.Type proxytype) {
            this.host = host;
            this.port = port;
            this.feedtype = feedtype;
            this.proxytype = proxytype;
        }


		@Override
        public boolean equals(Object obj) {
            if (obj instanceof Subscription) {
                Subscription sub = (Subscription) obj;
                return sub.host.equals(host) //&& sub.group.equals(group)
                        && sub.port == port && sub.feedtype == feedtype;
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return host.hashCode() + port + feedtype.ordinal(); //+ group.hashCode();
        }

        public FeedType getFeedtype() {
            return feedtype;
        }

        /**
         * @return java.net.Proxy.Type
         */
        public Proxy.Type getProxytype() {
			return proxytype;
		}

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }
    	
    }
    
    /**
	 * Reads peers.conf
	 */
	public SubscriptionsProvider() {
		
		String peersStr = Resource.getAsString("peers.conf", true);
		if(peersStr == null) {
			Log.get().log(Level.WARNING, "Could not read peers.conf");
			//return new HashSet<>(); // return empty list
		}

		String[] peersLines = peersStr.split("\n");
		//Thread.dumpStack();
		//System.out.println("#"+Thread.currentThread().getStackTrace()+"#");
		allSubs = new HashSet<>();
		allhosts = new HashSet<>();
		for(String subLine : peersLines) {
			if(subLine.startsWith("#")) {
				continue;
			}

			subLine = subLine.trim();
			String[] subLineChunks = subLine.split("\\s+");
			if(subLineChunks.length < 1) {
				//Log.get().log(Level.WARNING, "Malformed peers.conf line: {0}", subLine);
				continue;
			}
			//one line for one host
			if (allhosts.contains(subLineChunks[0])){
				Log.get().log(Level.WARNING, "peers.conf: peer is duplicated, first line will be used: {0}", subLineChunks[0]);
				continue;
			}

			allhosts.add(subLineChunks[0]);

			FeedType ftype = FeedType.BOTH; //default
			Proxy.Type ptype = Proxy.Type.DIRECT;//default
			if (subLineChunks.length >= 3){
				if (subLineChunks[2].equalsIgnoreCase("PUSH"))
					ftype = FeedType.PUSH;
				else if (subLineChunks[2].equalsIgnoreCase("PULL"))
					ftype = FeedType.PULL;
				else if (subLineChunks[2].equalsIgnoreCase("HTTP")) //if subLineChunks.length == 3 
					ptype = Proxy.Type.HTTP;
				else if (subLineChunks[2].equalsIgnoreCase("SOCKS"))
					ptype = Proxy.Type.SOCKS;
			}

			
			if (subLineChunks.length >= 4){
				if (subLineChunks[3].equalsIgnoreCase("HTTP")){
					ptype = Proxy.Type.HTTP;
				} else if (subLineChunks[3].equalsIgnoreCase("SOCKS")){
					ptype = Proxy.Type.SOCKS;
				}
			}
			Log.get().log(Level.INFO, "Found peer subscription {0}", subLine);

			allSubs.add(new Subscription(subLineChunks[0], Integer.parseInt(subLineChunks[1]), ftype, ptype));
		}
    }
    
    /**
     * @return unmodifiable Set
     */
    public Set<Subscription> getAll() {
    	return Collections.unmodifiableSet(allSubs);
    }
    
    public boolean has(String host) {
    	if (allhosts.contains(host))
    		return true;
    	else
    		return false;
	}
    

    
    
    /*
    public Set<Group> getGroups() {
        return allgroups;
    }*/
}