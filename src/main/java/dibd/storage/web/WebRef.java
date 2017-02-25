package dibd.storage.web;

/**
 * Article web reference.
 * thread-id + replay-id 
 * 
 * @author user
 *
 */
public class WebRef{
	
	private int thread_id;
	private int replay_id;
	
	public int getThread_id() {
		return thread_id;
	}
	public String getThread_id_hex() {
		return String.format("%X", this.thread_id);
	}

	public int getReplay_id() {
		return replay_id;
	}
	public String getReplay_id_hex() {
		return String.format("%X", this.replay_id);
	}

	public WebRef(int thread_id, int replay_id) {
		super();
		this.thread_id = thread_id;
		this.replay_id = replay_id;
	}
}