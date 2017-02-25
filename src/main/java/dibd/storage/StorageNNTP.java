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

import java.util.List;

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
     * 
     * @return Article or null
     * @throws StorageBackendException
     */
    Article getArticle(String message_id, Integer id) throws StorageBackendException;

    /**
     * Get Article IDs.
     * 
     * Return value or empty list
     * 
     * @param groupID
     * @param start - SQL OFFSET from 0 - return first
     * @return List<Integer>
     * @throws StorageBackendException
     */
    List<Integer> getArticleNumbers(int groupID, int start) throws StorageBackendException;

    //Done
    int getArticleCountGroup(int groupID) throws StorageBackendException;    

    
    /**
	 * Create replay.
	 * Without repeat check.
	 * 
	 * @param article
	 * @param bfile
	 * @param format       - "image/png"
	 * @throws internal Id
	 * @throws StorageBackendException
	 */	
	Article createReplay(Article article, byte[] bfile, String format)
			throws StorageBackendException;
	
	/**
	 * Create thread.
	 * Without repeat check.
	 * 
	 * @param groupName
	 * @param article
	 * @param bfile
	 * @param format       - "image/png"
	 * @throws internal Id
	 * @throws StorageBackendException
	 */
	Article createThread(Article article, byte[] bfile, String format)
			throws StorageBackendException;
	
	/**
	 * Get new articles with full threads for NEWNEWS command for "pulling".
	 * 
	 * @param group
	 * @param date
	 * @return
	 * @throws StorageBackendException
	 */
	List<String> getNewArticleIDs(Group group, long date)
			throws StorageBackendException;
	
	
	long getLastPostOfGroup(Group g) throws StorageBackendException;
	
}
