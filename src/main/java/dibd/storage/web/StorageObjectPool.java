/**
 * 
 */
package dibd.storage.web;

import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import dibd.storage.StorageBackendException;
import dibd.storage.article.ArticleForPush;
import dibd.storage.article.ArticleOutput;
import dibd.storage.article.ArticleWebInput;
import dibd.storage.impl.JDBCDatabase;

/**
 * Replace StorageManager for dynamically spawn threads.
 * 
 * Adapter of database StorageWeb interface.
 * 
 * Much slower that StorageManager.current()
 * 
 * @author user
 *
 */
public class StorageObjectPool implements StorageWeb {

	//storage number 
	//number of parallel threads <= number of StorageWeb methods
	//because they are all with locks.
	public static final int QUEUE_SIZE = 9;//7+2 

	//must be thread-safe
	private final LinkedBlockingQueue<StorageWeb> fixedStoragePool = new LinkedBlockingQueue<StorageWeb>(QUEUE_SIZE);
	private final Lock[] locks = new Lock[QUEUE_SIZE];
	
	/**
	 * @throws SQLException
	 */
	public StorageObjectPool() throws SQLException {
		for(int i = 0; i<QUEUE_SIZE; i++){
			JDBCDatabase db = new JDBCDatabase();
			db.arise();
			fixedStoragePool.add(db);
			locks[i] = new ReentrantLock();
		}
	}

	@Override
	public ArticleOutput getArticleWeb(String message_id, Integer id) throws StorageBackendException {
		StorageWeb db = null;
		Lock l = locks[0];
		l.lock();
		try{
			db = fixedStoragePool.remove();
			ArticleOutput ret = db.getArticleWeb(message_id, id);
			return ret;
		}finally{
			if( db != null)
				fixedStoragePool.add(db);
			l.unlock();
		}
	}

	@Override
	public int getThreadsCountGroup(int groupId) throws StorageBackendException {
		StorageWeb db = null;
		Lock l = locks[1];
		l.lock();
		try{
			db = fixedStoragePool.remove();
			int ret = db.getThreadsCountGroup(groupId);
			return ret;
		}finally{
			if( db != null)
				fixedStoragePool.add(db);
			l.unlock();
		}
	}

	@Override
	public ArticleForPush createReplayWeb(ArticleWebInput article, File file)
			throws StorageBackendException {
		StorageWeb db = null;
		Lock l = locks[2];
		l.lock();
		try{
			db = fixedStoragePool.remove();
			ArticleForPush ret = db.createReplayWeb(article, file);
			return ret;
		}finally{
			if( db != null)
				fixedStoragePool.add(db);
			l.unlock();
		}
	}

	@Override
	public ArticleForPush createThreadWeb(ArticleWebInput article, File file)
			throws StorageBackendException {
		StorageWeb db = null;
		Lock l = locks[3];
		l.lock();
		try{
			db = fixedStoragePool.remove();
			ArticleForPush ret = db.createThreadWeb(article, file);
			return ret;
		}finally{
			if( db != null)
				fixedStoragePool.add(db);
			l.unlock();
		}
	}

	@Override
	public Map<ThRLeft<ArticleOutput>, List<ArticleOutput>> getThreads(int boardId, int boardPage, String boardName)
			throws StorageBackendException {
		StorageWeb db = null;
		Lock l = locks[4];
		l.lock();
		try{
			db = fixedStoragePool.remove();
			Map<ThRLeft<ArticleOutput>, List<ArticleOutput>> ret = db.getThreads(boardId, boardPage, boardName);
			return ret;
		}finally{
			if( db != null)
				fixedStoragePool.add(db);
			l.unlock();
		}
	}

	@Override
	public List<ArticleOutput> getOneThreadWeb(int threadId, String boardName) throws StorageBackendException {
		StorageWeb db = null;
		Lock l = locks[5];
		l.lock();
		try{
			db = fixedStoragePool.remove();
			List<ArticleOutput> ret = db.getOneThreadWeb(threadId, boardName);
			return ret;
		}finally{
			if( db != null)
				fixedStoragePool.add(db);
			l.unlock();
		}
	}

	@Override
	public int getReplaysCount(int threadId) throws StorageBackendException {
		StorageWeb db = null;
		Lock l = locks[6];
		l.lock();
		try{
			db = fixedStoragePool.remove();
			int ret = db.getReplaysCount(threadId);
			return ret;
		}finally{
			if( db != null)
				fixedStoragePool.add(db);
			l.unlock();
		}
	}
	
}
