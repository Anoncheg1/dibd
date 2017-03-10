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
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.logging.Level;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;

import dibd.daemon.ChannelLineBuffers;
import dibd.daemon.NNTPConnection;
import dibd.daemon.NNTPInterface;
import dibd.daemon.TLS;
import dibd.daemon.command.IhaveCommand;
import dibd.storage.StorageBackendException;
import dibd.storage.GroupsProvider.Group;
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
	
	//private long lastActivity = System.currentTimeMillis();

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
		public String getHost() { //Do I need it?
			/*
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
			 */
			return null;
		}	
	}
	private Response conn = new Response(); //hook for loopback

	/**
	 * We assume that newnews will return correct ordered list.
	 *
	 * map ordered
	 *  
	 * 
	 * @param group
	 * @param last_post  mID, thread? + if true = thread, false = replay 
	 * @throws IOException
	 */
	private Map<String, Boolean> newnewsScrap(Group group, long last_post) throws IOException{
		//NEWNEWS groupname 1464210306
		//230 success
		StringBuilder buf = new StringBuilder();
		//String gs = groups.stream().map(e -> e.getName()).reduce( (e1, e2) -> e1+","+e2).get();//"group,group,group"
		buf.append("NEWTHREADS ").append(group.getName()).append(' ').append(last_post).append(NL);
		this.out.print(buf.toString());
		this.out.flush();

		//this.lastActivity = System.currentTimeMillis();
		String line = this.in.readLine();
		if (line == null || !line.startsWith("230")) { //230 List of new articles follows (multi-line)
			Log.get().log(Level.WARNING, "From host: {0} NEWTHREADS response: {1}",
					new Object[]{this.host, line});
			return new LinkedHashMap<>(); //skip group
		}

		Map<String, Boolean> messageIDs = new HashMap<String, Boolean>(0); //empty for return
		Map<String, String> replays = new LinkedHashMap<>(250);
		List<String> threads = new ArrayList<String>(250);
		
		//this.lastActivity = System.currentTimeMillis();
		line = this.in.readLine();
		while (line != null && !(".".equals(line))) {
			String[] lsplitted = line.split("\\p{Space}+");
			
			
			/*if(lsplitted[0].matches(NNTPConnection.MESSAGE_ID_PATTERN)) //article match patter
				if(lsplitted.length >= 2 && lsplitted[1].matches(NNTPConnection.MESSAGE_ID_PATTERN)) //if thread and match patter
					messageIDs.putIfAbsent(lsplitted[0], false); //replay
				else
					messageIDs.putIfAbsent(lsplitted[0], true); //thread*/
			
			//Just for case we will sort input. It is important to have no missing threads.
			
			
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
			Log.get().log(Level.SEVERE, "From host: {0} NEWNEWS for group {1}, there is over 999000 replays or threads", new Object[]{this.host, group.getName()});
			throw new IOException();
		}
		
		//sort just for case. It is important to have no missing threads.
		messageIDs = FeedManager.sortThreadsReplays(threads, replays, this.host); //ordered LinkedHashMap
		
		return messageIDs;
	}
	
	/**
	 * Get article with ARTICLE and offer it to himself with IhaveCommand class.
	 * 
	 * @param ihavec
	 * @param messageId
	 * @return true if accepted false if we don't want or remote article is missing 
	 * @throws IOException for any inappropriate input from remote host
	 * @throws StorageBackendException
	 */
	public boolean transferToItself(IhaveCommand ihavec, String messageId)
			throws IOException, StorageBackendException {
		
		String s = "IHAVE "+ messageId;
		ihavec.processLine(conn, s, s.getBytes("UTF-8")); //send ihave
	
		String line = conn.readLine();
		if (line == null || !line.startsWith("335")) {
			if (line != null && line.startsWith("435"))
				Log.get().log(Level.FINE, "IHAVE-loopback {0} we already have this or don't want it", messageId);
			else
				Log.get().log(Level.WARNING, "transferToItself from {0} {1} we ihave:{2}", new Object[]{this.host, messageId, line} );
			return false; //we don't want to receive
		}

		this.out.print("ARTICLE " + messageId + NL);
		this.out.flush();
		
		//this.lastActivity = System.currentTimeMillis();
		/**	220 0|n message-id    Article follows (multi-line)
	    *	430                   No article with that message-id
	    */
		line = this.in.readLine(); //read response for article request
		if (line == null) {
			Log.get().warning("Unexpected null ARTICLE reply from remote host " + this.host);
			throw new IOException(); //we will retry
		}else if (line.startsWith("430")) {
			Log.get().log(Level.WARNING, "Message {0} not available at {1}",
					new Object[]{messageId, this.host});
			return false;
		} else if (!line.startsWith("220")) {
			Log.get().log(Level.WARNING, "Article from {0} {1} response:{3}", new Object[]{this.host, messageId, line} );
			throw new IOException(); //we will retry
		}

		do{ //read from ARTICLE response and write to loopback IHAVE
			
			line = this.in.readLine();
			if (line == null) {
				Log.get().log(Level.WARNING, "Article from {0} {1} null line resived during reading", new Object[]{this.host, messageId} );
				return false;
			}
			//System.out.println(line);
			byte[] raw = line.getBytes(charset);
			if (raw.length >= ChannelLineBuffers.BUFFER_SIZE){ //ReceivingService.readingBody() will not add \r\n for big line
				ihavec.processLine(conn, line, raw); //send ihave
				ihavec.processLine(conn, "", new byte[]{}); //ReceivingService will add \r\n
			}else
				ihavec.processLine(conn, line, raw); //send ihave

		}while(!".".equals(line));

		//this.lastActivity = System.currentTimeMillis();
		line = conn.readLine();//IHAVE response
		if (line != null)
			if(line.startsWith("235")) {
				Log.get().log(Level.INFO, "Message {0} successfully transmitted", messageId);
				return true;
			} else
				Log.get().log(Level.WARNING, "Pulling {0} from {1} IHAVE: {2}", new Object[]{messageId, this.host, line});
			
		return false;
	}


	/**
	 * Get last post for every group,
	 * request article ids with NewNews
	 * call transferToItself for every id
	 *
	 * @param groupsTime
	 * @param pulldays - not necessary
	 * @return list of threads(true) following its replays(false) right after it.
	 * @throws IOException
	 * @throws StorageBackendException
	 */
	public Map<String, Boolean> check(final Map<Group, Long> groupsTime, int pulldays) throws StorageBackendException, IOException{
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
		if (!capabilties.contains("READER")){ //case sensitive!
			Log.get().log(Level.WARNING, "From host: {0} CAPABILITIES do not contain: READER", this.host);
			throw new IOException(); //this is too strange we will not skip
		}
		
		Map<String, Boolean> messageIDs = new LinkedHashMap<String, Boolean>();
		for(Entry<Group, Long> entry: groupsTime.entrySet()){//fer every group
			Group group = entry.getKey();
			long last_post = entry.getValue(); //time since last post in group
			if (last_post > 0 + 60*60*24)
				last_post -= 60*60*24*pulldays; // last_post = last_post - pulldays (it is not necessary)
			
			// The host is the same like we are
			if (capabilties.contains("NEWTHREADS")){//TODO:should put if absent
				messageIDs.putAll(newnewsScrap(group, last_post));//sorted threads first
			}else // The host is nntpchan
				messageIDs.putAll(oldFashionScrap(group));//only full scrap. to prevent thread partialization
				//messageIDs.addAll(oldFashionScrap(group, last_post));//sorted threads first
		}
		return messageIDs;
		
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
	 * @return
	 * @throws IOException
	 */
	private Map<String, Boolean> oldFashionScrap(final Group group) throws IOException{

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

		//500 initial capacity may be anything. 500 is rough min posts count.(just more than default 10)
		Map<String, Boolean> messageIDs = new HashMap<String, Boolean>(0); //empty for return
		
		Map<String, String> replays = new LinkedHashMap<>(250);
		List<String> threads = new ArrayList<String>(250);
		
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
				if (part.length == 5 || !part[5].matches(NNTPConnection.MESSAGE_ID_PATTERN))
					threads.add(part[4]);
				else if ( part.length == 6 && part[5].matches(NNTPConnection.MESSAGE_ID_PATTERN))
					replays.put(part[4], part[5]);
				else
					Log.get().log(Level.WARNING, "From: {0} unsupported XOVER format of line: {1}", new Object[]{this.host, line});
			}else
				Log.get().log(Level.WARNING, "From: {0} unsupported XOVER format of line: {1}", new Object[]{this.host, line});

			if(replays.size() > 999000/2 || threads.size() > 999000/2){
				Log.get().log(Level.SEVERE, "From host: {0} XOVER 0 for group {1}, there is over 999000 article lines like {2}", new Object[]{this.host, gname, line});
				throw new IOException();
			}

			line = this.in.readLine();
		}
		
		//Sort. It is important to have no missing threads.
		messageIDs = FeedManager.sortThreadsReplays(threads, replays, this.host); //ordered LinkedHashMap		

		return messageIDs;
	}

	/*private List<String> oldFashionScrap(final Group group, final long last_post) throws IOException{
		Map<String, Long> threads = new HashMap<>();
		Map<String, String> replThread = new HashMap<>(); //replay-id with thread-id
		Map<String, Long> replTime = new HashMap<>();

		String gname = group.getName();
		this.out.print("GROUP "+gname+NL);
		this.out.flush();
		String line = this.in.readLine();
		if (line == null || !line.startsWith("211")) { //Group selected
			Log.get().log(Level.WARNING, "From host: {0} GROUP {1} request, response: {2}", new Object[]{this.host, gname, line});
			return new ArrayList<>();//we will skip this group
		}

		this.out.print("XOVER 0"+NL);
		this.out.flush();
		line = this.in.readLine();
		if (line == null || !line.startsWith("224")) { //overview follows
			Log.get().log(Level.WARNING, "From host: {0} XOVER 0 for group {1}, response: {2}", new Object[]{this.host, gname, line});
			return new ArrayList<>();//we will skip this group
		}

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
			Long post_time;
			try {
				post_time = Headers.ParseRawDate(part[3]);
			} catch (ParseException e) {
				//Log.get().log(Level.WARNING, "From host: {0} XOVER 0 for group {1}, Lines have part[3] date in wrong format: {2}", new Object[]{ia.getHostString(), gname, part[3]});
				//return new ArrayList<>();//we skip this peer
				post_time = null;
			}
			
			if(part[4].matches(NNTPConnection.MESSAGE_ID_PATTERN)){
				if (part.length == 5 || !part[5].matches(NNTPConnection.MESSAGE_ID_PATTERN)){
					//replay
					
					replTime.put(part[4], post_time);
					replThread.put(part[4], part[4]);
				}else if ( part.length >= 6 && part[5].matches(NNTPConnection.MESSAGE_ID_PATTERN))
					threads.put(part[4], post_time); //we assume that post_time is not a last post time
			}else{
				Log.get().log(Level.WARNING, "ArticlePuller.oldFashionScrap: {0} cant detect message-ID in such lines: {2}", new Object[]{this.host, line});
				throw new IOException();//we skip this peer
			}
			
			line = this.in.readLine();
		}
		
		//1) get all replays and threads which date is accepted
		//2) search for all threads for accepted replays
		//3) search for all replays for accepted threads
		Map<String, String> aReplThread = new HashMap<>();//accepted
		Map<String, Long> aReplTime = new HashMap<>();
		Map<String, Long> aThreads = new HashMap<>();
		//1. replays
		Iterator it = replTime.entrySet().iterator();
		while(it.hasNext()){
			@SuppressWarnings("unchecked")
			Map.Entry<String, Long> rt = (Map.Entry<String, Long>)it.next();
			if(rt.getValue() == null || rt.getValue() >= last_post){
				aReplTime.put(rt.getKey(), rt.getValue());
				aReplThread.put(rt.getKey(), replThread.get(rt.getKey()));
				it.remove();
			}
		}
		//1. threads
		it = threads.entrySet().iterator();
		while(it.hasNext()){
			@SuppressWarnings("unchecked")
			Map.Entry<String, Long> th = (Map.Entry<String, Long>)it.next();
			if(th.getValue() == null || th.getValue() >= last_post){
				aThreads.put(th.getKey(), th.getValue());
				it.remove();
			}
		}
		
		//2.
		for(Entry<String, String> aRT : aReplThread.entrySet()){
			String threadId = aRT.getValue();//thread-id we assume it is message-id
			if (!aThreads.containsKey(threadId)) //many replays has same thread
				aThreads.put(threadId, threads.get(threadId));
		}
		//3.
		for(String aTH : aThreads.keySet()){ //for each accpeted thread
				for(Entry<String, String> rt : replThread.entrySet()) //we search replays
					//if replay in accepted and was not added before
					//if(rt.getValue().equals(aTH) && !aReplThread.containsKey(rt.getKey())){  
					if(rt.getValue().equals(aTH)){// No need to worry if was added before.
						String arep = rt.getKey();
						aReplThread.put(arep, rt.getValue());
						aReplTime.put(arep, replTime.get(arep));
					}
		}
		// we dont care about old maps 
		
		List<String> accpetedSortedMId = new ArrayList<String>(aThreads.keySet());
		accpetedSortedMId.addAll(aReplThread.keySet());
		return accpetedSortedMId;


	}*/
		
	
	/**
	 * Do not used in class. For PullFeeder only.
	 * 
	 * @throws IOException
	 */
	public void close(){
		try{
			if (out != null && in != null){
				this.out.print("QUIT\r\n");
				this.out.flush();

				//this.lastActivity = System.currentTimeMillis();
				this.in.readLine(); //we are polite
			}
		}catch(IOException ex){}
		try{
			this.socket.close();
		}catch(IOException ex){}
	}

}
