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
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.nio.charset.Charset;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
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

	private final Socket socket;
	private PrintWriter out;
	private BufferedReader in;
	private static final String NL = NNTPConnection.NEWLINE;
	private final Charset charset = Charset.forName("UTF-8");
	private final String host; //for log messages
	private final boolean TLSEnabled; //for private class Response
	
	private ChannelLineBuffers lineBuffers;
	
	
	/**
	 * Check connection to remote peer.
	 * host name required for log messages.
	 * 
	 * @param socket
	 * @param TLSEnabled
	 * @param host
	 * @throws IOException
	 */
	public ArticlePuller(Socket socket, boolean TLSEnabled, String host) throws SSLPeerUnverifiedException, IOException{
		this.host = host;
		this.TLSEnabled = TLSEnabled;
		
		this.socket = FeedManager.getHelloFromServer(socket, TLSEnabled, host, charset);
		
		if (TLSEnabled){
			
			SSLSocket sslsocket = (SSLSocket) this.socket;
			//new encrypted streams
			this.out = new PrintWriter(new OutputStreamWriter(sslsocket.getOutputStream(), this.charset));
			this.in = new BufferedReader(new InputStreamReader(sslsocket.getInputStream(), this.charset));
			
		}else{
			this.out = new PrintWriter(new OutputStreamWriter(this.socket.getOutputStream(), this.charset));
			this.in = new BufferedReader(new InputStreamReader(this.socket.getInputStream(), this.charset));
	
		}
		
		lineBuffers = new ChannelLineBuffers();
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
					cert = (X509Certificate) ((SSLSocket)socket).getSession().getPeerCertificates()[0];
				} catch (SSLPeerUnverifiedException e) {// should never happen
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				Matcher m = Pattern.compile("CN=([^,]+)").matcher(cert.getSubjectX500Principal().getName());
				if(m.find()){host = m.group(1);}

			}else
				host = socket.getRemoteSocketAddress().toString();

			return host;
		}	
	}
	private Response conn = new Response(); //hook for loopback
	
	

	
	
	
	/**
	 * Pull articles from carefully sorted list of threads and rLeft message-ids. 
	 * 
	 * @param mIDs  mind, isThread?
	 * @return
	 * @throws IOException
	 * @throws StorageBackendException
	 */
	public int toItself(Map<String, List<String>> mIDs) throws IOException, StorageBackendException{
		int reseived = 0;
		//we pass rLeft for accepted thread only
		//If thread was corrupted then we reject his rLeft
		//it will prevent "getting missing threads".
		
		for (Entry<String, List<String>> mId : mIDs.entrySet()){
			int res = transferToItself(new IhaveCommand(), mId.getKey());
			if (res == 0)//thread accepted?
				reseived ++;
			else if (res == 1) //error in thread
				continue; 
				
			for(String replay : mId.getValue())
				if (transferToItself(new IhaveCommand(), replay) == 0)
					reseived ++;
					
		}
		return reseived;
	}
	
	
	
	/**
	 * Get article with ARTICLE and offer it to himself with IhaveCommand class.
	 * 
	 * @param ihavec
	 * @param messageId
	 * @return 0 if accepted 1 if error 2 if already have 
	 * @throws IOException for any inappropriate input from remote host
	 * @throws StorageBackendException
	 */
	public int transferToItself(IhaveCommand ihavec, String messageId)
			throws StorageBackendException {
		
		ihavec.blockPush(); //we do not need to push pulled articles. we will push pushed articles.
		
		String s = "IHAVE "+ messageId;
		try {
			ihavec.processLine(conn, s, s.getBytes("UTF-8"));
		
	
		String line = conn.readLine();
		if (line == null || !line.startsWith("335")) {
			if (line != null && line.startsWith("435")){
			//	Log.get().log(Level.FINE, "IHAVE-loopback {0} we already have this or don't want it", messageId);
				return 2;
			}else
				Log.get().log(Level.WARNING, "transferToItself from {0} {1} we ihave:{2}", new Object[]{this.host, messageId, line} );
			return 1; //some error
		}

		this.out.print("ARTICLE " + messageId + NL);
		this.out.flush();
		
		/**	220 0|n message-id    Article follows (multi-line)
	    *	430                   No article with that message-id
	    */
		
		Log.get().log(Level.FINE, "Pulling article {0} from {1} ", new Object[]{messageId, this.host} );
		
		do{ //read from ARTICLE response and write to loopback IHAVE
			
			line = this.in.readLine();
			if (line == null) {
				Log.get().log(Level.WARNING, "Article from {0} {1} null line resived during reading", new Object[]{this.host, messageId} );
				return 1;
			}

			byte[] raw = line.getBytes(charset);
			if (raw.length >= ChannelLineBuffers.BUFFER_SIZE){ //ReceivingService.readingBody() will not add \r\n for big line
				ihavec.processLine(conn, line, raw); //send ihave
				ihavec.processLine(conn, "", new byte[]{}); //ReceivingService will add \r\n
			}else
				ihavec.processLine(conn, line, raw); //send ihave

		}while(!".".equals(line));

		
		line = conn.readLine();//IHAVE response
		if (line != null)
			if(line.startsWith("235")) {
				Log.get().log(Level.INFO, "Message {0} successfully transmitted", messageId);
				return 0;
			} else if (line.equals(ihavec.noRef)){//must never happen
				Log.get().log(Level.SEVERE, "PULL from {0} {1} NO SUCH THREAD", new Object[]{this.host, messageId} );
				System.err.println("PULL from "+this.host+" "+messageId+" NO SUCH THREAD");
				System.exit(1);
			}else
				Log.get().log(Level.WARNING, "Pulling {0} from {1} self IHAVE: {2}", new Object[]{messageId, this.host, line});
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} //send ihave	
		return 1;
	}

	
	private List<String> getCapabilities() throws IOException{
		this.out.print("CAPABILITIES"+NL);
		this.out.flush();

		String line = this.in.readLine();
		if (line == null || !line.startsWith("101")) { //230 List of new articles follows (multi-line)
			Log.get().log(Level.WARNING, "From host: {0} CAPABILITIES response: {1}", new Object[]{this.host, line});
			throw new IOException();
		}
		List<String> capabilties = new ArrayList<>();
		while (line != null && !(".".equals(line))) {
			capabilties.add(line);
			line = this.in.readLine();
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
	
	
	void getThread(String threadMId, String gname) throws IOException, StorageBackendException{
		List<String> capabilties = getCapabilities();
		int received=0;
		//First lets try to get thread
		int res = transferToItself(new IhaveCommand(), threadMId);
		if (res == 1){
			Log.get().log(Level.WARNING, "From host {0} can not get thread {1} group {2}", new Object[]{this.host, threadMId, gname});
			return;
		}else if (res == 0)
			received++;
		
		if ( ! capabilties.contains("READER")){ //case sensitive!
			Log.get().log(Level.WARNING, "From host: {0} CAPABILITIES do not contain: READER", this.host);
			return;
		}
		
		this.out.print("GROUP "+gname+NL);
		this.out.flush();
		String line = this.in.readLine();
		if (line == null || !line.startsWith("211")) { //Group selected
			Log.get().log(Level.WARNING, "From host: {0} GROUP {1} request, response: {2}", new Object[]{this.host, gname, line});
			throw new IOException(); //we will retry
		}

		this.out.print("XOVER 0"+NL);
		this.out.flush();
		line = this.in.readLine();
		if (line == null || !line.startsWith("224")) { //overview follows
			Log.get().log(Level.WARNING, "From host: {0} XOVER 0 for group {1}, response: {2}", new Object[]{this.host, gname, line});
			throw new IOException(); //we will retry
		}
		
		List<String> replays = null;
		boolean found = false;
		
		line = this.in.readLine();
		while (line != null && !(".".equals(line.trim()))) {
			String[] part = line.split("\t");
			if (part.length < 5){
				Log.get().log(Level.WARNING, "From host: {0} XOVER 0 for group {1}, Lines have less than 5 parts: {2}", new Object[]{this.host, gname, line});
				return;
			}

			//0 1 2 - id, subject, from
			//part[3] = time;
			//part[4] = mId;
			//part[5] = threadMId; //for replay
			//6 7 - lines,bytes - old RFC format.  We do not allow such format and will crash
			
			
			//1)search for thread
			//2) if thread exist
			//3) get his replays
			
			//we assume thread is early than his replays
			
			if(found == false){ //1)
				if (part[4].equals(threadMId)){
					replays = new ArrayList<>();
					found = true;
				}
			}else //2)
				if(part[5].equals(threadMId) && part[4].matches(NNTPConnection.MESSAGE_ID_PATTERN))
					replays.add(part[4]); //3)
		}
		
		if( ! found){
			Log.get().log(Level.WARNING, "Thread not found. From host {0} group {1} mid {2}", new Object[]{this.host, gname, threadMId});
			return;
		}
		
		//receive replays
		for (String mId : replays){
			res = transferToItself(new IhaveCommand(), mId);	
			if (res == 0)//thread accepted?
				received ++;
		}
		
		if (received > 0)
			Log.get().log(Level.FINE, "Missing thread {0} reseived in articles {1}", new Object[]{threadMId, received});
			
		
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
		String line = this.in.readLine();
		if (line == null || !line.startsWith("211")) { //Group selected
			Log.get().log(Level.WARNING, "From host: {0} GROUP {1} request, response: {2}", new Object[]{this.host, gname, line});
			return new LinkedHashMap<>();//we will skip this group
		}

		this.out.print("XOVER 0"+NL);
		this.out.flush();
		line = this.in.readLine();
		if (line == null || !line.startsWith("224")) { //overview follows
			Log.get().log(Level.WARNING, "From host: {0} XOVER 0 for group {1}, response: {2}", new Object[]{this.host, gname, line});
			return new LinkedHashMap<>();//we will skip this group
		}

		
		
		Map<String, String> replays = new LinkedHashMap<>(250);
		List<String> threads = new ArrayList<String>(250);
		Map<String, String> aReplTime = new HashMap<>();
		Map<String, String> aThreads = new HashMap<>();
		
		line = this.in.readLine();
		while (line != null && !(".".equals(line.trim()))) {
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

			line = this.in.readLine();
		}
		
		//500 initial capacity may be anything. 500 is rough min posts count.(just more than default 10)
		Map<String, List<String>> messageIDs;// = new LinkedHashMap<>(0); //empty for return
		
		//remove rLeft without threads
		messageIDs = FeedManager.sortThreadsReplays(threads, replays, this.host); //ordered LinkedHashMap
		
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
		int pages = Config.inst().get(Config.PAGE_COUNT, 6);
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
		
		Log.get().log(Level.INFO, "Threads to PULL: {0} ", messageIDs2.size());
		return messageIDs2;
	}

	/**
	 * Do not used in class. For PullFeeder only.
	 * 
	 * @throws IOException
	 */
	public void close(){
		try{
			if (out != null && this.in != null){
				this.out.print("QUIT\r\n");
				this.out.flush();

				//this.lastActivity = System.currentTimeMillis();
				this.in.readLine(); //we are polite
				lineBuffers.recycleBuffers();
				in.close();
				out.close();
			}
		}catch(IOException ex){}
		try{
			this.socket.close();
		}catch(IOException ex){}
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
