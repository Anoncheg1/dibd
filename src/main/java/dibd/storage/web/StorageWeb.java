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
	 * @param format       - "image/png"
	 * @throws internal Id
	 * @throws StorageBackendException
	 */	
	Article createReplayWeb(Article article, byte[] bfile, String format)
			throws StorageBackendException;
	
	/**
	 * Create thread. ThreadService.
	 * Return null if thread already exist with that hash
	 * and time for last 180 sec.
	 * 
	 * @param groupName
	 * @param article
	 * @param bfile
	 * @param format       - "image/png"
	 * @throws internal Id
	 * @throws StorageBackendException
	 */
	Article createThreadWeb(Article article, byte[] bfile, String format)
			throws StorageBackendException;
	
	/**
	 * Get threads for web front-end board. BoardService.
	 * 
	 * throw StorageBackendException if threads was not found.
	 * 
	 * @param boardId
	 * @param boardPage
	 * @param boardName
	 * @return thread,replays
	 * @throws StorageBackendException
	 */
	Map<Article,List<Article>> getThreads(int boardId, int boardPage, String boardName) throws StorageBackendException;
	
	/**
	 * Get one thread. ThreadService.
	 * boardName used for article initialization only.
	 * 
	 * throw StorageBackendException if thread do not found.
	 * 
	 * 
	 * @param threadId
	 * @param boardName for article attachment string
	 * @return
	 * @throws StorageBackendException when restart fail
	 */
	List<Article> getOneThread(int threadId, String boardName) throws StorageBackendException;
	
	/**
	 * Get amount of replays+1 for thread.
	 * Required to make limit of replays.
	 * If thread do not exist return 0 else replays+1 or 1 if no replays.
	 * 
	 * @param threadId
	 * @return if thread exist replays+1 returned.
	 * @throws StorageBackendException
	 */
	int getReplaysCount(int threadId) throws StorageBackendException;
	
}
