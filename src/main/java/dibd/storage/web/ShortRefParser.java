/**
 * 
 */
package dibd.storage.web;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dibd.config.Config;
import dibd.daemon.NNTPConnection;
import dibd.storage.GroupsProvider.Group;
import dibd.storage.StorageBackendException;
import dibd.storage.StorageManager;
import dibd.storage.article.Article;
import dibd.util.Log;

/**
 * Was inside BoardThreadService
 * 
 * @author user
 *
 */
public class ShortRefParser {
	
		/**
		 * Prepare message of WEB to pass to database.
		 * Get shortRefs.
		 * Convert short links to global message-id if exist.
		 * 
		 * @param message
		 * @return new message
		 * @throws StorageBackendException 
		 */
		public static String shortRefParser(StorageWeb db, String message) throws StorageBackendException{
			if (message == null || message.isEmpty())
				return null;
			int fc = Config.inst().get(Config.ID_F_COUNT, 5);
			Matcher matcher = 
	        		Pattern.compile("(>>(([0-9a-fA-F]){2,"+fc+"}))").matcher(message);
			
			Set<String> done = new HashSet<>(); //protection from abuse
			while (matcher.find()) {
				String mg2 = matcher.group(2);
				if (! done.contains(mg2)){
					int id = Integer.parseInt(matcher.group(2),16);

					Article art = db.getArticleWeb(null, id);

					if (art != null)
						message = message.replace(matcher.group(1), art.getMessageId()); //replace all

					done.add(mg2);
				}
			}
			
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
				final String message){
			
			Map <String, WebRef> short_ref_messageId = new HashMap<>();
			
			if (message == null || message.isEmpty())
				return short_ref_messageId;
			
			Matcher matcher = 
					Pattern.compile(NNTPConnection.MESSAGE_ID_PATTERN).matcher(message); //diff			

			while (matcher.find()) {
				
				String mIdmatch = matcher.group();
				Article art;
				try {
					art = db.getArticleWeb(mIdmatch, null);  //diff
				} catch (StorageBackendException e) {
					art = null;
				}
				
				if (art != null)
					short_ref_messageId.put(mIdmatch,
							new WebRef(art.getThread_id(), art.getId()));  //diff
			}

			return short_ref_messageId;
		}
		
		
		/**
		 * Prepare NNTPCHAN message to pass to database.
		 * Convert NNTPCHAN truncated sha1 to global message-id if exist.
		 * >>7239a9807e56c0b4e2
		 * 3-18
		 * to <message-id>
		 * 
		 * Used in ReceivingService only.
		 * 
		 * Links working in whole group now(slow)
		 * 
		 * @param message
		 * @param group
		 * @return new message
		 * @throws StorageBackendException 
		 * @throws NoSuchAlgorithmException 
		 */
		public static String nntpchanLinks(String message, Group group) throws StorageBackendException {
			if (message == null || message.isEmpty())
				return null;
			try{
				Matcher matcher = 
						Pattern.compile("(>>(([0-9a-f]){3,18}))").matcher(message);

				//Map <String, String> short_ref_messageId = new HashMap<>();
				boolean found = false;
				Map <String, String> Sha1AndMID = null;
				while (matcher.find()) {

					if( ! found){
						found = true;
						//get message-id list from whole group
						Set<String> mIds = StorageManager.current().getNewArticleIDs(group, 0).keySet();
						//prepare map sha-1 => message-id
						Sha1AndMID = new HashMap<>(mIds.size());

						MessageDigest md = MessageDigest.getInstance("SHA-1");
						for(String mId : mIds){
							md.reset();
							md.update(mId.getBytes());
							Sha1AndMID.put(new BigInteger(1, md.digest()).toString(16).substring(0, 18), mId);
						}
					}

					//main work
					for(Entry<String, String> sha : Sha1AndMID.entrySet())
						if (sha.getKey().startsWith(matcher.group(2))){
							//replace all
							message = message.replaceFirst(matcher.group(1), sha.getValue()); //nntpchan link to message-id
							break;
						}
				}
			}catch (NoSuchAlgorithmException ex){
				Log.get().log(Level.WARNING, "No SHA-1 for damned nntpchan links", ex);
			}
			//return short_ref_messageId;
	        return message;
		}

}
