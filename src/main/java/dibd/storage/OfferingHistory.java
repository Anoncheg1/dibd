package dibd.storage;

import java.util.ArrayDeque;

/**
 * Keep little history for recent posts.
 * We need it because receiving IHAVE or TAKETHIS may take minutes.
 * Used in CHECK and IHAVE. takethis use check.
 * 
 * @author user
 *
 */
public class OfferingHistory {
	private static int AMOUNT = 20;
	private ArrayDeque<String> callhistory = new ArrayDeque<>(AMOUNT); //messageIds

	public OfferingHistory() {
		// TODO Auto-generated constructor stub
	}
	
	/**
	 * Check articles with any status.
	 * 
	 * @param messageId
	 * @return
	 * @throws StorageBackendException
	 */
	public synchronized boolean contains(String messageId) throws StorageBackendException{
		if(callhistory.contains(messageId) || 
				StorageManager.current().getArticle(messageId, null, 99) != null){
			
			return true;
		}else{
			callhistory.add(messageId);
			if (callhistory.size() >= AMOUNT)
				callhistory.removeFirst();
			return false;
		}
	}
}
