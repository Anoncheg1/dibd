package dibd.storage.article;

import java.io.IOException;
import java.nio.charset.Charset;

public interface ArticleForPush {
	public String getGroupName();
	public String getPath_header();
	public String getMsgID_host();
	public String getMessageId();
	public NNTPArticle buildNNTPMessage(Charset charset, int what) throws IOException;

}
