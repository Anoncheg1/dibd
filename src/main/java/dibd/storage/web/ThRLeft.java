package dibd.storage.web;

import dibd.storage.article.Article;

//for StoregeWeb.getThreads()
/**
 * Thread article with number of replays do not shown.
 * Used on the board where required to show limited
 * amount of replays.
 * 
 * @author user
 *
 * @param <T> Article and ArticleWeb
 */
public class ThRLeft<T extends Article> {

	T thread;
	Integer rLeft; //may be null. hidden replays
	
	public ThRLeft(T thread, Integer left) {
		this.thread = thread;
		this.rLeft = left;
	}
	
	public T getThread() {
		return thread;
	}
	
	/**
	 * Get number of replays do not shown.
	 * 
	 * @return
	 */
	public Integer getRLeft() {
		return rLeft;
	}

}
