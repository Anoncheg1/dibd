package dibd.storage.web;

import dibd.storage.article.Article;

//for StoregeWeb.getThreads()
public class ThRLeft<T extends Article> {

	T thread;
	Integer rLeft; //may be null
	
	public ThRLeft(T thread, Integer left) {
		this.thread = thread;
		this.rLeft = left;
	}
	
	public T getThread() {
		return thread;
	}
	
	public Integer getRLeft() {
		return rLeft;
	}

}
