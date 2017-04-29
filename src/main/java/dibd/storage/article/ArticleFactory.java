package dibd.storage.article;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.time.Instant;
import java.util.logging.Level;

import dibd.config.Config;
import dibd.storage.GroupsProvider.Group;
import dibd.storage.article.Article.Art;
import dibd.util.Log;

/**
 * Factory
 * 
 * Article - concrete product.
 * 
 * @author user
 *
 */
public class ArticleFactory {

	/**
	 * Generates 4bytes MD5 hash for check of repeat.
	 * used: groupId, subject, message(length < 10)
	 * 
	 * @param a
	 */
	private static void generateHash(Art a){
		//String randomString;
		MessageDigest md5;
		try {
			md5 = MessageDigest.getInstance("MD5");
			md5.reset();
			assert(a.groupId != 0);
			byte [] g = new byte[] {
					(byte)(a.groupId >>> 24),
					(byte)(a.groupId >>> 16),
					(byte)(a.groupId >>> 8),
					(byte)a.groupId};
			md5.update(g);
			if ( a.subject != null)
				md5.update(a.subject.getBytes(Charset.availableCharsets().get("UTF-16")));
			if ( a.message != null)
			md5.update((a.message.length() > 10 ? a.message.substring(0, 10) : a.message )
					.getBytes(Charset.availableCharsets().get("UTF-16")));
			byte[] result = md5.digest();
			a.hash=0;
			for (int i = 0; i < result.length/8; i++) { //MD5 16byte*8=128bits  16/4=4bytes - int
				//hexString.append(Integer.toHexString(0xFF & result[i]));
				a.hash += (0xFF & result[i]) << i*8;
			}
			//randomString = hexString.toString();
		} catch (NoSuchAlgorithmException ex) {
			Log.get().log(Level.SEVERE, ex.getLocalizedMessage(), ex);
			//randomString = UUID.randomUUID().toString();
		}
		//String msgID = "<" + randomString + post_time*128 + "@"
		//        + msgID_origin + ">";
	}
	
	
	private static String escapeString(String str) {
        String nstr = str.replace("\r", "");
        nstr = nstr.replace('\n', ' ');
        nstr = nstr.replace('\t', ' ');
        return nstr.trim();
    }
	
	
	/**
	 * Creates a new Article object. www input, NNTP POST.
	 * No message_id
	 * 
	 * @param thread_id - null if thread
	 * @param a_name	may be null
	 * @param subject	may be null
	 * @param message	may be null
	 * @param group
	 * @param fileName may be null
	 * @param contentType may be null
	 * @return never null
	 */
	public static ArticleWebInput crAWebInput(Integer thread_id, String a_name, String subject, String message, Group group, String fileName, String contentType){
		Art a = new Art();
		
		if (a_name != null)
			if (a_name.isEmpty())
				a_name = null;
			else
				a.a_name = escapeString(a_name);
		
		if (subject != null)
			if (subject.isEmpty())
				subject = null;
			else
				a.subject = escapeString(subject);
		
		if (message != null)
			if (message.isEmpty())
				message = null;
			else
				message = message.replaceAll("\\s+$", ""); //we are more accurate with message. UTF-8 0 byte may appear..
		
		a.thread_id = thread_id;
		a.message = message;
		a.groupId = group.getInternalID(); //for input
		a.groupName = group.getName();
		Instant nowEpoch = Instant.now();        
		a.post_time = nowEpoch.getEpochSecond();
		a.msgID_host = Config.inst().get(Config.HOSTNAME, null);
		
		//TODO:check that .xxx is write and contentType == null with filename 
		a.fileName = fileName; //TODO make it .xxx - for very rare formats
		a.fileFormat = contentType; //if not null file exist
		
		generateHash(a);
		
		
		return new Article(a);
	}
	
	
	/**
	 * Creates a new Article object NNTP IHAVE, TAKETHIS input.
	 * Group id is required!
	 * @param thread_id 1
	 * @param messageId 2
	 * @param msgID_host 3
	 * @param a_name 4
	 * @param subject 5
	 * @param message 6
	 * @param date 7
	 * @param path_header 8
	 * @param groupName 9
	 * @param groupId
	 * @param status 10
	 * @param fileName 11 may be null
	 * @param contentType 12 may be null
	 * @return never null
	 */
	public static ArticleInput crAInput(Integer thread_id, String messageId, String msgID_host, String a_name, String subject, String message,
			long date, String path_header, String groupName, int groupId, int status, String fileName, String contentType){
		Art a = new Art();
		
		a.thread_id = thread_id; //if null = new thread
		//a.msgID_random = msgID_random;
		assert( messageId != null);
		a.messageId = messageId;
		assert( msgID_host != null);
		a.msgID_host = msgID_host;
		a.a_name = a_name;
		a.subject = subject;
		a.message = message;
		a.post_time = date;
		assert( path_header != null); //TODO: allow any at overchan
		a.path_header = path_header;
		assert( groupName != null);
		a.groupName = groupName;
		a.groupId = groupId;
		a.status = status; //null nothing, 1 - file too large
		a.fileName = fileName; //TODO make it .xxx - for very rare formats
		a.fileFormat = contentType; //if not null file exist

		generateHash(a);
		
		return new Article(a);
	}
	
	/**
	 * For feeding after storage. Namely in JDBCDatabase implementation.
	 * Used when return after thread or replay stored.
	 *  
	 * @param article
	 * @param id
	 * @param messageId
	 * @param fileName
	 * @param contentType
	 * @return never null
	 */
	public static ArticleForPush crAForPush(ArticleInput article, int id, String messageId, String fileName, String contentType) {
		Art a = ((Article) article).a;
		
		
		a.id = id;
		assert (messageId != null);	//IMPORTANT
		a.messageId = messageId;

		a.fileName = fileName; //TODO make it .xxx - for very rare formats
		a.fileFormat = contentType; //if not null file exist
		
		return new Article(a);
	}
	
	
	/**
	 * Creates a new Article object OUTPUT. groupId no need, hash no need.
	 * Only this constructor may be used with buildNNTPMessage and JDBCDatabase.createThread,replay output.
	 * Output
	 * @param id 1
	 * @param thread_id 2
	 * @param messageId 3
	 * @param msgID_host 4
	 * @param a_name 5
	 * @param subject 6
	 * @param message 7
	 * @param post_time 8
	 * @param path_header 9
	 * @param groupName 10
	 * @param fileName 11
	 * @param fileFormat 12
	 * @param status 13
	 * @throws ParseException 
	 */
	public static ArticleOutput crAOutput(int id, int thread_id, String messageId, String msgID_host, String a_name, 
			String subject, String message, long post_time, String path_header, String groupName, String fileName, String fileFormat, int status){
		
		Art a = new Art();
		a.id = id;
		a.thread_id = thread_id;
		assert( messageId != null);
		a.messageId = messageId;
		assert( msgID_host != null);
		a.msgID_host = msgID_host;
		a.a_name = a_name;
		a.subject = subject;
		a.message = message;
		a.post_time = post_time;
		//assert( path_header != null); should assert at input
		a.path_header = path_header;
		assert( groupName != null);
		a.groupName = groupName;
		a.fileName = fileName; //TODO make it .xxx - for very rare formats
		a.fileFormat = fileFormat; //if not null file exist
		a.status = status;
		return new Article(a);
	}
	
	//JDBC indexLastArts
	/**
	 * @param groupName 1
	 * @param id 2
	 * @param thread_id 3
	 * @param subject 4
	 * @param message 5
	 * @param fileName 6
	 * @param fileFormat 7
	 * @param status 8
	 * @return never null
	 */
	public static ArticleForOverview crAForOverview(String groupName, int id, int thread_id, String subject, String message, String fileName, String fileFormat, int status){
		Art a = new Art();
		assert( groupName != null);
		a.groupName = groupName;
		a.id = id;
		a.thread_id = thread_id;
		a.subject = subject;
		a.message = message;
		a.fileName = fileName; //TODO make it .xxx - for very rare formats
		a.fileFormat = fileFormat; //if not null file exist
		a.status = status;
		return new Article(a);
	}
	
	
}
