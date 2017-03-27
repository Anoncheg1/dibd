package dibd.feed;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.UnknownHostException;
import java.net.Proxy.Type;
import java.nio.charset.Charset;
import java.security.cert.X509Certificate;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.Set;
import java.util.logging.Level;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import dibd.config.Config;
import dibd.daemon.NNTPConnection;
import dibd.daemon.TLS;
import dibd.storage.GroupsProvider.Group;
import dibd.storage.StorageManager;
import dibd.storage.SubscriptionsProvider.FeedType;
import dibd.storage.SubscriptionsProvider.Subscription;
import dibd.storage.article.Article;
import dibd.util.Log;

public class FeedManager extends Thread{
	
	//TODO:create separate for every peer
	public static Proxy getProxy(Subscription sub) throws NumberFormatException, UnknownHostException{
		//proxy
		Proxy proxy;
		Proxy.Type type = sub.getProxytype();
		String adress = null;
		if (type.equals(Type.SOCKS))			
			adress = Config.inst().get(Config.PROXYSOCKS, null);
		//else if (type.equals(Type.HTTP))
			//adress = Config.inst().get(Config.PROXYHTTP, null);
    	InetSocketAddress iadr;
    	if (adress == null){
    		proxy = null;
    	}else{
    		String[] adr = adress.split(":");
    		if(adr[0].isEmpty() || adr[1].isEmpty())
    			throw new UnknownHostException("Wrong proxy configuration "+type.name());
    		iadr = new InetSocketAddress(InetAddress.getByName(adr[0]), Integer.parseInt(adr[1]));
    		proxy = new Proxy(type, iadr);
    	}
		return proxy;
	}
	
	// TODO Make configurable
	public static final int QUEUE_SIZE = 64; //queue length for every sub

	/**
	 * Fixed running push daemons list per subscription with shared queue.
	 */
	private static Map<String, List<PushDaemon>> pushDaemons= new HashMap<>();
	
	
	//Pull At Start and starting Pull daemons for missing threads
	public void run(){
		if (Config.inst().get(Config.PEERING, true)) {
			
			//1) Pull daemon for getting missing threads 
			final int pullThreadsAmount = 5; //TODO:make configurable
			for(int i = 0; i < pullThreadsAmount; i++){
        		(new PullDaemon()).start();
        	}

			
			//1) Pull new articles at startup
        	for (Subscription sub : StorageManager.peers.getAll()) {
        		if (sub.getFeedtype() != FeedType.PUSH){
        			PullAtStart pf;
        			try {
        				pf = new PullAtStart(sub);

        				pf.start(); //thread per subscription

        				pf.join();
        			} catch (UnknownHostException | NumberFormatException e) {
						Log.get().log(Level.SEVERE, "Wrong proxy configuration: {0}", e);
						break;
					} catch (InterruptedException e) {
						break;
					}
        		}
        	}
        }
	}

	
	
	
	/**
	 * Start pushing
	 * 
	 */
	public static void startPushDaemons(){
		if (Config.inst().get(Config.PEERING, true)) {
			for (Subscription sub : StorageManager.peers.getAll()) {
				if (sub.getFeedtype() == FeedType.PULL) //It is PUSH and BOTH
					continue;
				
				boolean subGood = false;
				Set<Group> groups = StorageManager.groups.groupsPerPeer(sub);
				if (groups != null)
					for (Group gr : groups)
						if (! gr.isDeleted()){ //if subscription have not deleted groups
							subGood = true;
							break;
						}
				if(subGood){
					//two threads her subscription. with shared LinkedBlockingQueue
					LinkedBlockingQueue<Article> articleQueue = new LinkedBlockingQueue<>(QUEUE_SIZE);
					List<PushDaemon> pds= Arrays.asList(new PushDaemon[]{
							new PushDaemon(sub, articleQueue), new PushDaemon(sub, articleQueue)});
					pushDaemons.put(sub.getHost(), pds);
					pds.forEach( (e) -> e.start() );
					//Log.get().info("pushDaemons for " +sub.getHost()+" started");
				}
			}
			
        }
	}

	
	/**
	 * Push to subscription.
	 * 
	 * POST, IHAVE, TAKETHIS, WEB input 
	 * 
	 * @param article
	 */
	public static void queueForPush(Article article) {
		if (Config.inst().get(Config.PEERING, false)){
			String newsgroup = article.getGroupName();
			assert(newsgroup != null);
			Group group = StorageManager.groups.get(newsgroup);
			assert( ! group.isDeleted());

			Set<String> grhosts = group.getHosts();

			if ( ! grhosts.isEmpty())
				for(String s : group.getHosts()){
					List<PushDaemon> plist = pushDaemons.get(s);
					if (plist != null){
						// Circle check: if subscribers in path, then they already have message.
						boolean check = false;
						String path = article.getPath_header();
						if (path != null)
							for(String ps : Arrays.asList(path.split("!")))
								if (ps.equalsIgnoreCase(s))
									check = true;
						if (check || article.getMsgID_host().equalsIgnoreCase(s))
							continue;

						plist.get(0).queueForPush(article); //queue shared we can use any of thread to put.
						//plist.forEach( (e) -> e.queueForPush(article));
					}

				}
		}
	}

	/**
	 * Create socket for pull or push.
	 * DLS leak for http proxy.
	 * 
	 * @param proxy my be null
	 * @param host
	 * @param port
	 * @return
	 * @throws IOException
	 */
	public static Socket createSocket(Proxy proxy, String host, int port) throws IOException{
		
		int CONNECT_TIMEOUT_MILLISECONDS = 60000;
		int READ_TIMEOUT_MILLISECONDS = 60000;
		Socket socket;
		
		if (proxy == null){
			socket = new Socket();
			socket.setSoTimeout(READ_TIMEOUT_MILLISECONDS);
			InetSocketAddress ia = new InetSocketAddress(InetAddress.getByName(host), port);
			socket.connect(ia, CONNECT_TIMEOUT_MILLISECONDS);
			
		}else if (proxy.type().equals(Proxy.Type.SOCKS)){
		
		  	socket = new Socket();
	        socket.setSoTimeout(READ_TIMEOUT_MILLISECONDS);
	        socket.connect(proxy.address(), CONNECT_TIMEOUT_MILLISECONDS);

	        DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
	        outputStream.write((byte)0x04);
	        outputStream.write((byte)0x01);
	        outputStream.writeShort((short)port);
	        outputStream.writeInt(0x01);
	        outputStream.write((byte)0x00);
	        outputStream.write(host.getBytes("US-ASCII"));
	        outputStream.write((byte)0x00);

	        DataInputStream inputStream = new DataInputStream(socket.getInputStream());
	        if (inputStream.readByte() != (byte)0x00 || inputStream.readByte() != (byte)0x5a) {
	            throw new IOException("SOCKS4a connect failed");
	        }
	        inputStream.readShort();
	        inputStream.readInt();
	        //socket is ready to use
	        return socket;
	        
		}else if (proxy.type().equals(Proxy.Type.HTTP)){//TODO:i2p?
			
			socket = new Socket(proxy);
			socket.setSoTimeout(READ_TIMEOUT_MILLISECONDS);
			//TODO: what to do with dns leak?
			InetSocketAddress ia = new InetSocketAddress(InetAddress.getByName(host), port); //DNS leak
			socket.connect(ia, CONNECT_TIMEOUT_MILLISECONDS);
			
		}else
			throw new IOException("FeedManager.createSocket: unsupported proxy type.");
			
		return socket;
	}
	
	/**
	 * Returned socket may be sslsocket if TLSEnabled.
	 * 
	 * @param socket
	 * @param TLSEnabled
	 * @param host
	 * @param charset
	 * @return
	 * @throws IOException
	 */
	public static Socket getHelloFromServer(Socket socket, boolean TLSEnabled, String host, Charset charset) throws SSLPeerUnverifiedException, IOException{
		
		PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), charset));
		BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), charset));
		
		String line = in.readLine();
		if (line == null || (!line.startsWith("200") && !line.startsWith("201") )){
			Log.get().log(Level.WARNING, "Bad Hello from host {0} : {1}",
					new Object[]{host, line});
			throw new IOException();
		}
		
		if (TLSEnabled){
			SSLSocket sslsocket = TLS.createSSLClientSocket(socket);
			
			out.print("STARTTLS"+NNTPConnection.NEWLINE);
			out.flush();
			line = in.readLine();
			if (line == null || !line.startsWith("382")) { //"382 Continue with TLS negotiation"
				Log.get().log(Level.WARNING, "From host {0} STARTTLS response: {1}",
						new Object[]{host, line});
				throw new IOException();
			}
			
			SSLSession session = sslsocket.getSession(); //handshake
			//throw exception:
			X509Certificate cert = (X509Certificate) session.getPeerCertificates()[0]; //I am not sure how to check that it is right cert. TrustManager must do it.
	        
			//ready for encrypted communication
			
			//new encrypted streams
			//this.out = new PrintWriter(new OutputStreamWriter(sslsocket.getOutputStream(), this.charset));
			//this.in = new BufferedReader(new InputStreamReader(sslsocket.getInputStream(), this.charset));
			return sslsocket;
		}else
			return socket;
		
	}
	
	/**
	 * Sort rLeft to threads. No rLeft without threads should be left.
	 * 
	 * Replays order should be save.
	 * 
	 * @param threads
	 * @param rLeft
	 * @param host just for log
	 * @return sorted threads with his rLeft followed right after it. true - thread, false - replay
	 */
	public static Map<String, List<String>> sortThreadsReplays(List<String> threads, Map<String, String> replays, String host){

		Map<String, List<String>> messageIDs = new LinkedHashMap<>(50);


		for(String th: threads){
			List<String> trepl = new ArrayList<String>();

			Iterator<Entry<String, String>> rit = replays.entrySet().iterator();

			//search rLeft for this threads
			while(rit.hasNext()){
				Map.Entry<String, String> rep = rit.next();

				if(rep.getValue().equals(th)){ //replay for this thread?
					trepl.add(rep.getKey());
					rit.remove();
				}
			}
			messageIDs.put(th, trepl);
		}


		//replays without threads. May happen for nntpchan only
		if (! replays.isEmpty()){
			StringBuilder restreplays= new StringBuilder();
			replays.entrySet().forEach(e -> restreplays.append(e).append(" "));
			Log.get().log(Level.FINE, "From: {0} NEWNEWS or XOVER replays without thread: {1}", new Object[]{host, restreplays.toString()});
		}

		return messageIDs;
	}
	
}
