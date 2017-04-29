package dibd.storage.article;

import java.io.IOException;
import java.nio.charset.Charset;

/**
 * 
 * Converted to Article in web-fronted
 * 
 * @author user
 *
 */
public interface ArticleOutput {
	public Integer getId();
	public Integer getThread_id();
	public String getMessageId();
	public String getGroupName();
	public NNTPArticle buildNNTPMessage(Charset charset, int what) throws IOException;
	public Integer getStatus();
	
	//TODO: check status instead
	@Deprecated
	public String getMsgID_host();
	
	//for frontend to use getGlobalRefs
	public String getMessage();
}
