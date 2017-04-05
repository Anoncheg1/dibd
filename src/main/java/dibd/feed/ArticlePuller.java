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

package dibd.feed;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;

import dibd.config.Config;
import dibd.daemon.ChannelLineBuffers;
import dibd.daemon.NNTPConnection;
import dibd.daemon.NNTPInterface;
import dibd.daemon.TLS;
import dibd.daemon.command.IhaveCommand;
import dibd.storage.StorageBackendException;
import dibd.storage.GroupsProvider.Group;
import dibd.storage.Headers;
import dibd.storage.article.Article;
import dibd.storage.article.Article.NNTPArticle;
import dibd.util.Log;

/**
 * Pull an Article from a NNTP server using the NEWNEWS and ARTICLE commands.
 * To be shure we pull messages from all servers in one group. 
 * (If there is several servers in one group it is enough to check messages from one of them.)
 * 
 * If error it throws IOException.
 *
 * @author .
 * @since dibd
 */
public class ArticlePuller {

	private Socket socket;
	private PrintWriter out;
	private BufferedReader in;
	private static final String NL = NNTPConnection.NEWLINE;
	private final Charset charset = StandardCharsets.UTF_8;
	
	
	
	private Proxy proxy;
	private final String host; //for log messages
	private int port;
	private final boolean TLSEnabled; //for private class Response
	/**
	 * Check connection to remote peer.
	 * host name required for log messages.
	 * 
	 * @param socket
	 * @param TLSEnabled
	 * @param host
	 * @throws IOException
	 */
	public ArticlePuller(Proxy proxy, String host, int port, boolean TLSEnabled) throws SSLPeerUnverifiedException, IOException{
		this.proxy = proxy;
		this.host = host;
		this.port = port;
		this.TLSEnabled = TLSEnabled;
		connect();
	
		
	}
	
	void connect() throws IOException{
		this.socket = FeedManager.createSocket(proxy, host, port);
		socket.setSoTimeout(3000);
		this.socket = FeedManager.getHelloFromServer(socket, TLSEnabled, host, charset);
		if (TLSEnabled){
			
			SSLSocket sslsocket = (SSLSocket) this.socket;
			//system.out Log.get().severe("Socket:"+socket.getClass().getName());
			//new encrypted streams
			this.out = new PrintWriter(new OutputStreamWriter(sslsocket.getOutputStream(), this.charset));
			this.in = new BufferedReader(new InputStreamReader(sslsocket.getInputStream(), this.charset));
			
			this.instream = sslsocket.getInputStream();
			
		}else{
			this.out = new PrintWriter(new OutputStreamWriter(this.socket.getOutputStream(), this.charset));
			//this.in = new BufferedReader(new InputStreamReader(this.socket.getInputStream(), this.charset));
			
			this.instream = socket.getInputStream();
	
		}
	}

	private InputStream instream;
	private ChannelLineBuffers lineBuffers = new ChannelLineBuffers(); //must be very accurate with recycling
	List<ByteBuffer> lines = new ArrayList<>(); //must be recycled no matter what.

	
	//BufferedReader replacer. Buffered reader may be DoSed it can't stop.
	String getIS() throws IOException {
		byte[] raw = read();
		if (raw != null)
			return new String(raw, this.charset);
		else
			return null;
	}
	
	
	byte[] read() throws IOException {
			lines.addAll(lineBuffers.getInputLines());
			if(!lines.isEmpty()){
				ByteBuffer buf = this.lines.remove(0);
				byte[] rawline = new byte[buf.limit()];
				buf.get(rawline);
				ChannelLineBuffers.recycleBuffer(buf);
				return rawline;
			}else{
				ByteBuffer buf1 = this.lineBuffers.getInputBuffer();
				int i = 0;
				while (buf1.hasRemaining()) {

					int b;
					try{
						b = this.instream.read();
					}catch(SocketTimeoutException e)
					{
						
						if (i++ >= 6){
							Log.get().log(Level.INFO, "From {0} connection timeout. return null.", this.host);
							return null; //connection timeout
						}
						lines.addAll(lineBuffers.getInputLines());
						if (lines.isEmpty())
							continue;
						else
							break;
					}

					//if (b == -1) {
						//break;
					//}

					buf1.put( (byte) b);
				}
				
				return read();
			}

		}
	
	
	//Hook for command connection
	private class Response implements NNTPInterface{

		private Queue<String> rLine = new LinkedList<String>(); //FIFO 1 thread - PullFeeder

		public void println(String line){
			rLine.add(line);//to end
		}

		//my
		public String readLine(){
			return rLine.poll(); //remove first
		};

		public Charset getCurrentCharset() {return charset;}
		public Article getCurrentArticle() {return null;}
		public Group getCurrentGroup() {return null;}
		public void setCurrentArticle(Article article) {}
		public void setCurrentGroup(Group group) {}
		public void close() {}
		public void print(FileInputStream fs, String mId){}
		public void print(NNTPArticle nart, String mId){}
		public TLS getTLS() {return null;}
		public void setTLS(TLS tls) {}
		public boolean isTLSenabled() {
			return TLSEnabled;
		}
		public String getHost() { //used in IHAVE when transfer to itself for log
			
			String host = null;
			if (TLSEnabled){
				X509Certificate cert = null;
				try {
					//was checked at FeedManager.getHelloFromServer()
					//TODO: Strange error happen sometimes. here - can not cast socket to SSLSocket. 
					cert = (X509Certificate) ((SSLSocket)socket).getSession().getPeerCertificates()[0];
				} catch (SSLPeerUnverifiedException e) {// should never happen
					// TODO Auto-generated catch block
					e.printStackTrace();
					return null;
				}
				Matcher m = Pattern.compile("CN=([^,]+)").matcher(cert.getSubjectX500Principal().getName());
				if(m.find()){host = m.group(1);}

			}else
				host = socket.getRemoteSocketAddress().toString();

			return host;
		}

		
			
	}
	private Response conn = new Response(); //hook for loopback
	
	
	private int retryes = 1;
	
	/**
	 * Pull articles from carefully sorted list of threads and rLeft message-ids. 
	 * 
	 * @param mIDs  mind, isThread?
	 * @return
	 * @throws IOException
	 * @throws StorageBackendException
	 */
	public int toItself(Iterator<Entry<String, List<String>>> iterator, int resived) throws StorageBackendException, IOException{
		int reseived = resived;
		int errors = 0;
		try{
		//we pass rLeft for accepted thread only
		//If thread was corrupted then we reject his rLeft
		//it will prevent "getting missing threads".
			while (iterator.hasNext()){
				Entry<String, List<String>> mId = iterator.next();

				int res = transferToItself(new IhaveCommand(), mId.getKey());
				if (res == 0)//thread accepted?
					reseived ++;
				else if (res == 1){ //error in thread
					if(errors++ >= 2)
						throw new IOException("2 errors"); 
					continue;
				}

				for(String replay : mId.getValue()){
					int re = transferToItself(new IhaveCommand(), replay);
					if (re == 0)
						reseived ++;
					else if (re == 1 && errors++ >= 2)
							throw new IOException("2 errors");//error
				}

				iterator.remove();
			}

		}catch(IOException e){
			if (this.retryes++ > 5){
				Log.get().log(Level.WARNING, "Pull brake up with {0} becouse unexpected responses.", this.host );
				return reseived;
			}else{
				Log.get().log(Level.INFO, "Pull {0} some error happen, we reconnect at {1} time.", new Object[]{this.host, this.retryes} );
				close();//lines recycled in close()
				lineBuffers.getInputBuffer().clear();//clear input
				connect();
				return toItself(iterator, resived); //recursion
			}
		}
		
		return reseived;
	}
	
	
	
	/**
	 * Get article with ARTICLE and offer it to himself with IhaveCommand class.
	 * 
	 * @param ihavec
	 * @param messageId
	 * @return 0 if accepted 1 if error 2 if already have 3 do not repeat 
	 * @throws IOException for any inappropriate input from remote host
	 * @throws StorageBackendException
	 */
	public int transferToItself(IhaveCommand ihavec, String messageId)
			throws StorageBackendException, IOException {
		//we do not need to push pulled articles. we will push pushed articles.
		//and we don't need log messages.
		ihavec.setPullMode(); 

		String s = "IHAVE "+ messageId;

		ihavec.processLine(conn, s, s.getBytes(charset));

		String line = conn.readLine();
		if (line == null || !line.startsWith("335")) {
			if (line != null && line.startsWith("435")){
				return 2;
			}else
				Log.get().log(Level.WARNING, "From {0} {1} we ihave:{2}", new Object[]{this.host, messageId, line} );
			return 1; //some error
		}

		//ARTICLE
		this.out.print("ARTICLE " + messageId + NL);
		this.out.flush();
		/**	220 0|n message-id    Article follows (multi-line)
		 *	430                   No article with that message-id
		 */
		line = getIS();//this.in.readLine();
		if (line == null) {
			Log.get().warning("Unexpected null reply from remote host or timeout");
			return 1;
		}
		if (line.startsWith("430 ")) {
			Log.get().log(Level.WARNING, "Message {0} not available at {1}",
					new Object[]{messageId, this.host});
			return 3;
		}
		if (!line.startsWith("220 ")) {
			throw new IOException("Unexpected reply to ARTICLE "+messageId+" "+line);
		}


		//OK Lets rock!
		Log.get().log(Level.FINE, "Pulling article {0} from {1} ", new Object[]{messageId, this.host} );


		read_article:{
			do{ //read from ARTICLE response and write to loopback IHAVE

				//System.out.println("READ FILE " + messageId+ " readed:"+line.length());
				String ihaveline = conn.readLine();
				if ( ihaveline != null){//error
					conn.println(ihaveline);

					//any message in the middle of transfer is error or success that required to finish
					for(int i = 0; i<15 ; i++){ //wait 15 lines for end then interrupt.
						line = getIS();//this.in.readLine();
						if(line == null || line.equals("."))
							break read_article;
					} //wait for 20 lines

					Log.get().log(Level.INFO, "{0} Reconnect, clear buffers", messageId);
					close();//lines recycled in close()

					lineBuffers.getInputBuffer().clear();//clear input

					connect();

					break;
				}

				byte[] raw = read();
				if (raw == null){
					Log.get().log(Level.WARNING, "{0} null line resived or timeout during reading from {1} ", new Object[]{messageId, this.host} );
					return 1;
				}
				line = new String(raw, this.charset);
				Log.get().log(Level.FINEST, "<< {0}", line);
				ihavec.processLine(conn, line, raw); //send ihave

			}while(!".".equals(line));
		}



		line = conn.readLine();//IHAVE response
		if (line != null)
			if(line.startsWith("235")) {
				Log.get().log(Level.INFO, "{0} successfully received", messageId);
				return 0;
			} else if (line.equals(IhaveCommand.noRef)){//may happen if thread is refering to another thread....
				Log.get().log(Level.SEVERE, "PULL from {0} {1} THIS THREAD refering another thread!", new Object[]{this.host, messageId} );
				return 1;
			}else
				Log.get().log(Level.WARNING, "Pulling {0} from {1} self IHAVE: {2}", new Object[]{messageId, this.host, line});

		return 1;
	}

	
	private List<String> getCapabilities() throws IOException{
		this.out.print("CAPABILITIES"+NL);
		this.out.flush();

		String line = getIS();//this.in.readLine();
		if (line == null || !line.startsWith("101")) { //230 List of new articles follows (multi-line)
			Log.get().log(Level.WARNING, "From host: {0} CAPABILITIES response: {1}", new Object[]{this.host, line});
			throw new IOException();
		}
		List<String> capabilties = new ArrayList<>();
		while (line != null && !(".".equals(line))) {
			capabilties.add(line);
			line = getIS();
		}
		return capabilties; 
	}
	

	/**
	 * Get last post for every group,
	 * request article ids with NewNews
	 * call transferToItself for every id
	 *
	 * @param groupsTime
	 * @param pulldays - not necessary
	 * @return list of threads(true) following its rLeft(false) right after it.
	 * @throws IOException
	 * @throws StorageBackendException
	 */
	public Map<String, List<String>> scrap(final Set<Group> groups) throws StorageBackendException, IOException{
		List<String> capabilties = getCapabilities();
		if ( ! capabilties.contains("READER")){ //case sensitive!
			Log.get().log(Level.WARNING, "From host: {0} CAPABILITIES do not contain: READER", this.host);
			throw new IOException(); //this is too strange we will not skip
		}
		
		Map<String, List<String>> messageIDs = new LinkedHashMap<>(100);
		for (Group g : groups){
			messageIDs.putAll(oldFashionScrap(g));
		}
		return messageIDs;
		
	}
	
	//throw ioexception if want to repeat.
	boolean getThread(String threadMId, String gname) throws IOException, StorageBackendException{
		
		//int received=0;
		//First lets try to get thread
		/*int res = transferToItself(new IhaveCommand(), threadMId);
		if (res == 1){
			Log.get().log(Level.WARNING, "From host {0} can not get thread {1} group {2}", new Object[]{this.host, threadMId, gname});
			throw new IOException(); //we will retry
		}else if (res == 2){
			Log.get().log(Level.INFO, "Missing thread {0} we guess was received before {1} group {2}", new Object[]{threadMId, this.host, gname});
			return false;
		}else if (res == 3){
			Log.get().log(Level.INFO, "Missing thread {0} we do not have access {1} group {2}", new Object[]{threadMId, this.host, gname});
			return false;
		}else if (res == 0)
			received++;
		*/
		List<String> capabilties = getCapabilities();
		if ( ! capabilties.contains("READER")){ //case sensitive!
			Log.get().log(Level.WARNING, "From host: {0} CAPABILITIES do not contain: READER", this.host);
			return false;
		}
		
		this.out.print("GROUP "+gname+NL);
		this.out.flush();
		String line = getIS();//this.in.readLine();
		if (line == null || !line.startsWith("211")) { //Group selected
			Log.get().log(Level.WARNING, "From host: {0} GROUP {1} request, response: {2}", new Object[]{this.host, gname, line});
			throw new IOException(); //we will retry
		}

		this.out.print("XOVER 0"+NL);
		this.out.flush();
		line = getIS();//this.in.readLine();
		if (line == null || !line.startsWith("224")) { //overview follows
			Log.get().log(Level.WARNING, "From host: {0} XOVER 0 for group {1}, response: {2}", new Object[]{this.host, gname, line});
			throw new IOException(); //we will retry
		}
		
		List<String> replays = new ArrayList<>();
		boolean found = false;
		
		line = getIS();//this.in.readLine();
		while (line != null && !(".".equals(line))) {
			String[] part = line.split("\t");
			if (part.length < 5){
				Log.get().log(Level.WARNING, "From host: {0} XOVER 0 for group {1}, Lines have less than 5 parts: {2}", new Object[]{this.host, gname, line});
				return false;
			}

			//0 1 2 - id, subject, from
			//part[3] = time;
			//part[4] = mId;
			//part[5] = threadMId; //for replay
			//6 7 - lines,bytes - old RFC format.  We do not allow such format and will crash
			
			
			//1)search for thread
			//2) if thread exist
			//3) get his replays
			
			//Do not assume thread is early than his replays '-.-'
			
			if(part.length > 5 && part[5].equals(threadMId) && part[4].matches(NNTPConnection.MESSAGE_ID_PATTERN))
				replays.add(part[4]); //3)
			
			line = getIS();
		}
		//thread must have replays if it got up from dust
		if(replays.isEmpty()){
			Log.get().log(Level.WARNING, "No replays for missing thread, strange! From host {0} group {1} mid {2}", new Object[]{this.host, gname, threadMId});
			return false;
		}
		
		
		
		//receive replays
		Map<String, List<String>> mIDs = new LinkedHashMap<>(1);
		mIDs.put(threadMId, replays);
		int received = this.toItself(mIDs.entrySet().iterator(), 0);
		
		/*for (String mId : replays){
			res = transferToItself(new IhaveCommand(), mId);	
			if (res == 0)//thread accepted?
				received ++;
		}*/
		
		if (received > 0){
			Log.get().log(Level.FINE, "Missing thread {0} reseived in articles {1}", new Object[]{threadMId, received});
			return true;
		}
		return false;
			
		
	}
	
		
	
	/**
	 * Get list of articles. 
	 * Peer MUST return sorted list.
	 * (or else we will slow down in reseivingService loop for
	 * "no reference" case)
	 * 
	 * XOVER 0
	 *  
	 * @param group
	 * @return empty list if error
	 * @throws IOException
	 * @throws StorageBackendException 
	 */
	private Map<String, List<String>> oldFashionScrap(final Group group) throws IOException, StorageBackendException{

		String gname = group.getName();
		this.out.print("GROUP "+gname+NL);
		this.out.flush();
		
		String line = getIS();
		if (line == null || !line.startsWith("211")) { //Group selected
			Log.get().log(Level.WARNING, "From host: {0} GROUP {1} request, response: {2}", new Object[]{this.host, gname, line});
			return new LinkedHashMap<>();//we will skip this group
		}

		this.out.print("XOVER 0"+NL);
		this.out.flush();
		line = getIS();
		if (line == null || !line.startsWith("224")) { //overview follows
			Log.get().log(Level.WARNING, "From host: {0} XOVER 0 for group {1}, response: {2}", new Object[]{this.host, gname, line});
			return new LinkedHashMap<>();//we will skip this group
		}

		
		
		Map<String, String> replays = new LinkedHashMap<>(250);
		List<String> threads = new ArrayList<String>(250);
		Map<String, String> aReplTime = new HashMap<>();
		Map<String, String> aThreads = new HashMap<>();
		
		line = getIS();
		while (line != null && !(".".equals(line))) {
			String[] part = line.split("\t");
			if (part.length < 5){
				Log.get().log(Level.WARNING, "From host: {0} XOVER 0 for group {1}, Lines have less than 5 parts: {2}", new Object[]{this.host, gname, line});
				throw new IOException();//we skip this peer
			}

			//0 1 2 - id, subject, from
			//part[3] = time;
			//part[4] = mId;
			//part[5] = threadMId; //for replay
			//6 7 - lines,bytes - old RFC format.  We do not allow such format and will crash
			
			//sorted by replay post date. threads are disrupted
			
			if(part[4].matches(NNTPConnection.MESSAGE_ID_PATTERN)){
				if (part.length == 5 || !part[5].matches(NNTPConnection.MESSAGE_ID_PATTERN)){
					threads.add(part[4]);
					aThreads.put(part[4], part[3]);
				}else if ( part.length == 6 && part[5].matches(NNTPConnection.MESSAGE_ID_PATTERN)){
					replays.put(part[4], part[5]);
					aReplTime.put(part[4], part[3]);
				}else
					Log.get().log(Level.WARNING, "From: {0} unsupported XOVER format of line or mesage id:\n{1}", new Object[]{this.host, line});
			}else
				Log.get().log(Level.WARNING, "From: {0} unsupported XOVER format of line or mesage id:\n{1}", new Object[]{this.host, line});

			if(replays.size() > 999000/2 || threads.size() > 999000/2){
				Log.get().log(Level.SEVERE, "From host: {0} XOVER 0 for group {1}, there is over 999000 article lines like {2}", new Object[]{this.host, gname, line});
				throw new IOException();
			}

			line = getIS();
		}
		
		//500 initial capacity may be anything. 500 is rough min posts count.(just more than default 10)
		Map<String, List<String>> messageIDs;// = new LinkedHashMap<>(0); //empty for return
		
		//remove rLeft without threads
		messageIDs = FeedManager.sortThreadsReplays(threads, replays, this.host, group); //ordered LinkedHashMap
		
		//replay with larger date should be after replay with earlier date
		//Log.get().log(Level.F, "BEFORE: {0} ", messageIDs.size());
		Map<String, Long> thTime = new LinkedHashMap<>();
		for(Entry<String, List<String>> th : messageIDs.entrySet()){
			//check post date of last replay
			String rdate;
			List<String> re = th.getValue();
			if ( ! re.isEmpty()) //thread is not empty
				rdate = aReplTime.get(re.get(re.size()-1)); //time of last
			else
				rdate = aThreads.get(th.getKey());
			
			try {
				thTime.put(th.getKey(), Headers.ParseRawDate(rdate));
			} catch (ParseException e) {
				Log.get().log(Level.WARNING, "cannot parse date: {0} ", e.getLocalizedMessage());
			}
		}
		
		//sort threads by time
		List<Entry<String, Long>> thTimeList = thTime.entrySet().stream().sorted( (e1, e2) -> Long.compare(e1.getValue(), e2.getValue()) ).collect(Collectors.toList());
		//left max threads per group threads
		int threads_per_page = Config.inst().get(Config.THREADS_PER_PAGE, 5);
		int pages = Config.inst().get(Config.PAGE_COUNT, 6)+1; //from 0
		int maxth =  threads_per_page * pages;
		int size = thTimeList.size();
		Map<String, List<String>> messageIDs2 = new LinkedHashMap<>(maxth);
		int i = (size - maxth)>0 ? (size - maxth) : 0; //last maxth threads
		
		for(; i<size; i++){
			String mid = thTimeList.get(i).getKey();
			List<String> val = messageIDs.get(mid);
			//if (thTimeList.get(i).getValue().longValue() >= last_post) //will be problem if peer was off and we had new messages. not very old peer threads will be rejected.
				messageIDs2.put(mid, val);
		}
		
		Log.get().log(Level.INFO, "Threads {0} in {1} to PULL from {2} ", new Object[]{messageIDs2.size(), gname, this.host});
		return messageIDs2;
	}

	/**
	 * Do not used in class. For PullFeeder only.
	 * 
	 * @throws IOException
	 */
	public void close(){
		try{
			//ChannelLineBuffers buffers must be recycled no matter what.
			if(lines != null && ! lines.isEmpty()){
				Iterator<ByteBuffer> it = lines.iterator();
				while (it.hasNext()){
					ChannelLineBuffers.recycleBuffer(it.next());
					it.remove();
				}
			}
			
			if (out != null && this.instream != null){
				this.out.print("QUIT\r\n");
				this.out.flush();

				//this.lastActivity = System.currentTimeMillis();
				//getIS();
				this.in.readLine(); //we are polite
				this.in.close();
				//lineBuffers.recycleBuffers();
				instream.close();
				out.close();
			}
		}catch(IOException ex){}finally{
			try{
				this.socket.close();
			}catch(IOException ex){}
		}

	}
	
	/**
	 * We assume that newnews will return correct ordered list.
	 *
	 * map ordered
	 *  
	 * TODO:do we really need last_post? what if peer was off for the first start. 
	 * @param group
	 * @param last_post  mID, thread? + if true = thread, false = replay 
	 * @throws IOException
	 */
	/*
	private Map<String, List<String>> newnewsScrap(Group group, long last_post) throws IOException{
		//NEWNEWS groupname 1464210306
		//230 success
		StringBuilder buf = new StringBuilder();

		buf.append("NEWTHREADS ").append(group.getName()).append(' ').append(last_post).append(NL);
		this.out.print(buf.toString());
		this.out.flush();

		String line = this.in.readLine();
		if (line == null || !line.startsWith("230")) { //230 List of new articles follows (multi-line)
			Log.get().log(Level.WARNING, "From host: {0} NEWTHREADS response: {1}",
					new Object[]{this.host, line});
			return new LinkedHashMap<>(); //skip group
		}

		
		Map<String, String> replays = new LinkedHashMap<>(250);
		List<String> threads = new ArrayList<String>(250);
		
		//this.lastActivity = System.currentTimeMillis();
		line = this.in.readLine();
		while (line != null && !(".".equals(line))) {
			String[] lsplitted = line.split("\\p{Space}+");
			
			if(lsplitted[0].matches(NNTPConnection.MESSAGE_ID_PATTERN))
				if(lsplitted.length == 1) //if thread and match patter
					threads.add(lsplitted[0]); //thread
				else if(lsplitted.length >= 2 && lsplitted[1].matches(NNTPConnection.MESSAGE_ID_PATTERN)) 
					replays.putIfAbsent(lsplitted[0], lsplitted[1]); //replay
				else
					Log.get().log(Level.WARNING, "From: {0} NENEWS unsupported Message-ID line: {1}", new Object[]{this.host, line});

			if(replays.size() > 999000/2 || threads.size() > 999000/2){
				Log.get().log(Level.SEVERE, "From host: {0} NEWNEWS for group {1}, there is over 999000 article lines like {2}",
						new Object[]{this.host, group.getName(), line});
				throw new IOException();
			}

			line = this.in.readLine();
		}

		if(replays.size() > 999000/2 || threads.size() > 999000/2){
			Log.get().log(Level.SEVERE, "From host: {0} NEWNEWS for group {1}, there is over 999000 rLeft or threads", new Object[]{this.host, group.getName()});
			throw new IOException();
		}
		 
		//sort just for case. It is important to have no missing threads.
		 Map<String, List<String>> messageIDs = FeedManager.sortThreadsReplays(threads, replays, this.host); //ordered LinkedHashMap
		
		return messageIDs;
	}*/
	

}
