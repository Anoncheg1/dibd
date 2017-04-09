package dibd.storage;

public class ScrapLine {

	public int thread_id;
	public int id;
	public String message_id;
	public long article_post_time;
	
	public ScrapLine(int thread_id, int id, String message_id, long article_post_time) {
		this.thread_id = thread_id;
		this.id = id;
		this.message_id = message_id;
		this.article_post_time = article_post_time;
	}

}
