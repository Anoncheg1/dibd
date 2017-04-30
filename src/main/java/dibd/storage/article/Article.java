package dibd.storage.article;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.Random;
import javax.mail.internet.MimeUtility;

import dibd.config.Config;
import dibd.daemon.NNTPConnection;
import dibd.storage.AttachmentProvider;
import dibd.storage.Headers;
import dibd.storage.StorageBackendException;
import dibd.storage.StorageManager;

/**
 * Represents a newsgroup article.
 * 
 * Separated to individual package to protect Art class.
 * Modifier 	Class 	Package 	Subclass 	World
 * 	protected 	Y 		Y 			Y 			N
 * 
 * We need Class and Subclass for Art.
 * Article class should not be used in dibd. 
 * Article class public only for ArticleWeb in web-frontend.
 * 
 * @author user
 * @since dibd/0.1
 */
public class Article implements ArticleWebInput, ArticleForPush, ArticleInput, ArticleOutput, ArticleForOverview{ //extends ArticleHead

	 // visible to subclasses and self only
	protected static class Art{	 //utf-16
		public Integer id = null; //randomly generated in JDBC impl (unsigned)
		public Integer thread_id = null;
		//private String msgID_random = null;
		public String messageId = null;
		public String msgID_host = null;
		public Integer hash = null; //for duplication check
		public String a_name = null;
		public String subject = null;
		public String message = null;
		public long post_time = 0;
		public String path_header = null;
		public int groupId = 0; //for message_id too
		public String groupName = null; //for images
		public String fileName = null; // attachment
		public String fileFormat = null; // attachment
		public int status = 0;  //0 - ok, 1 - file too large
	}

	protected Art a; // visible to subclasses and self only
	
	/**
	 * For subclasses.
	 * When call super in web-frontend after get threads and one thread. 
	 * 
	 * @param article
	 */
	protected Article(ArticleOutput article) {
		super();
		
		a = ((Article) article).a;
		assert (a.id != null); //just for case
	}
	
	/**
	 * For Factory 
	 * 
	 * @param art
	 */
	protected Article(Art a2) {
		super();
		a = a2;
	}
			

	public Integer getId() {
		return a.id;
	}

	public String getMsgID_host() {
		return a.msgID_host;
	}
	//input
	public Integer getHash() {
		return a.hash;
	}

	public Integer getThread_id() {
		return a.thread_id;
	}

	public String getA_name() {
		return a.a_name;
	}

	public String getSubject() {
		return a.subject;
	}

	public String getMessage() {
		return a.message;
	}

	/**
	 * Get epoch time seconds
	 * 
	 * @return
	 */
	public long getPost_time() {
		return a.post_time;
	}

	/**
	 * Get date in NNTP format
	 * UTC
	 * Date:	Thu, 02 May 2013 12:16:44 +0000
	 * 
	 * @return
	 */
	public String getDate() {
		return Headers.formatDate(a.post_time);
	}

	public String getPath_header() {
		return a.path_header;
	}

	public int getGroupId() {
		assert (a.groupId != 0);
		return a.groupId;
	}

	public String getFileCT() {
		return a.fileFormat;
	}
	
	public String getFileName() {
		return a.fileName;
	}

	public String getGroupName() {
		return a.groupName;
	}

	//output
	/**
	 * Get full message_id
	 * 
	 * @return {@literal <}random{@literal @}host{@literal >}
	 */
	public String getMessageId() {
		return a.messageId;
	}
	
	public Integer getStatus() {
		return a.status;
	}
	
	
	/**
	 * If we several times query buildNNTPMessage() we need to save boundary for all of them. 
	 */
	private String boundary = null;
	
	//nntpchan links god damn it.
	private boolean message_was_nntpchaned = false;
	
	//NNTP output
	/**
	 * Get headers or body(or both) for NNTP output.
	 * File without \r\n ant the end! you must place it by yourself
	 * no "." at the end.
	 * BEFORE WRITING MUST BE ENCODED with daemon.LineEncoder!(for buffers recycling)
	 * 
	 * Side effect - boundary. it is static.
	 * 
	 * what 0 - full(with "\r\n" at the end.)
	 * 		1 - header								-for articleCommand,ArticlePusher
	 * 		
	 * 
	 * @param charset used for base64 encoding
	 * @param what
	 * @return article 
	 * @throws IOException 
	 */
	public NNTPArticle buildNNTPMessage(Charset charset, int what) throws IOException {
		assert (Config.inst().get(Config.HOSTNAME, null) != null);
		
		//subject folding max 78 word-encoded
		//Content-Transfer-Encoding: 8bit for cyrilic
		//Content-Transfer-Encoding: 7bit for ASCII
		
		///////////////////      PREPERATION      ///////////////////
		assert(a.messageId != null);
		StringBuilder buf = new StringBuilder();
		//String nl = "\r\r\n"; //"knews" work such?
		final String c = ": ";
		final String b = "--";
		final String nl = NNTPConnection.NEWLINE;
		final int maxLine = 72; //thunderbird like
		final String CTtext = "text/plain; charset=utf-8";
		
		//message testing and nntpchan links
		if (a.message == null)
			a.message = new String("");
		else if (! message_was_nntpchaned){
			a.message = dibd.storage.web.ShortRefParser.addToGlobalNntpchanLinks(a.message);
			message_was_nntpchaned = true;
		}
		String messsageB64 = null;
		int lengthLimit = 490; //max length	of message if we do not have file attached
		boolean ascii = false;
		int maxLength = 0;
		if (a.fileName == null){ //it detect longest string and check if message is ASCII
			for (String s : a.message.split("\n"))
				if(s.length()> maxLength)
					maxLength = s.length();
			ascii = StandardCharsets.US_ASCII.newEncoder().canEncode(a.message);
			if (ascii)
				lengthLimit = 992;
		}
		
		if(a.fileName != null && boundary == null){ //boundary generator
			final char[] MULTIPART_CHARS =
					"-_1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
					.toCharArray();
			StringBuilder buffer = new StringBuilder(); //boundary consists of 1 to 70 characters
			Random rand = new Random();
			for (int i = rand.nextInt(11) + 30; i > 0; i--) { // a random size from 30 to 40
				buffer.append(MULTIPART_CHARS[rand.nextInt(MULTIPART_CHARS.length)]);
			}
			boundary = "=-=-=_"+buffer.toString()+"_=-=-=";
		}

		///////////////////      NNTP ARTICLE BUILDING      ///////////////////
		//Mime-Version
		buf.append(Headers.MIME_VERSION).append(nl);
		//From
		if(a.a_name != null && !a.a_name.isEmpty())
			buf.append(Headers.FROM).append(c).append(MimeUtility.encodeWord(a.a_name)).append(nl);//256
		//Date
		buf.append(Headers.DATE).append(c).append(this.getDate()).append(nl);//256
		//Message-Id
		buf.append(Headers.MESSAGE_ID).append(c).append(this.getMessageId()).append(nl);//256
		//Newsgroups
		assert(a.groupName!=null);
		buf.append(Headers.NEWSGROUPS).append(c).append(a.groupName).append(nl);
		//Subject
		if(a.subject != null && !a.subject.isEmpty())
			//buf.append(Headers.SUBJECT).append(c).append(MimeUtility.fold(8, MimeUtility.encodeWord(a.subject))).append(nl);//256
			buf.append(Headers.SUBJECT).append(c).append(a.subject).append(nl);//256 //UTF-8 headers
		//References
		if(a.thread_id != null && ! a.thread_id.equals(a.id)){
			String refArtMid = null;
			try{
				refArtMid = StorageManager.current().getMessageId(a.thread_id);
			} catch (StorageBackendException e) {	throw new Error("Article.build cant not get thread message-id.",e); } //Never happen
			buf.append(Headers.REFERENCES).append(c).append(refArtMid).append(nl);
		}
		//Path + localhost
		buf.append(Headers.PATH).append(c);
		if(a.path_header != null){ //not local then we should add path
			//System.out.println(a.msgID_origin +" "+ Config.inst().get(Config.HOSTNAME, "localhost"));
			buf.append(Config.inst().get(Config.HOSTNAME, null)+"!").append(a.path_header); //added from the left
		}else{ //local
			assert(a.path_header == null);
			buf.append(Config.inst().get(Config.HOSTNAME, null));
		}
		buf.append(nl);

		if(a.fileName == null){ //text/plain
			//Content-Type
			buf.append(Headers.CONTENT_TYPE).append(c).append(CTtext).append(nl);
			if( maxLength > lengthLimit){
				//Content-Transfer-Encoding: base64
				buf.append(Headers.ENCODING).append(c).append("base64")
				.append(nl);
			} else
				if(ascii) //Content-Transfer-Encoding: 7bit
					buf.append(Headers.ENCODING).append(c).append("7bit")
					.append(nl);
				else //Content-Transfer-Encoding: 8bit
					buf.append(Headers.ENCODING).append(c).append("8bit")
					.append(nl);
		}else{ //multipart
			//Content-Transfer-Encoding
			buf.append(Headers.ENCODING).append(c).append("8bit").append(nl);
			//Content-Type
			buf.append(Headers.CONTENT_TYPE).append(c).append("multipart/mixed; boundary=\""+boundary+"\"")
			.append(nl);
		}

		//empty line: main headers - body
		buf.append(nl);

		if (what == 1) //head
			return new NNTPArticle(buf.toString(), null, null); //only head
		

		
		//body preperation 
		//if we have attachment or too long line we create messsageB64  
		if (a.fileName != null || maxLength > lengthLimit){ //thunderbird like (about 990-995 for ASCII), (about 490-500 for UTF-8)
			byte[] message = a.message.getBytes(charset);
			messsageB64 = new String(Base64.getMimeEncoder(maxLine, NNTPConnection.NEWLINE.getBytes()).encode(message)); //utf-16
		}
		//else a.message will be used.
		//////////////////// Body ///////////////////////////////
		StringBuilder buf2 = new StringBuilder();
		StringBuilder buf3 = null;
		if(a.fileName == null){ //text/plain
			if (maxLength > lengthLimit) //base64 encoded message
				buf2.append(messsageB64); //the end, no need new line
			else
				buf2.append(a.message);
		}else{ //multipart
			//Part 1 message
			buf2.append(b).append(boundary).append(nl);
			//Content-type: text/plain; charset=utf-8
			buf2.append(Headers.CONTENT_TYPE).append(c).append(CTtext)
			.append(nl);
			//Content-Transfer-Encoding: base64
			buf2.append(Headers.ENCODING).append(c).append("base64")
			.append(nl);
			//empty line
			buf2.append(nl);

			//message
			buf2.append(messsageB64).append(nl);

			//empty line no need
			//buf2.append(NNTPConnection.NEWLINE);
			//Part 2 attachment
			buf2.append(b).append(boundary).append(nl);
			//Content-Type: ?
			buf2.append(Headers.CONTENT_TYPE).append(c).append(a.fileFormat)
			.append(nl);
			//Content-Disposition: attachment; filename="?"
			buf2.append(Headers.CONTENT_DISP).append(c).append("attachment; filename=\""+a.fileName+"\"")
			.append(nl);
			//Content-Transfer-Encoding: base64
			buf2.append(Headers.ENCODING).append(c).append("base64").append(nl);
			//empty line
			buf2.append(nl);

			buf3 = new StringBuilder();
			//buf3.append(new String(eFile)).append(nl);
			buf3.append(nl);
			buf3.append(b).append(boundary).append(b); //the end, no need new line!
		}


		//if (what == 2) //body
			//return buf2.toString();
		//else //full
		if (buf3 != null)
			return new NNTPArticle(buf.append(buf2).toString(),
					StorageManager.attachments.getPath(a.groupName, a.fileName, AttachmentProvider.Atype.img), 
					buf3.append(nl).toString());
		else
			return new NNTPArticle(buf.append(buf2).append(nl).toString(), null, null); //full

	}
	
	
	//for testing
	@Override
	public String toString() {
		return "id:"+a.id+ ' '+"thread_id:"+ a.thread_id+' '+"messageId:"+a.messageId+' '
		+"msgID_host:"+a.msgID_host+' '+"hash:"+a.hash+' '+"a_name:"+a.a_name+' '
		+"subject:"+a.subject+' '+"message:"+a.message+' '+"post_time:"+a.post_time+' '
		+"path_header:"+a.path_header+' '+"groupId:"+a.groupId+' '+"groupName:"+a.groupName+' '
		+"fileName:"+a.fileName+' '+"fileFormat:"+a.fileFormat;
	}
	
	//for testing
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof Article) {
			Article arti = (Article) obj;
			return ( Objects.equals(arti.a.id, a.id) && Objects.equals(arti.a.thread_id, a.thread_id) && Objects.equals(arti.a.messageId, a.messageId)
					&& Objects.equals(arti.a.msgID_host, a.msgID_host) && Objects.equals(arti.a.hash, a.hash) && Objects.equals(arti.a.a_name, a.a_name)
					&& Objects.equals(arti.a.subject, a.subject) && Objects.equals(arti.a.message, a.message) && arti.a.post_time == a.post_time
					&& Objects.equals(arti.a.path_header, a.path_header) && arti.a.groupId == a.groupId && Objects.equals(arti.a.groupName, a.groupName)
					&& Objects.equals(arti.a.fileName, a.fileName) && Objects.equals(arti.a.fileFormat, a.fileFormat));
			
			//return (this.a.hashCode() == arti.a.hashCode()); //not working :(
		} else {
			return false;
		}
	}
	
}
