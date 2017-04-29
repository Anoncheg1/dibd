package dibd.storage.article;

public interface ArticleForOverview {
	//IndexObserver
	public String getGroupName();
	public Integer getId();
	public Integer getThread_id();
	public String getSubject();
	public String getMessage();
	public String getFileName();
	public Integer getStatus();
}
