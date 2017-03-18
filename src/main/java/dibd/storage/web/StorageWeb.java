/**
 * 
 */
package dibd.storage.web;

import java.util.List;
import java.util.Map;

import dibd.storage.StorageBackendException;
import dibd.storage.article.Article;

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
     * @return Article or null
     * @throws StorageBackendException
     */
	Article getArticle(String message_id, Integer id) throws StorageBackendException;
	
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
	 * @param bfile
	 * @param file_ct   -content-type
	 * @param file_name
	 * @throws internal Id
	 * @throws StorageBackendException
	 * @return null if exist or article with id.
	 */	
	Article createReplayWeb(Article article, byte[] bfile, String file_ct, String file_name)
			throws StorageBackendException;
	
	/**
	 * Create thread. ThreadService.
	 * Return null if thread already exist with that hash
	 * and time for last 180 sec.
	 * 
	 * @param groupName
	 * @param article
	 * @param bfile
	 * @param file_ct
	 * @param file_name
	 * @throws internal Id
	 * @throws StorageBackendException
	 * @return null if exist or article with id.
	 */
	Article createThreadWeb(Article article, byte[] bfile, String file_ct, String file_name)
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
	Map<ThRLeft<Article>, List<Article>> getThreads(int boardId, int boardPage, String boardName) throws StorageBackendException;
	
	/**
	 * Get one thread. ThreadService.
	 * boardName used for article initialization only.
	 * 
	 * If thread do not found 
	 * 
	 * @param threadId
	 * @param boardName for article attachment string
	 * @param status
	 * @return
	 * @throws StorageBackendException if no such thread
	 */
	List<Article> getOneThread(int threadId, String boardName, int status) throws StorageBackendException;
	
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
