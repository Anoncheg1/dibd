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
import dibd.storage.article.Article;
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
	public Article getArticleWeb(String message_id, Integer id) throws StorageBackendException {
		StorageWeb db = null;
		Lock l = locks[0];
		l.lock();
		try{
			db = fixedStoragePool.remove();
			Article ret = db.getArticleWeb(message_id, id);
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
	public Article createReplayWeb(Article article, File file, String file_ct, String file_name)
			throws StorageBackendException {
		StorageWeb db = null;
		Lock l = locks[2];
		l.lock();
		try{
			db = fixedStoragePool.remove();
			Article ret = db.createReplayWeb(article, file, file_ct, file_name);
			return ret;
		}finally{
			if( db != null)
				fixedStoragePool.add(db);
			l.unlock();
		}
	}

	@Override
	public Article createThreadWeb(Article article, File file, String file_ct, String file_name)
			throws StorageBackendException {
		StorageWeb db = null;
		Lock l = locks[3];
		l.lock();
		try{
			db = fixedStoragePool.remove();
			Article ret = db.createThreadWeb(article, file, file_ct, file_name);
			return ret;
		}finally{
			if( db != null)
				fixedStoragePool.add(db);
			l.unlock();
		}
	}

	@Override
	public Map<ThRLeft<Article>, List<Article>> getThreads(int boardId, int boardPage, String boardName)
			throws StorageBackendException {
		StorageWeb db = null;
		Lock l = locks[4];
		l.lock();
		try{
			db = fixedStoragePool.remove();
			Map<ThRLeft<Article>, List<Article>> ret = db.getThreads(boardId, boardPage, boardName);
			return ret;
		}finally{
			if( db != null)
				fixedStoragePool.add(db);
			l.unlock();
		}
	}

	@Override
	public List<Article> getOneThread(int threadId, String boardName, int status) throws StorageBackendException {
		StorageWeb db = null;
		Lock l = locks[5];
		l.lock();
		try{
			db = fixedStoragePool.remove();
			List<Article> ret = db.getOneThread(threadId, boardName, status);
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
