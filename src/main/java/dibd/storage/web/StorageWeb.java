/**
 * 
 */
package dibd.storage.web;

import java.io.File;
import java.util.List;
import java.util.Map;

import dibd.storage.StorageBackendException;
import dibd.storage.article.ArticleForPush;
import dibd.storage.article.ArticleOutput;
import dibd.storage.article.ArticleWebInput;

/**
 * A back-end interface designed for use in web front-end
 * to access all required data.
 *
 * @author Vitalij Chepelev
 * @since dibd/1.0.1
 */
public interface StorageWeb {

	/**
     * Get article for message_id:{@literal <}random{@literal @}host{@literal >} OR for internal id
     * Used to replace short-reference with message-Id 
     * 
	 * @param message_id
	 * @param id
	 * @return Article or null
	 * @throws StorageBackendException
	 */
	ArticleOutput getArticleWeb(String message_id, Integer id) throws StorageBackendException;
	
	/**
	 * Get threads count(*) for groupId. Navigation bar. BoardService.
	 * 
	 * Return 0 if group do not exist.
	 * 
	 * @param groupId
	 * @return 0 if nothing found
	 * @throws StorageBackendException
	 */
	int getThreadsCountGroup(int groupId) throws StorageBackendException;

	
	/**
	 * Create replay. ThreadService.
	 * Return null if thread already exist with that hash
	 * and time for last 180 sec.
	 * 
	 * @param article
	 * @param file if null file_ct should also be null
	 * @throws StorageBackendException
	 * @return null if exist
	 */	
	ArticleForPush createReplayWeb(ArticleWebInput article, File file)
			throws StorageBackendException;
	
	/**
	 * Create thread. ThreadService.
	 * Return null if thread already exist with that hash
	 * and time for last 180 sec.
	 * 
	 * @param article
	 * @param file if null file_ct should also be null
	 * @throws StorageBackendException
	 * @return null if exist
	 */
	ArticleForPush createThreadWeb(ArticleWebInput article, File file)
			throws StorageBackendException;
	
	/**
	 * Get threads for web front-end board. BoardService.
	 * 
	 * throw StorageBackendException if threads was not found.
	 * 
	 * @param boardId
	 * @param boardPage
	 * @param boardName
	 * @return thread,rLeft
	 * @throws StorageBackendException
	 */
	Map<ThRLeft<ArticleOutput>, List<ArticleOutput>> getThreads(int boardId, int boardPage, String boardName) throws StorageBackendException;
	
	/**
	 * Get one thread. ThreadService.
	 * boardName used for article initialization only.
	 * 
	 * If thread do not found 
	 * 
	 * @param threadId
	 * @param boardName for article attachment string
	 * @return
	 * @throws StorageBackendException if no such thread
	 */
	List<ArticleOutput> getOneThreadWeb(int threadId, String boardName) throws StorageBackendException;
	
	/**
	 * Get amount of rLeft for thread.
	 * Required to make limit of rLeft.
	 * If thread do not exist return -1 else rLeft or 0 if no rLeft.
	 * 
	 * @param threadId
	 * @return if thread exist rLeft returned if no such thread -1.
	 * @throws StorageBackendException
	 */
	int getReplaysCount(int threadId) throws StorageBackendException;
	
}
