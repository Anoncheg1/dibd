package dibd.daemon;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.Charset;

import dibd.storage.GroupsProvider.Group;
import dibd.storage.article.Article;


/**
 * Interface for NNTP commands
 *
 */
public interface NNTPInterface{

	/**
     * Puts the given line into the output buffer, adds a newline character and
     * returns. The method returns immediately and does not block until the line
     * was sent. Terminated sequence \r\n (NNTPConnection.NEWLINE) added at the end.
     * If several lines passed, then \r\n must be added manually after each line except the last one.
     *
     * @param line
     * @param charset
	 * @throws ClosedChannelException 
     * @throws java.io.IOException
     */
	void println(String line) throws IOException;
	
	/**
	 * Puts article to output buffers. Article must have "\r\n" at the end and UTF-8 encoded.
	 * 
	 * @param fs
	 * @param mId 
	 * @throws IOException
	 */
	void print(FileInputStream fs, String mId) throws IOException;
	
	//void println(byte[] source)  throws IOException; unsafe for buffers recycling

	/**
	 * UTF-8
	 */
	Charset getCurrentCharset();

	Article getCurrentArticle();

	Group getCurrentGroup();

	void setCurrentArticle(Article article);

	void setCurrentGroup(Group group);
	
	void close();
	
	TLS getTLS();

	void setTLS(TLS tls);
	
	public boolean isTLSenabled();
	
	/**
	 * Socket connected to.
	 * 
	 * @return
	 */
	String getHost();

}
