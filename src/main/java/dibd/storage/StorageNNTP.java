/*
 *   SONEWS News Server
 *   see AUTHORS for the list of contributors
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package dibd.storage;

import java.io.File;
import java.util.List;
import java.util.Map;

import dibd.storage.GroupsProvider.Group;
import dibd.storage.article.Article;

/**
 * A generic storage backend interface.
 *
 * @author Christian Lins
 * @since sonews/1.0
 */
public interface StorageNNTP {

    

    /**
     * Get article for message_id:{@literal <}random{@literal @}host{@literal >} OR for internal id
     * articles with <= status;
     * 
     * status we get <= status. 1 = 0 and 1. 0 = for 0 only 
     * 
     * @return Article or null
     * @throws StorageBackendException
     */
    Article getArticle(String message_id, Integer id, int status) throws StorageBackendException;
    
    /**
     * Simple id to message_id
     * 
     * @param id
     * @return
     * @throws StorageBackendException
     */
    String getMessageId(int id) throws StorageBackendException;

    /**
     * Get Article IDs.
     * 
     * For ARTICLE command old format.
     * 
     * Return value or empty list
     * 
     * @param groupID
     * @param start - SQL OFFSET from 0 - return first
     * @return List<Integer>
     * @throws StorageBackendException
     */
    List<Integer> getArticleNumbers(int groupID, int start) throws StorageBackendException;

    //Group command
    int getArticleCountGroup(int groupID) throws StorageBackendException;    

    
    /**
	 * Create replay.
	 * Without repeat check.
	 * 
	 * @param article
	 * @param bfile if bfile == null we assume that file was too large 
	 * and we save it partially with status = 1. file_ct and file_name should not be null then. 
	 * @param file_ct   - content-type
	 * @param file_name
	 * @throws StorageBackendException
	 * @return article never null
	 */	
	Article createReplay(Article article, File file, String file_ct, String file_name)
			throws StorageBackendException;
	
	/**
	 * Create thread.
	 * Without repeat check.
	 * 
	 * Null never returned.
	 * 
	 * @param groupName
	 * @param article
	 * @param bfile if bfile == null we assume that file was too large 
	 * and we save it partially with status = 1. file_ct and file_name should not be null then.
	 * @param file_name
	 * @throws StorageBackendException
	 * @return article never null
	 */
	Article createThread(Article article, File file, String file_ct, String file_name)
			throws StorageBackendException;
	
	/**
	 * Get ordered new articles with full threads for NEWNEWS command for "pulling".
	 * May be empty
	 * 
	 * mId, null - thread
	 * mId, mId_above - replay
	 * 
	 * @param group
	 * @param date
	 * @return article-message_id
	 * @throws StorageBackendException
	 */
	Map<String, String> getNewArticleIDs(Group group, long date)
			throws StorageBackendException;
	
	
	/**
	 * Get last_post time in group
	 * 
	 * @param group
	 * @return 0 if group is empty
	 * @throws StorageBackendException
	 */
	//long getLastPostOfGroup(Group group) throws StorageBackendException;
	
	
	/**
	 * Get one thread. for XOVER
	 * boardName used for article initialization only.
	 * 
	 * throw StorageBackendException if thread do not found.
	 * 
	 * 
	 * @param threadId
	 * @param boardName for article attachment string
	 * @return
	 * @throws StorageBackendException if no such thread
	 */
	List<Article> getOneThread(int threadId, String boardName, int status) throws StorageBackendException;
	
	
	/**
	 * Scrap threads with status = 0 only. 1 rejected
	 * 
	 * Groups and articles are sorted by date (for postgresql tested)
	 * 
	 * never null
	 * 
	 * @param group
	 * @param limit thread amount
	 * @return threads with his articles, all sorted by last_post_time first and post_time second
	 * @throws StorageBackendException
	 */
	List<ScrapLine> scrapGroup(Group group, int limit) throws StorageBackendException;
	
	
	
	/**
	 * For index page used in ObservableDatabase
	 * 
	 * 
	 * @param status
	 * @param limit
	 * @return
	 * @throws StorageBackendException
	 */
	List<Article> indexLastArts(int status, int limit) throws StorageBackendException;
	
}
