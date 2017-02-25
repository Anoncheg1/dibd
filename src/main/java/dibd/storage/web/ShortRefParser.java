/**
 * 
 */
package dibd.storage.web;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dibd.config.Config;
import dibd.daemon.NNTPConnection;
import dibd.storage.StorageBackendException;
import dibd.storage.article.Article;

/**
 * Was inside BoardThreadService
 * 
 * @author user
 *
 */
public class ShortRefParser {
	
	//@return map (short_ref, message-id)
		//private Map <String, String> getShortRefs(String message){
		/**
		 * Prepare message to pass to database.
		 * Get shortRefs.
		 * Convert short links to global message-id if exist.
		 * 
		 * @param message
		 * @return new message
		 * @throws StorageBackendException 
		 */
		public static String getShortRefs(StorageWeb db, String message) throws StorageBackendException{
			if (message == null || message.isEmpty())
				return null;
			int fc = Config.inst().get(Config.ID_F_COUNT, 7);
			Matcher matcher = 
	        		Pattern.compile("(>>(([0-9a-fA-F]){2,"+fc+"}))").matcher(message);
			
			//Map <String, String> short_ref_messageId = new HashMap<>();
			while (matcher.find()) {
				int id = Integer.parseInt(matcher.group(2),16);

				Article art = db.getArticle(null, id);

				if (art != null)
					//short_ref_messageId.put(matcher.group(1), art.getMessageId());
					message = message.replace(matcher.group(1), art.getMessageId());
			}
			//return short_ref_messageId;
	        return message;
		}
		
		/**
		 * Prepare message from database to web.
		 * Get shortRefs.
		 * Search for global message-id in message then query database 
		 * for it's short reference.
		 * 
		 * @param message
		 * @return map (message-id, ThreadReplayRef)
		 * @throws StorageBackendException 
		 */
		public static Map <String, WebRef> getGlobalRefs(StorageWeb db,
				final String message) throws StorageBackendException{
			
			Map <String, WebRef> short_ref_messageId = new HashMap<>();
			
			if (message == null || message.isEmpty())
				return short_ref_messageId;
			
			Matcher matcher = 
					Pattern.compile(NNTPConnection.MESSAGE_ID_PATTERN).matcher(message); //diff			

			while (matcher.find()) {
				
				Article art = db.getArticle(matcher.group(), null); //diff
				
				if (art != null)
					short_ref_messageId.put(matcher.group(),
							new WebRef(art.getId(), art.getThread_id()));  //diff
			}

			return short_ref_messageId;
		}

}
