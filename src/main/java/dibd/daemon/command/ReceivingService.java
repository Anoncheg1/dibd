/**
 * 
 */
package dibd.daemon.command;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.Base64;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.mail.MessagingException;
import javax.mail.internet.ContentDisposition;
import javax.mail.internet.ContentType;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeUtility;

import dibd.config.Config;
import dibd.daemon.ChannelLineBuffers;
import dibd.daemon.NNTPConnection;
import dibd.daemon.NNTPInterface;
import dibd.feed.FeedManager;
import dibd.feed.PullDaemon;
import dibd.storage.Headers;
import dibd.storage.StorageBackendException;
import dibd.storage.StorageManager;
import dibd.storage.GroupsProvider.Group;
import dibd.storage.article.Article;
import dibd.storage.web.ShortRefParser;
import dibd.util.Log;

/**
 * 
 * No cachefile for POST.
 * 
 * @author user
 *
 */
class ReceivingService{
	
	// Size in bytes:
	private final int maxMessageSize = Config.inst().get(Config.MAX_MESSAGE_SIZE, 8192); //UTF-8 bytes
	//Very roughly, Base64-encoded binary data is equal to 1.37 times the original data size + headers
	private final long maxArticleSize = (long) (Config.inst().get(Config.MAX_ARTICLE_SIZE, 1) * 1024 * 1024 * 1.37); //MB
	private final ByteArrayOutputStream bufHead = new ByteArrayOutputStream(); //raw bytes UTF-8 by default
	//private final ByteArrayOutputStream bufBody = new ByteArrayOutputStream(); //raw bytes UTF-8 by default
	private File cacheFile = null;
	FileOutputStream cacheOs = null;
	
	//constructor
	private final String command;
	private final NNTPInterface conn;
	private final Charset charset;
	private final boolean pullMode; // for ArticlePuller
	private final String cMessageId;
	private final String host; //for debug ONLY
	
	public ReceivingService(String command, NNTPInterface conn, boolean pullMode, String cMessageId){
		this.command = command.toUpperCase();
		this.conn = conn;
		charset = conn.getCurrentCharset();
		this.pullMode = pullMode;
		this.cMessageId = cMessageId;
		host = conn.getHost();
	}

	
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

	/////    readingHeaders    /////
	private InternetHeaders headers = null;
	private String boundary = null; //maybe null here
	private ContentType contentType = null; //maybe null here
	private String[] from_raw = null; //may be null
	//private String[] subjectArr = null; //may be null
	private String subject;
	private String[] ref = null; //may be null (used for checkRef)
	
	private String date = null;
	private String path = null;
	private String messageId = null;//null if POST
	private String lastSender;
	private Group group;
	//SIDE EFFECT FUNCTION
	//bufHead
	/**
	 * 
	 * Return values: 
	 * null			- Continue.
	 * "ok"			- The end.
	 * any string	- error
	 * @param conn
	 * @param line
	 * @return
	 * @throws IOException
	 * @throws StorageBackendException
	 */
	String readingHeaders(String line, byte[] raw) throws IOException{
		lineHeadCount++;
		bufHead.write(raw);
		bufHead.write(NNTPConnection.NEWLINE.getBytes(charset)); //UTF-8
		//utf-8 subject without word encoding for nntpchan
		if (line.length() > 10 && line.substring(0,9).equalsIgnoreCase("subject: ")
				&& ! line.contains("=?UTF-")){ //&& line.length() <= 256+9
			int end = line.length() >= 256+9 ? 256+9 : line.length();  
			subject = line.substring(9, end).trim();
		}

		if (".".equals(line)) //end of the article
			return "No body for article.";//No body
		
		if ("".equals(line)) { //empty line headers-body separator
			// we finally met the blank line
			// separating headers from body

			try {
				// Parse the header using the InternetHeader class from
				// JavaMail API
				
				headers = new InternetHeaders(new ByteArrayInputStream(bufHead.toByteArray())); //we decode headers before parse.
				String[] contentType_raw = headers.getHeader(Headers.CONTENT_TYPE);//maybe null here
				from_raw = headers.getHeader(Headers.FROM); //may be null
				if (subject == null){
					String[] subjectArr = headers.getHeader(Headers.SUBJECT); //may be null
					subject = (subjectArr != null && !subjectArr[0].isEmpty()) ? trimZeros(decodeWord(MimeUtility.unfold(subjectArr[0]))) : null;
				}
				ref = headers.getHeader(Headers.REFERENCES); //may be null
				
				String[] groupHeader = headers.getHeader(Headers.NEWSGROUPS);
				String[] dateH = headers.getHeader(Headers.DATE);
				String[] pathH = headers.getHeader(Headers.PATH);
				String[] mId = headers.getHeader(Headers.MESSAGE_ID);
				
				if ( ! command.equals("POST")){
					if (dateH == null || pathH == null || mId == null){
						if( ! this.pullMode)
							Log.get().log(Level.WARNING, "{0} No date or path or messageId in headers from {1}", new Object[]{this.cMessageId, host});
						return "No date or path or messageId in headers.";//error
					}
					
					date = dateH[0];
					path = pathH[0];
					messageId = mId[0];
					lastSender = path.split("!")[0];
				}
							
				//checks reference if replay
				if(ref != null && !ref[0].isEmpty())
					if (! Headers.matchMsgId(ref[0])){
						if( ! this.pullMode)
							Log.get().log(Level.INFO, "{0} wrong reference {1} format in {2} from {3}", new Object[] {command, ref[0], this.cMessageId, host});
						return "Wrong format of reference in headers"; //error in header references
					}
					
				
				//check group
				if (groupHeader != null)
					group = StorageManager.groups.get(groupHeader[0].split(",")[0].trim());
				
				if (groupHeader == null || group == null || group.isDeleted())//check that we have such group
						return "No such news group.";//error
				
				
				
				//deep headers analysis (no need too deep - what if another error!)
				if (contentType_raw != null)
					contentType = new ContentType(contentType_raw[0]);

				if (contentType != null && contentType.getBaseType().equals("multipart/mixed")){ //multipart //rfc2046 MIME
					boundary = contentType.getParameter("boundary");
					if(boundary == null)
						return "Wrong miltipart Content-Type, no boundary";
				}

				
				
			} catch (MessagingException ex) {
				if( ! this.pullMode)
					Log.get().log(Level.INFO, ex.getLocalizedMessage(), ex);
				return "Can not parse Content-Type header.";//error in headers
			}
			
			///// end of analysis
			//reading body now
			//first save head to future cache.
			//createtmp
			cacheFile = File.createTempFile(cMessageId+"cachefile", "");
			cacheOs = new FileOutputStream(cacheFile);
			cacheOs.write(bufHead.toByteArray());
			return "ok";
		}//empty line
		
		
		//keep reading
		if (lineHeadCount > 20){
			Log.get().log(Level.SEVERE, "Headers is too large for {0} from host {1}", new Object[] {this.cMessageId, host});
			return "headers is too large.";
		}else
			return null;//continue
	}

	
	
	
	/////    readingBody    /////
	private int lineHeadCount = 0;
	private long bodySize = 0; //approximate
	
	private enum Multipart { Comment, MessagePart, AttachmentPart, AttachmentReaded};
	private Multipart mPart = Multipart.Comment;
	
	private enum MessagePts { Headers, Message};
	private MessagePts messagePart = MessagePts.Headers;
	
	private enum Attachment { Headers, Body};
	private Attachment attachPart = Attachment.Headers;
	
	
	//private String multiComment = null;
	private InternetHeaders multiMesHeaders;
	private StringBuilder messageB = new StringBuilder();	//for multi and not
	private InternetHeaders multiAttachHeaders;
	//private StringBuilder attachBody; //java heap overflow
	//saving attachment to file
	private FileOutputStream attachStream = null;
	private File attachFile  = null; //tmp file
	//SIDE EFFECT FUNCTION
	//bufBody
	/**
	 * 4 - "." reached. Wrong multipart format.
	 * 3 - too big. message was not read. multipart and not. "." not reachead.
	 * 2 - too big. multipart, message readed. "." not reachead.
	 * 1 - the end. success.
	 * 0 - continue
	 * 
	 * @param line
	 * @param raw
	 * @throws IOException
	 */
	int readingBody(String line, byte[] raw) throws IOException{
		
		if (! ".".equals(line)) {
			bodySize += raw.length; //approximate value
			
			//saving raw to bufBody 
			cacheOs.write(raw); //\r\n was already removed in ChannelLineBuffers and NNTPConnection
			if (raw.length < ChannelLineBuffers.BUFFER_SIZE)//we add new line if buffer was not full.
				cacheOs.write(NNTPConnection.NEWLINE.getBytes(charset)); //UTF-8
			
			
			
			//////////// multipart ////////////
			if (boundary != null){ //rfc2046 MIME
				
				switch (mPart) {
				case Comment:{
					if(line.startsWith("--") && line.equals("--"+boundary)){
						mPart = Multipart.MessagePart;
						multiMesHeaders = new InternetHeaders();
					}//else
						//multiComment+=line+" ";
					
					break;
				}
				case MessagePart:{
					if(line.startsWith("--") && line.equals("--"+boundary)){
						mPart = Multipart.AttachmentPart;
						//createtmp
						attachFile = StorageManager.nntpcache.createTMPfile(cMessageId +"attachmentone");
						attachStream = new FileOutputStream(attachFile); 
						multiAttachHeaders = new InternetHeaders();
						//attachBody = new StringBuilder();
						break;
					}
						
					if (messagePart == MessagePts.Headers){
						if(line.isEmpty())
							messagePart = MessagePts.Message;
						else
							multiMesHeaders.addHeaderLine(line); //no subject here //must contain only US-ASCII characters.
					}else{ //if(messagePart == MessagePts.Message){
						messageB.append(line);
						if(raw.length < ChannelLineBuffers.BUFFER_SIZE)
							messageB.append("\n");//stringbuilder.setlength to remove last \n
						//check message size
						if (messageB.length() > maxMessageSize){
							cacheOs.close();
							cacheFile.delete();
							return 3;
						}
							
					}
					
					break;
				}

				case AttachmentPart:{
					//We don't need more attachments for now.
					//That is why is it final part or not no matter.
					if(line.startsWith("--") && line.startsWith("--"+boundary)){ //+"--" 
						mPart = Multipart.AttachmentReaded; //other parts ignored.
						break;
					}
					
					if (attachPart == Attachment.Headers){
						if(line.isEmpty())
							attachPart = Attachment.Body;
						else
							multiAttachHeaders.addHeaderLine(line); //must contain only US-ASCII characters.
					}else{ //if(attachPart == Attachment.Body){
						attachStream.write(raw);
						//attachBody.append(line);
					}
					
						
					break;
				}
				default:
					break;
				
				}
				
				//multipart
				//check size of message
				if (bodySize > maxArticleSize){
					attachStream.close();
					attachFile.delete();
					cacheOs.close();
					cacheFile.delete();
					if (mPart == Multipart.AttachmentPart || mPart == Multipart.AttachmentReaded)
						return 2;//save only message in database.
					else
						return 3;//fail
					
				}
					
			
				
			}else{//////////// not multipart ////////////
				
					messageB.append(line).append("\n");//stringbuilder.setlength to remove last \n
			}
			
			//check message size
			if (messageB.length() > maxMessageSize){//error
				cacheOs.close();
				cacheFile.delete();
				return 3;//fail
			}
			
			
			return 0; //continue to read
			
		} else { //"." was reach
			
			cacheOs.flush();
			cacheOs.close();
			
			 //if body is empty?
			if ((boundary != null && mPart != Multipart.AttachmentReaded) || bodySize == 0){//error
				//cacheOs.close();//done
				cacheFile.delete();
				if (attachStream != null){
					attachStream.close();
					attachFile.delete();
				}
				return 4;//It is the end but it doesn't look like the end
			}
			
			if (attachStream != null)
				attachStream.close();
			
			return 1; //full success.
		}
		/*System.out.println("mheader:"+multiHeadMessage.getAllHeaders().hasMoreElements());
		System.out.println("mMessage:"+multiMessage.toString());
		if(attachBody != null)
			System.out.println("mAttachBody:"+attachBody.toString());*/
		
	}
	
	
	/**
	 * If return false if circle exist, true if messageB is new.
	 * 
	 * @param 
	 * @return
	 */
	boolean circleCheck(){
		// Circle check; note that Path can already contain the hostname
		// here
		String ourhost = Config.inst().get(Config.HOSTNAME, null);
		assert(ourhost != null);
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
				Log.get().log(Level.INFO, "{0}: sender is unknewn {1}, message-id {2}, host {3}",
						new Object[] {this.command, lastSender, messageId, host});
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
			Log.get().log(Level.INFO, "{0}: Last {1} sender {2}, is not in group {3}",
				    new Object[] {command, messageId, lastSender, group.getName()});
			return false;
		}
	}
	
	
	/**
	 * We detect if this sender is not in public list "peers of this groop" if such we will not public his messageB
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

	
	private Integer thread_id = null;
	/**
	 * Necessarily to call for IHAVE and TAKETHIS.
	 * 
	 * For replay:
	 * set this.thread_id
	 * Check if thread exist.
	 * If not we query for pull.
	 * 
	 * @return false if no such reference, true if we can proceed
	 * @throws StorageBackendException 
	 * @throws ParseException 
	 */
	boolean checkRef() throws StorageBackendException{
		//if ref equal mId we assume it is thread
		if ( ! command.equals("POST")){
			if (ref != null && !ref[0].isEmpty() && ! ref[0].equals(messageId)){ 

				Article art = StorageManager.current().getArticle(ref[0], null, 1); //get thread

				if (art != null){
					if (art.getId().intValue() == art.getThread_id().intValue()){ //check that ref is a thread
						this.thread_id = art.getThread_id(); //return true
					}else
						return false;
				}else{
					//request missed thread:
					PullDaemon.queueForPull(group, ref[0], messageId+" "+this.host+" "+path);
					return false; //"no such REFERENCE";
				}

			}
			
			
		}else{//POST
			if (ref != null && !ref[0].isEmpty()){
				Article art = StorageManager.current().getArticle(ref[0], null, 1); //get thread
				if (art != null){
					if (art.getId().intValue() == art.getThread_id().intValue()){ //check that ref is a thread
						this.thread_id = art.getThread_id(); //return true
					}else
						return false;
				}else
					return false; //"no such REFERENCE";
			}
			
		}
		
		return true;
	}

	/**
	 * @return null of message-id
	 */
	String getMessageId(){
		return messageId;
	}
	
	//utf-8 0 bytes appears at the end of message some times.
	private String trimZeros(String str) {
		int found = -1;
	    for( int i = str.length()-1 ; i >= 0;i--)
	    	if (str.charAt(i)== 0)
	    		found = i;
	    	else
	    		break;

	    if (found != -1){
	    	return str.substring(0, found);
	    }else
	    	return str;
	}
	

	/**
	 * Decode one tmp-file to another. First deleted, second returned.
	 * Base64
	 * 
	 * @param tf1
	 * @return
	 * @throws IOException
	 */
	private File decodeTmpFile( File tf1) throws IOException{
		InputStream i = null;
		InputStream is = null;
		BufferedInputStream isb = null;
		//second file
		//createtmp
		File attachFile2 = File.createTempFile(cMessageId+ "decodedattachment", "");//second tmp file with decoded data. will be moved later.
		FileOutputStream fos = new FileOutputStream(attachFile2);
		
		try{
			i = new FileInputStream(tf1);
			is = Base64.getDecoder().wrap(i);
			isb = new BufferedInputStream(is, 1024*512); //encoded to decoded
			int read = 0;
			byte[] bytes = new byte[1024*512];//write

			while ((read = isb.read(bytes)) != -1) {
				fos.write(bytes, 0, read);
			}
			
			fos.flush();
		}catch(IOException e){
			attachFile2.delete();
			throw e;
		}finally{
			if (i != null)
				i.close();
			if (is != null) //if first stream closed are wrappers close too?
				is.close();
			if (isb != null)
				isb.close();
			if (fos != null)
				fos.close();
		}
		tf1.delete();
		return attachFile2;
	}
	
	
	/**
	 * If no error it parse article, save and put to transfer.
	 * 
	 * from, subject, message are trimmed.
	 * 
	 * @param status 0 - ok, 1 - multifile not readed
	 * @return error message or null if success
	 * @throws StorageBackendException 
	 * @throws MessagingException 
	 * @throws ParseException 
	 * @throws IOException 
	 * @throws ParseException
	 * 
	 */
	String process(int status) throws StorageBackendException, MessagingException, ParseException, IOException{
		String message = null; //UTF-16
		
		String gfileCT = null; //guess Content-type for file

		ContentDisposition fCD = null;//file Content-Disposition with file name
		
		
		//   message, multipart or not   //
		
		if (messageB.length()>1){
			//we assume content_type.getBaseType().equals("text/plain")
			messageB.setLength(messageB.length()-1);//remove \n after loop

			String[] encoding;

			if (boundary != null)
				encoding = multiMesHeaders.getHeader(Headers.ENCODING);
			else
				encoding = headers.getHeader(Headers.ENCODING);
			if (encoding != null && encoding[0].equalsIgnoreCase("base64")){
				try{
					message = new String(Base64.getMimeDecoder().decode(this.messageB.toString()));
				}catch(IllegalArgumentException e){
					return "Base64 message in multipart can't be decoded or headers error";
				}
			}else
				message = messageB.toString();

			if (message.isEmpty())
				message = null;
			else
				message = trimZeros(message);
		}//else message = null;
		
		
		///  MULTIPART FILE  ///
		
		if (boundary != null){ //multipart //rfc2046 MIME
			
			//headers
			String[] ct = multiAttachHeaders.getHeader(Headers.CONTENT_TYPE);
			String[] cd = multiAttachHeaders.getHeader(Headers.CONTENT_DISP); //may be null
			String[] enc = multiAttachHeaders.getHeader(Headers.ENCODING);
			
			//ContentType
			if (ct == null) return "Multipart file Content-Type is not defined";
			ContentType fCT = new ContentType(ct[0]); //file attachment Content-Type header
			gfileCT = fCT.getBaseType();
			//Content-Disposition (may be null)
			if (cd != null)
				fCD = new ContentDisposition(cd[0]); //file attachment Content-Disposition header
			
			//file
			if (status == 0){ //if was read
				//Encoding
				if (enc == null) return "Multipart file Content-Transfer-Encoding is not defined";
				if(enc[0].equalsIgnoreCase("base64")) //base64
					this.attachFile = decodeTmpFile(this.attachFile); //attachFile now decoded
				else
					return "Wrong Content-Transfer-Encoding header in attachment";
			}
			
		}


		//*** SAVING MESSAGE ***
		//preparation
		String from = (from_raw != null && !from_raw[0].isEmpty()) ? trimZeros(decodeWord(from_raw[0])) : null; //decoded word
		//subject = (subjectArr != null && !subjectArr[0].isEmpty()) ? trimZeros(decodeWord(MimeUtility.unfold(subjectArr[0]))) : null;
		
		String file_name = null;
		if (fCD != null)
			file_name = fCD.getParameter("filename");
		//int s = subject == null ? 0 : subject.length();
		//from = from_raw[0].replaceAll("<.*>", "");

		// Create Replay or Thread
		Article art; //to save
		Article article; //to transfer
		
		String[] mId = null; //message-id two parts 0-id 1-sender

		if (!command.equals("POST")){
			mId = messageId.replaceAll("(<|>)", "").split("@");
			String ourhost = Config.inst().get(Config.HOSTNAME, null);
			if (mId[0].isEmpty() || mId[1].isEmpty())
				return "No message-id in headers"; //empty message-id. it is peer bug
			else if (mId[1].equals(ourhost)){ //message-id is mine
				//our old message of missing thread
				//we must check that this message is not a fake
				assert(path != null);
				String paths[] = path.split("!");
				//our host name may be only at the end of path
				for (int i = 0; i < paths.length-1; i++)
					if(paths[i].equalsIgnoreCase(ourhost)){
						Log.get().log(Level.SEVERE, "{0}: sender {1} FAKE MY ARTICLE {2} in group {3} with path {4}",
								new Object[]{this.command, host, messageId, group.getName(), path});
						return "Faking article detected. Path:"+path;
					}
			}
			
			//preparing raw data
			/*rawArticle = new byte[bufHead.size() + bufBody.size()];
			
			System.arraycopy(bufHead.toByteArray(), 0, rawArticle, 0, bufHead.size()); //remove trailing \r\n
			System.arraycopy(bufBody.toByteArray(), 0, rawArticle, bufHead.size(), bufBody.size()); //we have \r\n at the end in cache
			bufHead.close();
			bufBody.close();*/
		}


		////////////////////  SAVING  ////////////////////
		//1. cache save
		//2. attachment save to cache
		//3. attachment save to database if fail roll back
		//4. save thumbnail - Not critical.
		//5. article to database
		try{
			if (command.equals("POST")){
				assert(status == 0);
				if(thread_id != null){ //replay
					art = new Article(thread_id, from, subject, message, group);
					article = StorageManager.current().createReplay(art, this.attachFile, gfileCT, file_name);
				}else{ //thread
					art = new Article(null, from, subject, message, group);
					article = StorageManager.current().createThread(art, this.attachFile, gfileCT, file_name);
				}
				FeedManager.queueForPush(article); //send to peers
			}else{

				////   IHAVE, TAKETHIS    //////
				//status  1 - file was not readed.  0 - normal

				if(thread_id != null){ //replay
					//nntpchan links converter
					message = ShortRefParser.nntpchanLinks(message, group);
					art = new Article(thread_id, messageId, mId[1], from, subject, message,
							date, path.trim(), group.getName(), group.getInternalID(), status);

					if(status == 0){
						File fl = StorageManager.nntpcache.saveFile(group.getName(), messageId, this.cacheFile);
						try{
							article = StorageManager.current().createReplay(art, this.attachFile, gfileCT, file_name);
						}catch(StorageBackendException e){ //rollback cache
							StorageManager.nntpcache.delFile(fl);
							throw e;
						}

					}else{ //if (status == 1)//no need cache if status =1
						assert(this.attachFile == null); // that is how we understand that statys = 1
						article = StorageManager.current().createReplay(art, this.attachFile, gfileCT, file_name);
					}

				}else{ //thread
					//nntpchan links converter
					message = ShortRefParser.nntpchanLinks(message, group);
					art = new Article(null, messageId, mId[1], from, subject, message,
							date, path.trim(), group.getName(), group.getInternalID(), status);

					if(status == 0){
						File fl = StorageManager.nntpcache.saveFile(group.getName(), messageId, this.cacheFile);
						try{
							article = StorageManager.current().createThread(art, this.attachFile, gfileCT, file_name);
						}catch(StorageBackendException e){ //rollback cache
							StorageManager.nntpcache.delFile(fl);
							throw e;
						}
					}else{ //if (status == 1)//no need cache if status =1
						assert(this.attachFile == null); // that is how we understand that statys = 1
						article = StorageManager.current().createThread(art, this.attachFile, gfileCT, file_name);
					}

				}



				//3) Third check. Sender must be in group.
				if( ! group.getHosts().contains(mId[1])){
					Log.get().log(Level.SEVERE, "{0}: sender {1} have NEW SOURCE {2} in group {3}",
							new Object[]{this.command, host, mId[1], group.getName()});
					//FeedManager.lazyQueueForPush(art);

				}else if ( ! pullMode){
					if( status == 0){
						assert(article != null);
						//article.setRaw(rawArticle);
						Log.get().log(Level.INFO, "{0}: article {1} received and we push it to another another hosts",
								new Object[]{this.command, messageId});
						FeedManager.queueForPush(article); //send to peers

					}else
						Log.get().log(Level.INFO, "{0}: article {1} received. But attachment was too large for us.",
								new Object[]{this.command, messageId});
				}

			}
		}finally{
			if (this.attachFile != null && this.attachFile.exists()) //not happen - must be moved
				this.attachFile.delete(); //just for case
			
			//work for POST
			if (this.cacheFile != null && this.cacheFile.exists()) //not happen - must be moved
				this.cacheFile.delete(); //just for case
		}
		 
		return null; //success
	}
}
