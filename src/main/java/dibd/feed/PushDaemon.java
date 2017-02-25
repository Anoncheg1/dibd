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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.net.Proxy.Type;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;

import dibd.config.Config;
import dibd.daemon.DaemonThread;
import dibd.storage.StorageManager;
import dibd.storage.SubscriptionsProvider;
import dibd.storage.GroupsProvider.Group;
import dibd.storage.SubscriptionsProvider.FeedType;
import dibd.storage.SubscriptionsProvider.Subscription;
import dibd.storage.article.Article;
import dibd.util.Log;

/**
 * Pushes new articles to remote newsservers. This feeder sleeps until a new
 * message is posted to the sonews instance.
 *
 * @author Christian Lins
 * @since sonews/0.5.0
 */


public class PushDaemon extends DaemonThread {

	// TODO Make configurable
	public static final int QUEUE_SIZE = 256;

	//POST, IHAVE, TAKETHIS, WEB input
	private final static LinkedBlockingQueue<Article> articleQueue = new LinkedBlockingQueue<>(QUEUE_SIZE);
	
	private static volatile boolean running = false;

	public static void queueForPush(Article article) {
		if (running){
			try {
				// If queue is full, this call blocks until the queue has free space;
				// This is probably a bottleneck for article posting
				articleQueue.put(article);
			} catch (InterruptedException ex) {
				Log.get().log(Level.WARNING, null, ex);
			}
		}
	}
	
	
	@Override
	public void run() {
		PushDaemon.running = isRunning(); 
		while (isRunning()) {
			try {
				
				Article article = PushDaemon.articleQueue.take();
				
				String newsgroup = article.getGroupName();
				assert(newsgroup != null);
				
				Group group = StorageManager.groups.get(newsgroup);
				if(group.isDeleted())
					continue;
				//кроме источника, отправителя и пройденных
				if (!group.getHosts().isEmpty())
					for(String s : group.getHosts())
						for (Subscription sub : StorageManager.peers.getAll()) {
							
							if (!sub.getHost().equals(s))//subscription must be in list of group
								continue;
							if (sub.getFeedtype() == FeedType.PULL) //It is PUSH and BOTH
								continue;
							
							
							// Circle check: if subscribers in path, then they already have message.
							boolean check = false;
							String path = article.getPath_header();
							if (path != null)
								for(String ps : Arrays.asList(path.split("!")))
									if (ps.equalsIgnoreCase(sub.getHost()))
										check = true;
							if (check || article.getMsgID_host().equalsIgnoreCase(sub.getHost()))
								continue;

							//TODO: make proxy configurable for every peer
				    		Proxy proxy;
							try {
								proxy = FeedManager.getProxy(sub);
							} catch (NumberFormatException | UnknownHostException e) {
								Log.get().log(Level.SEVERE, "Wrong proxy configuration: {0}", e);
								return;
							}
							
							// POST the message to remote server
							
							ArticlePusher ap = null;
							boolean TLSenabled = Config.inst().get(Config.TLSENABLED, false);
							try { // two times we will try to connect
								ap = new ArticlePusher(FeedManager.createSocket(proxy, sub.getHost(), sub.getPort()), TLSenabled, sub.getHost());
							} catch (IOException ex) { //second try
								try {
									ap = new ArticlePusher(FeedManager.createSocket(proxy, sub.getHost(), sub.getPort()), TLSenabled, sub.getHost());
								} catch (IOException ex1) { //second try
									Log.get().info(ex1.toString()+" to host "+sub.getHost());
									continue; //fail to connect
								}
							}
							
							try {
								ap.writeArticle(article);
								Log.get().log(Level.INFO, "PushFeed secess: {0},{1},{2}", new Object[] { sub.getHost(), article.getMessageId(),group.getName() } );
							} catch (IOException ex) {
								if (ex.getMessage().startsWith("436"))
									PushDaemon.articleQueue.put(article);//we put article to query again, Possible infinity loop!!
								else if (ex.getMessage().startsWith("437"))
									continue;
								else
									Log.get().log(Level.WARNING, "PushFeeder I/O Exception: {0}", ex);
								continue;
							}finally{
								ap.close();
							}
						}
			} catch (InterruptedException ex) {
				Log.get().log(Level.WARNING, "PushFeeder interrupted: {0}", ex);
				return;
			}
		}
	}

	
}
