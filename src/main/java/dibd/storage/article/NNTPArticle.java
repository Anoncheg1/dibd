package dibd.storage.article;

import java.io.File;

public class NNTPArticle {
	
	/**
	 * if not multipart may contain full article 
	 * if command HEAD only head
	 */
	final public String before_attach;
	/**
	 * if multipart it must be file.
	 */
	final public File attachment;
	/**
	 * may be null
	 */
	final public String after_attach;

	/**
	 * @param before_attach
	 * @param attachment
	 * @param after_attach
	 */
	public NNTPArticle(String before_attach, File attachment, String after_attach) {
		this.before_attach = before_attach;
		this.attachment = attachment;
		this.after_attach = after_attach;
	}


}
