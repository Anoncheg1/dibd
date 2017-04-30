package dibd.storage.article;

/**
 * Creates a new Article object NNTP IHAVE, TAKETHIS input.
 * 
 * @author user
 *
 */
public interface ArticleInput{
	
	public int getGroupId();
	public String getGroupName();
	public String getMessageId();
	public Integer getThread_id();
	public String getMsgID_host();
	public long getPost_time();
	public Integer getHash();
	public String getA_name();
	public String getSubject();
	public String getMessage();
	public String getPath_header();
	public String getFileName();
	public String getFileCT();
	public Integer getStatus();

}
