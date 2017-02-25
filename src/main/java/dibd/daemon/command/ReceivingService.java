/**
 * 
 */
package dibd.daemon.command;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.Base64;
import java.util.Enumeration;
import java.util.Scanner;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.mail.Header;
import javax.mail.MessagingException;
import javax.mail.internet.ContentType;
import javax.mail.internet.HeaderTokenizer;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeUtility;

import dibd.config.Config;
import dibd.daemon.ChannelLineBuffers;
import dibd.daemon.NNTPConnection;
import dibd.daemon.NNTPInterface;
import dibd.feed.PullDaemon;
import dibd.feed.PushDaemon;
import dibd.storage.Headers;
import dibd.storage.StorageBackendException;
import dibd.storage.StorageManager;
import dibd.storage.GroupsProvider.Group;
import dibd.storage.article.Article;
import dibd.util.Log;

/**
 * @author user
 *TODO://make it stream like
 */
class ReceivingService{


	private int lineCount = 0;
	private int lineHeadCount = 0;
	private long bodySize = 0;
	private InternetHeaders headers = null;
	// Size in bytes:
	private final long maxBodySize = Config.inst().get(Config.ARTICLE_MAXSIZE, 100) * 1024L * 1024L; //Mb
	private final ByteArrayOutputStream bufHead = new ByteArrayOutputStream(); //raw bytes UTF-8 by default
	private final ByteArrayOutputStream bufBody = new ByteArrayOutputStream(); //raw bytes UTF-8 by default
	//private StringBuilder strHead = new StringBuilder();
	
	private final String command;
	private final NNTPInterface conn;

	public ReceivingService(String command, NNTPInterface conn){
		this.command = command.toUpperCase();
		this.conn = conn;
	}

	//headers
	private String[] contentType = null;//maybe null here
	private String[] from_raw = null; //may be null
	//groupNames[]
	private String[] subjectArr = null; //may be null
	private String[] ref = null; //may be null (used for checkRef)
	Integer thread_id = null;
	private String date = null;
	private String path = null;
	private String messageId = null;//null if POST
//	private String[] groupHeader = null;



	private String lastSender;
	private Group group;
	
	private String host; //for debug ONLY

	/**
	 * Decode word-encoded string if it is required
	 * 
	 * {@link} https://stackoverflow.com/questions/23044412/mimeutility-decode-doesnt-work-for-every-encoded-text
	 * @param word-encoded string or just string (must be not null)
	 * @return decoded string or just string
	 * @throws UnsupportedEncodingException 
	 */
	private String decodeWord(String s) throws UnsupportedEncodingException{
		//final String ENCODED_PART_REGEX_PATTERN="=\\?([^?]+)\\?([^?]+)\\?([^?]+)\\?=";
		final String ENCODED_PART_REGEX_PATTERN="(.*?)=\\?(.+?)\\?(\\w)\\?(.+?)\\?="; //org.apache.james.mime4j
		Pattern pattern = Pattern.compile(ENCODED_PART_REGEX_PATTERN);
		Matcher m=pattern.matcher(s);

	    if(m.find()){
				return MimeUtility.decodeText(s);
	    }
	    //else
		return s;
	}
	
	//SIDE EFFECT FUNCTION
	//strHead
	//headers
	/**
	 * 
	 * Return values: 
	 * 0 - Continue.
	 * The end of headers reached:
	 * 1 - error. 500 sended.
	 * 2 - Success. ReadingBody now.
	 * 3 - No body.
	 * 4 - No such group.
	 * 5 - lineHeadCount > 20
	 * @param conn
	 * @param line
	 * @return
	 * @throws IOException
	 * @throws StorageBackendException
	 */
	int readingHeaders(String line, byte[] raw) throws IOException, StorageBackendException {
		lineHeadCount++;
		bufHead.write(raw);
		bufHead.write(NNTPConnection.NEWLINE.getBytes(conn.getCurrentCharset())); //UTF-8
		//strHead.append(line);
		//strHead.append(NNTPConnection.NEWLINE);

		if ("".equals(line) || ".".equals(line)) {
			// we finally met the blank line
			// separating headers from body

			try {
				// Parse the header using the InternetHeader class from
				// JavaMail API
				
				headers = new InternetHeaders(new ByteArrayInputStream(bufHead.toByteArray())); //we decode headers before parse.
				contentType = headers.getHeader(Headers.CONTENT_TYPE);//maybe null here
				from_raw = headers.getHeader(Headers.FROM); //may be null
				subjectArr = headers.getHeader(Headers.SUBJECT); //may be null
				ref = headers.getHeader(Headers.REFERENCES); //may be null
				String[] groupHeader = headers.getHeader(Headers.NEWSGROUPS);
				String[] dateH = headers.getHeader(Headers.DATE);
				String[] pathH = headers.getHeader(Headers.PATH);
				String[] mId = headers.getHeader(Headers.MESSAGE_ID);
				
				//System.out.println(headers.getHeader(Headers.MESSAGE_ID));
				if (command != "POST"){
					if (dateH == null || pathH == null || mId == null)
						return 1;//error to recognize headers
					
					date = dateH[0];
					path = pathH[0];
					messageId = mId[0];
					lastSender = path.split("!")[0];
				}
				if (groupHeader == null)
					return 4;//No such group.
				else{
					group = StorageManager.groups.get(groupHeader[0].split(",")[0].trim());
					if (group == null || group.isDeleted())//check that we have such group
						return 4;//No such group.
				}
			} catch (MessagingException ex) {
				Log.get().log(Level.INFO, ex.getLocalizedMessage(), ex);
				return 1;//error in headers
			}
			
			if (".".equals(line))
				// Post an article without body
				return 3;//No body
			else{ //OK it is - "" empty line headers-body separator
				host = conn.getHost();
				return 2;//reading body now
			}
			
			

		}
		if (lineHeadCount > 20)
			return 5;
		return 0;//continue
	}

	//SIDE EFFECT FUNCTION
	//bodySize
	//lineCount
	//headers
	//bufBody
	/**
	 * If return value is 1 we can post.
	 * 2 - no body
	 * 3 - body is too big
	 * 0 - continue
	 * 
	 * @param conn
	 * @param line
	 * @param raw
	 * @throws IOException
	 * @throws StorageBackendException
	 */
	int readingBody(String line, byte[] raw) throws IOException, StorageBackendException {
		
		if (".".equals(line)) {
			// Set some headers needed for Over command
			headers.setHeader(Headers.LINES, Integer.toString(lineCount));
			headers.setHeader(Headers.BYTES, Long.toString(bodySize));
			bodySize -= 1;

			return 1;
		} else {
			bodySize += line.length()+1;
			lineCount++;
				
			bufBody.write(raw); //\r\n was already removed in ChannelLineBuffers and NNTPConnection
			if (raw.length < ChannelLineBuffers.BUFFER_SIZE)//we add new line if buffer was not full.
				bufBody.write(NNTPConnection.NEWLINE.getBytes(conn.getCurrentCharset())); //UTF-8
			
			if(bodySize == 0) //no body
				return 2;
			else if (bodySize > maxBodySize) 
				return 3;
			
		}
		return 0;
	}
	
	
	/**
	 * If return false if circle exist, true if message is new.
	 * 
	 * @param 
	 * @return
	 */
	boolean circleCheck(){
		// Circle check; note that Path can already contain the hostname
		// here
		String ourhost = Config.inst().get(Config.HOSTNAME, null);
		assert(path != null);
		if (path.indexOf(ourhost + "!", 1) > 0) { //except first occurrence, 
			Log.get().log(Level.INFO, "{0}: {1} Circle detected for host {2}",
					new Object[] {this.command, this.messageId, this.host });
			return false;
		}
		return true;
	}

	/**
	 * Is sender (Path header) in subscription
	 * @return true if everything is ok
	 */
	//We have TLS for authentication.
	boolean  checkSender1(){
		assert(this.lastSender != null);
		assert(this.messageId != null);
		if(!this.conn.isTLSenabled()){ //we check if TLS is not enabled
			//must we check first sender by socket connection?
			if (StorageManager.peers.has(lastSender)){ //1) first 
				return true;
			}else{
				//			Log.get().log(Level.INFO, () ->
				//		String.format(this.command+" sender is unknewn {%s}, message-id {%s)", lastSender, headers.getHeader(Headers.MESSAGE_ID)[0]));
				Log.get().log(Level.INFO, "{0}: sender is unknewn {1}, message-id {2}",
						new Object[] {this.command, lastSender, messageId});
				return false;
			}
		}else
			return true;
		
	}


	/**
	 * Is sender (Path header) in peers of group of his article
	 * @return true if everything is ok
	 */
	boolean checkSender2(){
		assert(group != null);
		if(group.getHosts().contains(lastSender))
			return true;
		else{
			Log.get().log(Level.INFO, "{0}: {1} sender {2}, is not in group {3}",
				    new Object[] {command, messageId, lastSender, group.getName()});
			return false;
		}
	}
	
	
	/**
	 * We detect if this sender is not in public list "peers of this groop" if such we will not public his message
	 * AND WILL BE WATCHING!
	 * @return true if everything is ok
	 */
	/*
	boolean checkSender3(){
		assert(group != null);
		if(group.getHosts().contains(mId[1])){
			return true;
		}else{
			//System.out.println(group.getHosts());
			Log.get().warning("NEW SENDER IN GROUP:" + group.getName()+ " PATH:"+path[0]+" MID:"+ messageId[0]);
			return false;
		}	
	}//see at the bottom of process() method.*/

	/**
	 * Check if thread exist if article is replay.
	 * If not exist we query for pull.
	 * 
	 * @return true if everything is ok
	 * @throws StorageBackendException 
	 * @throws ParseException 
	 */
	boolean checkRef() throws StorageBackendException{
		if (ref != null){ 
			if(!ref[0].isEmpty() && !ref[0].equals(messageId) && Headers.matchMsgId(messageId)){
				Article art = StorageManager.current().getArticle(ref[0], null);
				if (art != null)
					this.thread_id = art.getThread_id();
				else{
					try {
						PullDaemon.queueForPush(group, Headers.ParseRawDate(date));//request missed thread:
					} catch (ParseException e) {
						Log.get().log(Level.INFO, "{0}: {1} can not parse date {2}",
								new Object[]{this.command, messageId, date});
					}
					return false; //"no such REFERENCE";
				}
					
			}
		}
		
		return true;
		
	}

	/**
	 * @return null of message-id
	 */
	String getMessageId(){
		if (messageId != null)
			return messageId;
		else
			return "null";
	}
	
	
	/**
	 * If no error it parse article, save and put to transfer.
	 * 
	 * @param conn
	 * @return error message or null if success
	 * @throws StorageBackendException 
	 * @throws MessagingException 
	 * @throws ParseException 
	 * @throws IOException 
	 * @throws ParseException 
	 * 
	 * 
	 */
	String process(Charset charset) throws StorageBackendException, MessagingException, ParseException, IOException {
		// headers that we need

		String message = null; //UTF-16
		byte[] file = null;
		String mimeType = null;
		ContentType ct = null;
		if (contentType != null)
			ct = new ContentType(contentType[0]);

		//*** MIME MESSAGE PARSING ***
		if (contentType != null && ct.getBaseType().equals("multipart/mixed")){ //multipart //rfc2046 MIME
			String boundary = ct.getParameter("boundary"); 

			if(boundary == null)
				return "Wrong Content-Type, no boundary";

			String bodys = bufBody.toString(charset.name());
			String[] parts = bodys.split("--"+boundary);
			if(parts.length < 4)
				return "MIME multipart parts is less then 3";

			//parts[0]-empty [1]-headers+message [2]-headers+file [3]-empty "--"
			// HEADERS AND MESSAGE
			String[] headers_and_message = new String[2];
			int i = parts[1].indexOf("\r\n\r\n"); //empty line

			headers_and_message[0] = parts[1].substring(2, i);
			headers_and_message[1] = parts[1].substring(i+4,parts[1].length()-2);

			InternetHeaders mHeaders = new InternetHeaders(
					new ByteArrayInputStream(headers_and_message[0].trim().getBytes()));
			String[] encoding = mHeaders.getHeader(Headers.ENCODING);
			if (encoding != null && encoding[0].equalsIgnoreCase("base64")){
					try{
						message = new String(Base64.getMimeDecoder().decode(headers_and_message[1]));
					}catch(IllegalArgumentException e){
						return "Base64 message in multipart can't be decoded or headers error";
					}
			}else
				message = headers_and_message[1];

			// HEADERS AND FILE
			String[] headers_and_file = new String [2];
			int ind = parts[2].indexOf("\r\n\r\n"); //empty line

			headers_and_file[0] = parts[2].substring(0, ind);
			headers_and_file[1] = parts[2].substring(ind+4); //trailing \r\n left
			
			InternetHeaders fHeaders = new InternetHeaders(
					new ByteArrayInputStream(headers_and_file[0].trim().getBytes()));
			ContentType fCT = new ContentType(fHeaders.getHeader(Headers.CONTENT_TYPE)[0]); //file attachment Content-Type header
			if(fHeaders.getHeader(Headers.ENCODING)[0].equalsIgnoreCase("base64")){ //base64
				//System.out.println(messageId[0]+" file size:"+ " fCT START"+fCT.getPrimaryType()+"END"+fCT.getPrimaryType().equalsIgnoreCase("image"));
				if(fCT.getPrimaryType().equalsIgnoreCase("image")){ //image support only
					byte[] fileB64 = headers_and_file[1].replaceAll("\r?\n", "").getBytes();
					file = Base64.getDecoder().decode(fileB64);
					

					InputStream is = new BufferedInputStream(new ByteArrayInputStream(file));
					mimeType = URLConnection.guessContentTypeFromStream(is);
					
					if(!fCT.getBaseType().equals(mimeType)){//Detected Content-Type != Content-Type in header
						file = null;
						mimeType = null;
						Log.get().log(Level.INFO, "{0}: {1} Unknewn file type {2} or {3} from {4}",
								 new Object[] {command, messageId, fCT.getBaseType(), mimeType, lastSender});
					}
					
				}else
					Log.get().log(Level.INFO, "{0}: {1} Not image file type {2} or {3} from {4}",
							 new Object[] {command, messageId, fCT.getBaseType(), mimeType, lastSender});
				
			}else
				return "Wrong Content-Transfer-Encoding header of body";

		}else{ //non-multipart,  we don't care about Content-Type anymore
			//if( ct.getBaseType().equals("text/plain"))

			String[] encoding = headers.getHeader(Headers.ENCODING);
			if (encoding != null && encoding[0].equalsIgnoreCase("base64")){
				try{
					message = new String(Base64.getMimeDecoder().decode(bufBody.toByteArray()), charset);
				}catch(IllegalArgumentException e){
					return "Base64 message non-multipart can't be decoded or headers error";
				}
			}else{ //Content-Transfer-Encoding = null or anything else
				message = bufBody.toString(charset.name());
				message = message.substring(0,message.length()-2);//removeing last \r\n
			}

		}
		bufBody.close();


		//*** SAVING MESSAGE ***
		//preparation
		String from = (from_raw != null && !from_raw[0].isEmpty()) ? decodeWord(from_raw[0]) : null; //decoded word
		String subject = (subjectArr != null && !subjectArr[0].isEmpty()) ? decodeWord(MimeUtility.unfold(subjectArr[0])) : null;
		//int s = subject == null ? 0 : subject.length();
		//from = from_raw[0].replaceAll("<.*>", "");

		// Create Replay or Thread
		Article art;
		Article article;
		int f = file == null ? 0 : file.length;
		
		String[] mId = null; //message-id two parts 0-id 1-sender
		if (!command.equals("POST")){
			mId = messageId.replaceAll("(<|>)", "").split("@");
			String ourhost = Config.inst().get(Config.HOSTNAME, null);
			if (mId[0].isEmpty() || mId[1].isEmpty())
				return "No message-id in headers"; //empty message-id. it is peer bug
			else if (mId[1].equals(ourhost)){ //message-id is mine
				//my old message
				//we must check that this message is not a fake
				assert(path != null);
				String paths[] = path.split("!");
				//our host name may be only at the end of path
				for (int i = 0; i < paths.length-1; i++)
					if(paths[i].equalsIgnoreCase(ourhost)){
						Log.get().log(Level.SEVERE, "{0}: sender {1} FAKE MY MESSAGE {2} in group {3} with path {4}",
								new Object[]{this.command, host, messageId, group.getName(), path});
						return "Faking message detected. Path:"+path;
					}
			}
			
		}


		if(thread_id != null){ //replay
			//Check for too small message
			/*if (message.length() == 0 && ( s == 0 || f == 0  )) {
				conn.println("500 too short message. See minimal requrements for message content.");
				return false;
			}*/ //for now we accept null message
			//System.out.println("a: "+thread_id+" "+mId[0]+" "+mId[1]+" "+from+" "+subjectT+" "+message+" "
			//	+date[0]+" "+path[0]+" "+groupHeader[0]+" "+group.getInternalID());
			if (command.equals("POST"))
				art = new Article(thread_id, from, subject, message, group.getInternalID(), group.getName());
			else
				art = new Article(thread_id, messageId, mId[1], from, subject, message,
						date, path, group.getName(), group.getInternalID());
			article = StorageManager.current().createReplay(art, file, mimeType);

		}else{ //thread
			//Check for too small message
			/*if ((message.length() == 0 && ( s == 0 || f == 0)) 
					|| (s == 0 && f == 0)) {
				conn.println("500 too short message. See minimal requrements for message content.");
				return false;
			}*/ //for now we accept null message
			//System.out.println(message);
			if (command.equals("POST"))
				art = new Article(null, from, subject, message, group.getInternalID(), group.getName());
			else
				art = new Article(null, messageId, mId[1], from, subject, message,
						date, path, group.getName(), group.getInternalID());
			article = StorageManager.current().createThread(art, file, mimeType);
		}

		//conn.println("441 newsgroup not found or configuration error");
		if (command == "POST")
			PushDaemon.queueForPush(article); //send to peers
		else{
			//save to NNTP Cache
			byte[] rawArticle = new byte[bufHead.size() + bufBody.size()];
			//System.out.println("1#"+new String(bufHead.toByteArray())+"#");
			//System.out.println("2#"+new String(bufBody.toByteArray())+"#");
			//System.out.println("bufHead.size "+bufHead.size());
			//System.out.println("bufBody.size "+bufBody.size());
			//System.out.println("rawArticle "+rawArticle.length);
			System.arraycopy(bufHead.toByteArray(), 0, rawArticle, 0, bufHead.size()); //remove trailing \r\n
			System.arraycopy(bufBody.toByteArray(), 0, rawArticle, bufHead.size(), bufBody.size()); //we have \r\n at the end in cache
			StorageManager.nntpcache.saveFile(group.getName(), messageId, rawArticle);
			
			//3) Third check. Sender must be in group.
			if(group.getHosts().contains(mId[1])){
				article.setRaw(rawArticle);
				PushDaemon.queueForPush(article); //send to peers
				Log.get().log(Level.FINE, "{0}: article {1} received and it is {2} that we can send it to another hosts",
						new Object[]{this.command, messageId, group.getHosts().contains(mId[1])});
			}else{
				Log.get().log(Level.SEVERE, "{0}: sender {1} have NEW SOURCE {2} in group {3}",
						new Object[]{this.command, host, mId[1], group.getName()});
				//FeedManager.lazyQueueForPush(art);
			}
		} 
		return null; //success
	}

}
