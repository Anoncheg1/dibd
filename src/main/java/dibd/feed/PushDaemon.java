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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;

import dibd.config.Config;
import dibd.daemon.DaemonThread;
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
	
	//shared between PushDaemon of the same subscription 
	private final LinkedBlockingQueue<Article> articleQueue;
	
	private Subscription sub;
	
	/**
	 * Constructor for push daemon. 
	 * 
	 * @param sub
	 * @param articleQueue shared per sub
	 */
	PushDaemon(Subscription sub, LinkedBlockingQueue<Article> articleQueue){
		this.sub = sub;
		this.articleQueue = articleQueue;
	}

	/**
	 * Add article to shared FIFO queue.
	 * 
	 * @param article
	 */
	void queueForPush(Article article) {
		try {
			// If queue is full, this call blocks until the queue has free space;
			// This is probably a bottleneck for article posting
			articleQueue.put(article);
		} catch (InterruptedException ex) {
			Log.get().log(Level.WARNING, null, ex);
		}
	}
	
	
	@Override
	public void run() {
		while (isRunning()) {
			int retries = 8;
			for(int retry =1; retry<retries;retry++){
				
				Article article;
				try {
					article = articleQueue.take();
				} catch (InterruptedException ex) {
					Log.get().log(Level.FINEST, "PushDaemon interrupted: {0}", ex.getLocalizedMessage());
					return;
				}
				
				ArticlePusher ap = null;
				try{
					//for(int retry =1; retry<retries;retry++){
					//TODO: make proxy configurable for every peer
					Proxy proxy;
					try {
						proxy = FeedManager.getProxy(sub);
					} catch (NumberFormatException | UnknownHostException e) {
						Log.get().log(Level.SEVERE, "Wrong proxy configuration: {0}", e);
						return;
					}

					// POST the message to remote server

					// Connect
					
					boolean TLSenabled = Config.inst().get(Config.TLSENABLED, false);
					try { // two times we will try to connect
						ap = new ArticlePusher(FeedManager.createSocket(proxy, sub.getHost(), sub.getPort()), TLSenabled, sub.getHost());
					} catch (IOException ex) { //second try
						try {
							ap = new ArticlePusher(FeedManager.createSocket(proxy, sub.getHost(), sub.getPort()), TLSenabled, sub.getHost());
						} catch (IOException ex1) { //second try
							Log.get().warning(ex1.toString()+" to host "+sub.getHost());
							continue; //fail to connect
						}
					}

					//write article

					if(ap.writeArticle(article))
						Log.get().log(Level.FINE, "PushDaemon seccess: {0},{1},{2}", new Object[] { sub.getHost(), article.getMessageId(), article.getGroupName() } );
					else
						Log.get().log(Level.FINER, "PushDaemon {0} already have: {1}, {2}", new Object[] { sub.getHost(), article.getMessageId(), article.getGroupName() } );

					break;
				} catch (IOException ex) {
					Log.get().log(Level.INFO, "PushDaemon {0} {1} {2} , retry {3} I/O Exception: {4}", 
							new Object[] { sub.getHost(), article.getMessageId(), article.getGroupName(), retry, ex.getLocalizedMessage()});//contitune
				}catch (Exception e) {
					Log.get().log(Level.SEVERE, e.getLocalizedMessage(), e);
					return;
				}finally{
					if (ap != null)
						ap.close();
				}
				
				try {
	    			Thread.sleep(5*1000*retry*retry*retry);//geometric progression from 5 sec to 42 min 
	    		} catch (InterruptedException e) {
	    			break;
	    		}
			}
		}
	}
	
}
