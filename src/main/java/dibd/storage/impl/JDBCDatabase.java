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

package dibd.storage.impl;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;

import org.im4java.core.IM4JavaException;

import dibd.config.Config;
import dibd.storage.AttachmentProvider;
import dibd.storage.StorageBackendException;
import dibd.storage.StorageManager;
import dibd.storage.StorageNNTP;
import dibd.storage.GroupsProvider.Group;
import dibd.storage.article.Article;
import dibd.storage.web.StorageWeb;
import dibd.util.Log;

/**
 * Storage backend facade class for a relational SQL database using JDBC. The
 * statements used should work for at least PostgreSQL and MySQL.
 *
 * @author 
 * @since sonews/2.0
 */
public class JDBCDatabase implements StorageWeb, StorageNNTP {// implements Storage
	public static final int MAX_RESTARTS = 2;

	protected Connection conn = null;

	protected PreparedStatement pstmtGetThreadCountGroup = null;
	
	protected PreparedStatement pstmtGetId = null;

	protected PreparedStatement pstmtRepeatCheck = null;

	protected PreparedStatement pstmtDeleteOneOldestThread1 = null;
	protected PreparedStatement pstmtDeleteOneOldestThread2 = null;
	protected PreparedStatement pstmtDeleteOneOldestThread3 = null;

	protected PreparedStatement pstmtCreateThread1 = null;
	protected PreparedStatement pstmtCreateThread2 = null;
	protected PreparedStatement pstmtCreateThread3 = null;
	
	protected PreparedStatement pstmtAttachmentSaving = null; //in transaction only

	protected PreparedStatement pstmtCreateReplay1 = null;
	protected PreparedStatement pstmtCreateReplay2 = null;

	protected PreparedStatement pstmtGetThreads1 = null;
	protected PreparedStatement pstmtGetThreads2 = null;
	
	protected PreparedStatement pstmtGetOneThread = null;
	
	protected PreparedStatement pstmtGetReplaysCount = null;

	//NNTP
	protected PreparedStatement pstmtAddArticle1 = null;
	protected PreparedStatement pstmtAddArticle2 = null;
	protected PreparedStatement pstmtAddArticle3 = null;
	protected PreparedStatement pstmtAddArticle4 = null;
	protected PreparedStatement pstmtGetArticle = null;
	protected PreparedStatement pstmtGetArticleNumbers = null;
	protected PreparedStatement pstmtGetArticleCountGroup = null;
	protected PreparedStatement pstmtGetNewArticleIDs = null;
	protected PreparedStatement pstmtGetLastPostOfGroup = null;
	
	/** How many times the database connection was reinitialized */
	protected int restarts = 0;

	
	/**
	 * Rises the database: reconnect and recreate all prepared statements.
	 *
	 * @throws java.sql.SQLException
	 */
	public void arise() throws SQLException {// protected
		try {
			// Establish database connection
			this.conn = DriverManager.getConnection(
					Config.inst().get(Config.LEVEL_FILE, Config.STORAGE_DATABASE, "<not specified>"),
					Config.inst().get(Config.LEVEL_FILE, Config.STORAGE_USER, "root"),
					Config.inst().get(Config.LEVEL_FILE, Config.STORAGE_PASSWORD, ""));

			this.conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
			if (this.conn.getTransactionIsolation() != Connection.TRANSACTION_SERIALIZABLE) {
				Log.get().warning("Database is NOT fully serializable!");
			}

			//		     ************ WEB **********
			// Prepare simple statements for method getThreadCountGroup and others
			this.pstmtGetThreadCountGroup = conn.prepareStatement("SELECT count(*) FROM thread WHERE group_id = ?;");
			
			// Prepare simple statements for method createThread() and createReplay()
			this.pstmtGetId = conn.prepareStatement("SELECT id FROM article WHERE id = ?;");

			// Prepare statements for method repeatCheck()
			this.pstmtRepeatCheck = conn
					.prepareStatement("SELECT article.id FROM article WHERE hash = ? AND post_time BETWEEN ? AND ?;");

			// Prepare statements for method deleteOneOldestThread()
			this.pstmtDeleteOneOldestThread1 = conn
					.prepareStatement("SELECT thread.thread_id, message_id, message_id_host FROM thread, article "
							+ "WHERE article.thread_id = thread.thread_id AND last_post_time IN "
							+ "(SELECT min(last_post_time) FROM thread WHERE group_id = ?);");
			this.pstmtDeleteOneOldestThread2 = conn
					.prepareStatement("SELECT file_path FROM attachment WHERE article_id IN "
							+ "(SELECT id FROM article WHERE thread_id = ?);");
			this.pstmtDeleteOneOldestThread3 = conn
					.prepareStatement("DELETE FROM thread WHERE thread_id = ?;");
			/*this.pstmtDeleteOneOldestThread1 = conn
					.prepareStatement("SELECT file_path FROM attachment WHERE article_id IN "
							+ "(SELECT id FROM article WHERE thread_id IN "
							+ "(SELECT thread_id FROM thread WHERE last_post_time IN "
							+ "(SELECT min(last_post_time) FROM thread WHERE group_id = ?)));");
			this.pstmtDeleteOneOldestThread2 = conn.prepareStatement("DELETE FROM thread WHERE last_post_time "
					+ "IN (SELECT MIN(last_post_time) FROM thread WHERE group_id = ?);");*/

			// Prepare statements for method createThread()
			this.pstmtCreateThread1 = conn.prepareStatement(
					"INSERT INTO article (id, thread_id, message_id, message_id_host, hash, a_name, subject, message, post_time, path_header) VALUES (?, null,?,?,?,?,?,?,?,?);");
			this.pstmtCreateThread2 = conn.prepareStatement(
					"INSERT INTO thread (thread_id, group_id, last_post_time) VALUES (?,?,?);");
			this.pstmtCreateThread3 = conn
					.prepareStatement("UPDATE article SET thread_id = ? WHERE id = ?;");

			// Prepare statements for method attachmentSaving()
			this.pstmtAttachmentSaving = conn
					.prepareStatement("INSERT INTO attachment (article_id, file_path, media_type) VALUES (?,?,?);");

			// Prepare statements for method createReplay()
			this.pstmtCreateReplay1 = conn.prepareStatement(
					"INSERT INTO article (id, thread_id, message_id, message_id_host, hash, a_name, subject, message, post_time, path_header) VALUES (?,?,?,?,?,?,?,?,?,?);");
			this.pstmtCreateReplay2 = conn
					.prepareStatement("UPDATE thread SET last_post_time = ? WHERE thread_id = ?;");

			// Prepare statements for method getThreads()
			this.pstmtGetThreads1 = conn.prepareStatement(
					"SELECT article.id, article.message_id, article.message_id_host, article.a_name, article.subject, article.message, article.post_time, attachment.file_path "
							+ "FROM thread, article "
							+ "LEFT JOIN attachment ON article.thread_id = attachment.article_id "
							+ "WHERE thread.group_id = ? AND thread.thread_id = article.id ORDER BY last_post_time DESC LIMIT ? OFFSET ?;"); //threads
			this.pstmtGetThreads2 = conn.prepareStatement(
					"SELECT article.id, article.message_id, article.message_id_host, article.a_name, article.subject, article.message, article.post_time, attachment.file_path "
							+"FROM article "
							+"LEFT JOIN attachment ON article.id = attachment.article_id " 
							+ "WHERE article.thread_id = ? ORDER BY post_time DESC LIMIT ?;"); //comments
			
			// Prepare statements for method getOneThread(int)
			this.pstmtGetOneThread = conn.prepareStatement("SELECT article.id, article.message_id, article.message_id_host, article.a_name, article.subject, article.message, article.post_time, attachment.file_path "
							+ "FROM article "
							+ "LEFT JOIN attachment ON article.id = attachment.article_id "
							+ "WHERE article.thread_id = ? ORDER BY post_time;");
			// Prepare statements for method getReplaysCount(int)
			this.pstmtGetReplaysCount = conn.prepareStatement("SELECT COUNT(*) FROM article WHERE thread_id = ?;");
			
			//     ************ NNTP **********
			// Prepare simple statements for method getArticleCountGroup
						this.pstmtGetArticleCountGroup = conn.prepareStatement("SELECT count(*) FROM thread,article WHERE group_id = ? AND article.thread_id = thread.thread_id;");
			// Prepare simple statements for method getArticleNumbers
						this.pstmtGetArticleNumbers = conn.prepareStatement("SELECT article.id,post_time FROM thread,article WHERE group_id=? AND article.thread_id = thread.thread_id ORDER BY post_time OFFSET ?");
			// Prepare simple statements for method getArticle
						this.pstmtGetArticle = conn.prepareStatement("SELECT id, article.thread_id, message_id, message_id_host, a_name, subject, message, post_time, path_header, group_id, file_path, media_type "
							+ "FROM thread, article "
							+"LEFT JOIN attachment ON article.id = attachment.article_id "
							+"WHERE (( article.message_id = ? ) OR article.id = ?) AND thread.thread_id = article.thread_id;");
			// Prepare simple statements for method getNewArticleIDs
						this.pstmtGetNewArticleIDs = conn.prepareStatement("SELECT article.message_id FROM article, thread "
								+"WHERE group_id=? AND thread.last_post_time >= ? AND article.thread_id = thread.thread_id ORDER BY article.post_time;");
			// Prepare simple statements for method getLastPostOfGroup
						this.pstmtGetLastPostOfGroup = conn.prepareStatement("SELECT MAX(last_post_time) FROM thread WHERE group_id = ?;");
						
			
			

		} catch (Exception ex) {
			throw new Error("Database connection problem!", ex);
		}
	}
	
	/**
	 * Restart the JDBC connection to the Database server.
	 * 
	 * @param cause
	 * @throws StorageBackendException
	 */
	protected void restartConnection(SQLException cause) throws StorageBackendException {
		Log.get().log(Level.SEVERE,
				Thread.currentThread() + ": Database connection was closed (restart " + restarts + ").", cause);

		if (++restarts >= MAX_RESTARTS) {
			// Delete the current, probably broken JDBCDatabase instance.
			// So no one can use the instance any more.
			JDBCStorageProvider.instances.remove(Thread.currentThread());

			// Throw the exception upwards
			System.out.println("Database connection restart:"+restarts);
			throw new StorageBackendException(cause);
		}

		try {
			Thread.sleep(1500L * restarts);
		} catch (InterruptedException ex) {
			// Sleep was interrupted. Ignore the InterruptedException.
		}

		// Try to properly close the old database connection
		try {
			if (this.conn != null) {
				this.conn.close();
			}
		} catch (SQLException ex) {
			Log.get().warning(ex.getMessage());
		}

		this.conn = null;

		try {
			// Try to reinitialize database connection
			arise();
		} catch (SQLException ex) {
			Log.get().warning(ex.getMessage());
			restartConnection(ex);
		}
	}
	
	
	/**
	 * Check if article exist - hash and post_time used.
	 * 
	 * @param article
	 * @return true if exist.
	 * @throws StorageBackendException
	 */
	private boolean repeatCheck(int hash, long post_time) throws StorageBackendException {
		ResultSet rs = null;
		try {
			pstmtRepeatCheck.setInt(1, hash);
			pstmtRepeatCheck.setLong(2, post_time-180);
			pstmtRepeatCheck.setLong(3, post_time);
			rs = pstmtRepeatCheck.executeQuery();
			if (rs.next()) {
				return true;
			} else {
				return false;
			}
		} catch (SQLException ex) {
			restartConnection(ex);
			return repeatCheck(hash, post_time);
		} finally {
			closeResultSet(rs);
			//this.restarts = 0; // we don not reset for private method
		}
	}

	/**
	 * Save attachment for article and save generated thumbnail
	 * MUST BE EXECUTED IN NTRANSACTION UNIT ONLY
	 * 
	 * @param id
	 * @param groupName
	 * @param media_type
	 * @param bfile
	 * @throws SQLException
	 * @throws StorageBackendException 
	 */
	private void attachmentSaving(final int id, final String groupName, final String media_type, byte[] bfile) throws SQLException, StorageBackendException{
		if (media_type.length() > 256)
			throw new StorageBackendException("Tool long media-type");
		String fileName = String.valueOf(id) + "." + media_type.split("/")[1].split("[+]")[0];
		
		try {
			//save file
			StorageManager.attachments.saveFile(groupName, fileName, bfile);
			//save database record
			this.pstmtAttachmentSaving.setInt(1, id);
			this.pstmtAttachmentSaving.setString(2, fileName);
			this.pstmtAttachmentSaving.setString(3, media_type);
			this.pstmtAttachmentSaving.execute();
			//create thumbbnail
			try {
				StorageManager.attachments.createThumbnail(groupName, fileName);
			} catch (InterruptedException | IM4JavaException e) {
				e.printStackTrace();
				// Log.get().log(Level.SEVERE, "?????? of
				// createTread() failed: {0}", ex2);
			}
		} catch (IOException e) {
			Log.get().log(Level.SEVERE, "Can't save attachment createThread() failed: {0}", e);
		}
	} 
	
	/**
	 * Delete one oldest thread and all files in it,
	 * 
	 * @param groupId
	 * @param groupName
	 * @throws StorageBackendException
	 * @throws SQLException
	 */
	private void deleteOneOldestThread(int groupId, String groupName) throws StorageBackendException {
		ResultSet thread = null;
		ResultSet file = null;
		try {//1) get oldest thread_id
			this.conn.setAutoCommit(false); // start transaction
			
			pstmtDeleteOneOldestThread1.setInt(1, groupId);
			thread = pstmtDeleteOneOldestThread1.executeQuery();
			
			if (thread.next()){ //thread.thread_id(const), message_id, message_id_host
				int thread_id = thread.getInt(1);
				//2) get file path for images in thread
				pstmtDeleteOneOldestThread2.setInt(1, thread_id);
				file = pstmtDeleteOneOldestThread2.executeQuery();
				while (file.next())
					StorageManager.attachments.delFile(groupName, file.getString(1));
				//3) delete thread
				pstmtDeleteOneOldestThread3.setInt(1, thread_id);
				pstmtDeleteOneOldestThread3.executeUpdate();
				// transaction end
				this.conn.commit(); //I hope this will not close resultSet
				this.conn.setAutoCommit(true);
				//delete nntp cache
				do {
					String host = thread.getString(3).trim();
					if (!host.equals(Config.inst().get(Config.HOSTNAME, null)))
						//groupName, message_id=thread.getString(2)
						StorageManager.nntpcache.delArticle(groupName, thread.getString(2).trim());
				}while(thread.next());
			}
			
		} catch (SQLException ex) {
			Log.get().log(Level.SEVERE, "Fail in deleteOneOldestThread() failed: {0}", ex);
			this.restartConnection(ex);
			deleteOneOldestThread(groupId, groupName);
		} finally {
			closeResultSet(thread);
			//no oldest thread maybe at start
			closeResultSet(file);
			//this.restarts = 0; // we do not reset for private methods
		}
	}
	
	public int getThreadsCountGroup(int groupId) throws StorageBackendException {
		ResultSet rs = null;
		int ret = 0;
		try {
			pstmtGetThreadCountGroup.setInt(1, groupId);
			rs = pstmtGetThreadCountGroup.executeQuery();
			if (rs.next())
				ret = rs.getInt(1);
		} catch (SQLException ex) {
			restartConnection(ex);
			return getThreadsCountGroup(groupId);
		} finally {
			closeResultSet(rs);
			this.restarts = 0; // Reset error count
		}
		return ret;
	}
	
	/**
	 * 16 - hex base 16^x-1 = count of F in ID
	 * It can be increased or decreased without lose.
	 * max value Integer.MAX_VALUE=7fffffff 
	 * 5 is optimal
	 */
	static private final int  ID_LENGHT = (int) Math.pow(16, Config.inst().get(Config.ID_F_COUNT, 5))-1;
	
	/**
	 * Generate random id in and check if such id exist in loop.
	 * If we have 250,000 articles and id is 16-5 then 
	 * the probability of obtaining a unique number is:
	 * (16^5 -  250000)/ 16^5 ~ 76% = A
	 * the probability of success for first or second attempt is:
	 * A + A*(250000/16^5) ~ 94% for 50000 ~ 77% for 900000 ~ 26%
	 * 
	 * @return id
	 * @throws SQLException
	 */
	private int genId() throws SQLException{
		// Generate id
		int id;
		Random r = new Random();
		ResultSet rs;
		do{
			id = r.nextInt(ID_LENGHT);//16^x-1
			pstmtGetId.setInt(1, id);
			rs = pstmtGetId.executeQuery(); //rs is not closed....
		}while(rs.next()); // Check if exist
		return id;
	}
	
	
	public Article createThreadWeb(Article article, byte[] bfile, String media_type)
			throws StorageBackendException {
		if (repeatCheck(article.getHash(), article.getPost_time()))
			return null;
		else
			return createThread(article, bfile, media_type);
	}
	
	public Article createThread(Article article, byte[] bfile, String media_type)
			throws StorageBackendException {
		ResultSet rs = null;
		int id = 0;
		int groupId = article.getGroupId();
		String groupName = article.getGroupName();
		
		//delete old threads
		try {
			// Count on all threads in group
			pstmtGetThreadCountGroup.setInt(1, groupId);
			rs = pstmtGetThreadCountGroup.executeQuery();
			rs.next(); //return always true;  
			int threads_per_page = Config.inst().get(Config.THREADS_PER_PAGE, 5);
			int pages = Config.inst().get(Config.PAGE_COUNT, 6);
			//TODO:make configurable
			int archive_threads = 5;

			int threadsNow = rs.getInt(1); //0 or >0

			int max_threads =  threads_per_page * pages + archive_threads;
			if (threadsNow > max_threads+0) {//0 delete 1 threads at once.
				for (int i = threadsNow; i > max_threads; i--)
					deleteOneOldestThread(groupId, groupName);
			}
		} catch (SQLException e1) {
			throw new StorageBackendException("Count postings return nothing!");
		}finally{
			closeResultSet(rs);	
		}
		
		try {
			this.conn.setAutoCommit(false); // start transaction
			// Generate id
			id = genId();
			String messageId = article.getMessageId();
			
			assert(article.getMsgID_host() != null);
			if (messageId == null){
				String mId_random = Integer.toHexString(id) + article.getPost_time();
				messageId = "<"+ mId_random + "@" + article.getMsgID_host() +">";
			}
			// insert article
			pstmtCreateThread1.setInt(1, id);
			pstmtCreateThread1.setString(2, messageId);
			pstmtCreateThread1.setString(3, article.getMsgID_host());
			pstmtCreateThread1.setInt(4, article.getHash());
			pstmtCreateThread1.setString(5, article.getA_name());
			pstmtCreateThread1.setString(6, article.getSubject());
			pstmtCreateThread1.setString(7, article.getMessage());
			pstmtCreateThread1.setLong(8, article.getPost_time());
			pstmtCreateThread1.setString(9, article.getPath_header());
			pstmtCreateThread1.execute();
			// insert thread
			pstmtCreateThread2.setInt(1, id);
			pstmtCreateThread2.setInt(2, groupId);
			pstmtCreateThread2.setLong(3, article.getPost_time());
			pstmtCreateThread2.execute();
			//update article
			pstmtCreateThread3.setInt(1, id);
			pstmtCreateThread3.setInt(2, id);
			pstmtCreateThread3.execute();
			// transaction end
			

			// attachment saving
			if (bfile != null && media_type != null)
				attachmentSaving(id, groupName, media_type, bfile);
			
			this.conn.commit();
			this.conn.setAutoCommit(true);

			return new Article(article, id, messageId, media_type);
		} catch (SQLException ex) {
			try {
				this.conn.rollback(); // Rollback changes
			} catch (SQLException ex2) {
				Log.get().log(Level.SEVERE, "Rollback of createThread() failed: {0}", ex2);
			}

			try {
				this.conn.setAutoCommit(true); // and release locks
			} catch (SQLException ex2) {
				Log.get().log(Level.SEVERE, "setAutoCommit(true) of createThread() failed: {0}", ex2);
			}

			restartConnection(ex);
			createThread(article, bfile, media_type);
		}finally{
			this.restarts = 0; // Reset error count
		}
		return null;
	}
	
	public Article createReplayWeb(Article article, byte[] bfile, final String media_type)
			throws StorageBackendException {
		if (repeatCheck(article.getHash(), article.getPost_time()))
			return null;
		else
			return createReplay(article, bfile, media_type);
		
	}
	
	public Article createReplay(Article article, byte[] bfile, final String media_type)
			throws StorageBackendException {
		//TODO: Get replays  in thread and throw Exception if them too many 
		int id = 0;
		String groupName = article.getGroupName();
		
		try {
			this.conn.setAutoCommit(false); // start transaction

			// Generate id
			id = genId();
			String messageId = article.getMessageId();
			
			if (messageId == null){
				String mId_random = Integer.toHexString(id) + article.getPost_time();
				messageId = "<"+ mId_random + "@" + article.getMsgID_host() +">";
			}
			// insert article
			pstmtCreateReplay1.setInt(1, id);
			pstmtCreateReplay1.setInt(2, article.getThread_id());
			pstmtCreateReplay1.setString(3, messageId);
			pstmtCreateReplay1.setString(4, article.getMsgID_host());
			pstmtCreateReplay1.setInt(5, article.getHash());
			pstmtCreateReplay1.setString(6, article.getA_name());
			pstmtCreateReplay1.setString(7, article.getSubject());
			pstmtCreateReplay1.setString(8, article.getMessage());
			pstmtCreateReplay1.setLong(9, article.getPost_time());
			pstmtCreateReplay1.setString(10, article.getPath_header());
			pstmtCreateReplay1.execute();
			//update thread (post time, thread_id)
			pstmtCreateReplay2.setLong(1, article.getPost_time());
			pstmtCreateReplay2.setInt(2, article.getThread_id());
			pstmtCreateReplay2.execute();
			// transaction end
			
			// attachment saving
			if (bfile != null && media_type != null)
				attachmentSaving(id, groupName, media_type, bfile);

			this.conn.commit();
			this.conn.setAutoCommit(true);
			
			return new Article(article, id, messageId, media_type);	
		} catch (SQLException ex) {
			try {
				this.conn.rollback(); // Rollback changes
			} catch (SQLException ex2) {
				Log.get().log(Level.SEVERE, "Rollback of createReplay() failed: {0}", ex2);
			}

			try {
				this.conn.setAutoCommit(true); // and release locks
			} catch (SQLException ex2) {
				Log.get().log(Level.SEVERE, "setAutoCommit(true) of createReplay() failed: {0}", ex2);
			}

			restartConnection(ex);
			createReplay(article, bfile, media_type);
		}finally{
			this.restarts = 0; // Reset error count
		}
		return null;
	}

	public Map<Article,List<Article>> getThreads(int boardId, int boardPage, String boardName) throws StorageBackendException {
		ResultSet rs = null;
		ResultSet rs2 = null;
		Map<Article,List<Article>> map;
		
		try {
			//1) get threads
			int threads_per_page = Config.inst().get(Config.THREADS_PER_PAGE, 5);
			this.pstmtGetThreads1.setInt(1, boardId);
			this.pstmtGetThreads1.setInt(2, threads_per_page); //limit
			this.pstmtGetThreads1.setInt(3, boardPage * threads_per_page); //offset
			rs = this.pstmtGetThreads1.executeQuery();
			// Log.get().log(Level.INFO, "SQL Timeout Exception: check that your
			// database is working and restart application");
			// 1article.id, 2article.message_id, 3article.host,
			// 4article.a_name, 5article.subject,
			// 6article.message, 7article.post_time, 
			// 8attachment.file_path
			if(!rs.next())
				throw new StorageBackendException("getThreads():getThreads");
			else{
				map = new LinkedHashMap<Article,List<Article>>();
				do {
					int thread_id =rs.getInt(1);
					Article thread = new Article(thread_id, thread_id, rs.getString(2), rs.getString(3), rs.getString(4),
							rs.getString(5), rs.getString(6), rs.getLong(7), null, boardName, rs.getString(8), null); //thread

					int replays = 3;
					this.pstmtGetThreads2.setInt(1, rs.getInt(1));//thread_id
					this.pstmtGetThreads2.setInt(2, replays);//limit
					rs2 = this.pstmtGetThreads2.executeQuery();
					Deque<Article> rd = new ArrayDeque<Article>(); //reversed order
					while (rs2.next()) {
						if(rs2.getInt(1) == rs.getInt(1))
							continue;//skip thread, we need replays
						rd.push(new Article(rs2.getInt(1), thread_id, rs2.getString(2), rs2.getString(3), rs2.getString(4),
								rs2.getString(5), rs2.getString(6), rs2.getLong(7), null, boardName, rs2.getString(8), null)); //replay
					}
					map.put(thread, new ArrayList<Article>(rd));
					closeResultSet(rs2);
				} while (rs.next());
			}
		} catch (SQLException ex) {
			restartConnection(ex);
			return getThreads(boardId, boardPage, boardName);
		} finally {
			closeResultSet(rs);
			closeResultSet(rs2);
			this.restarts = 0; // Reset error count
		}

		return map;
	}
	
	
	/**
	 * Get one thread.
	 * 
	 * @param threadId
	 * @param boardName for article attachment string
	 * @return
	 * @throws StorageBackendException when restart fail
	 */
	public List<Article> getOneThread(int threadId, String boardName) throws StorageBackendException {
		ResultSet rs = null;
		List<Article> threads;
		try {
			this.pstmtGetOneThread.setInt(1, threadId);
			rs = this.pstmtGetOneThread.executeQuery();

			// Log.get().log(Level.INFO, "SQL Timeout Exception: check that your
			// database is working and restart application");
			//1article.id, 2article.message_id, 3article.message_id_host,
			//4article.a_name, 5article.subject, 6article.message, 7article.post_time,
			//8attachment.file_path
			if(!rs.next())
				throw new StorageBackendException("getOneThread():No such thread");
			else{
				threads = new ArrayList<Article>();
				do {
					threads.add(new Article(rs.getInt(1), threadId, rs.getString(2), rs.getString(3), rs.getString(4),
							rs.getString(5), rs.getString(6), rs.getLong(7), null, boardName, rs.getString(8), null));
				} while (rs.next());
			}
		} catch (SQLException ex) {
			restartConnection(ex);
			return getOneThread(threadId, boardName);
		} finally {
			closeResultSet(rs);
			this.restarts = 0; // Reset error count
		}

		return threads;
	}
	
	
	public int getReplaysCount(int threadId) throws StorageBackendException{
		ResultSet rs = null;
		int count = 0;
		try {
			this.pstmtGetReplaysCount.setInt(1, threadId);
			rs = this.pstmtGetReplaysCount.executeQuery();
			if(rs.next())
				count = rs.getInt(1);
		} catch (SQLException ex) {
			restartConnection(ex);
			return getReplaysCount(threadId);
		} finally {
			closeResultSet(rs);
			this.restarts = 0; // Reset error count
		}
		
		return count;
	}
	
	
	
	// ***********************************************************************************************
	
	protected void closeResultSet(ResultSet rs) {
		if (rs != null)
			try {
				rs.close();
			} catch (SQLException ex) {
				// Ignore exception
			}
	}	
	//									//
	//				Legacy				//
	//									//


	@Override
	public List<Integer> getArticleNumbers(int groupID, int start) throws StorageBackendException {
		
		ResultSet rs = null;
		List<Integer> ret= new ArrayList<Integer>();
		try {
			pstmtGetArticleNumbers.setInt(1, groupID);
			pstmtGetArticleNumbers.setInt(2, start);
			rs = pstmtGetArticleNumbers.executeQuery();
			while (rs.next())
				ret.add(rs.getInt(1));
		} catch (SQLException ex) {
			restartConnection(ex);
			return getArticleNumbers(groupID, start);
		} finally {
			closeResultSet(rs);
			this.restarts = 0; // Reset error count
		}
		return ret;
	}

	@Override
	public int getArticleCountGroup(int groupID) throws StorageBackendException {
		ResultSet rs = null;
		int ret = 0;
		try {
			pstmtGetArticleCountGroup.setInt(1, groupID);
			rs = pstmtGetArticleCountGroup.executeQuery();
			if (rs.next())
				ret = rs.getInt(1);
		} catch (SQLException ex) {
			restartConnection(ex);
			return getArticleCountGroup(groupID);
		} finally {
			closeResultSet(rs);
		}
		return ret;
	}

	
	@Override
	public Article getArticle(String messageId, Integer id) throws StorageBackendException {
		assert(messageId != null || id != null);
		ResultSet rs = null;
		Article art = null;
		try {
			String[] mId = new String[2];
			pstmtGetArticle.setString(1, messageId); //I don't knew how to setNull String
			
			if (id != null)
				pstmtGetArticle.setInt(2, id);
			else
				pstmtGetArticle.setNull(2, java.sql.Types.INTEGER);
			rs = pstmtGetArticle.executeQuery();
			//1id, 2thread_id, 3message_id, 4message_id_host, 5a_name, 6subject, 7message, 8post_time, 9path_header, 10group_id, 11file_path, 12media_type
			if (rs.next()){
				//String media_type = ;
				/*if (media_type != null){
					System.out.println("#"+media_type+"#");
					System.out.println("#"+rs.getString(3)+"#");
					media_type = media_type.trim();
				}*/
				art = new Article(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getString(4), 
						rs.getString(5), rs.getString(6), rs.getString(7), rs.getLong(8), 
						rs.getString(9), StorageManager.groups.getName(rs.getInt(10)), rs.getString(11), rs.getString(12));
			}
		} catch (SQLException ex) {
			restartConnection(ex);
			return getArticle(messageId, id);
		} finally {
			closeResultSet(rs);
			this.restarts = 0; // Reset error count
		}
		return art;
	}

	@Override
	public List<String> getNewArticleIDs(Group group, long date) throws StorageBackendException {		
		ResultSet rs = null;
		List<String> ret= new ArrayList<String>();
		try {
			pstmtGetNewArticleIDs.setInt(1, group.getInternalID());
			pstmtGetNewArticleIDs.setLong(2, date);
			rs = pstmtGetNewArticleIDs.executeQuery();
			while (rs.next())
				ret.add(rs.getString(1));
		} catch (SQLException ex) {
			restartConnection(ex);
			return getNewArticleIDs(group, date);
		} finally {
			closeResultSet(rs);
			this.restarts = 0; // Reset error count
		}
		return ret;
	}

	@Override
	public long getLastPostOfGroup(Group g) throws StorageBackendException {
		ResultSet rs = null;
		try {
			pstmtGetLastPostOfGroup.setInt(1, g.getInternalID());
			rs = pstmtGetLastPostOfGroup.executeQuery();
			if (rs.next()) {
				return rs.getLong(1);
			} else 
				return 0;
				
		} catch (SQLException ex) {
			restartConnection(ex);
			return getLastPostOfGroup(g);
		} finally {
			closeResultSet(rs);
			this.restarts = 0; // Reset error count
		}
	}

}
