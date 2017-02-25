package dibd.feed;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.UnknownHostException;
import java.net.Proxy.Type;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;

import dibd.config.Config;
import dibd.daemon.NNTPConnection;
import dibd.daemon.TLS;
import dibd.storage.StorageManager;
import dibd.storage.SubscriptionsProvider;
import dibd.storage.GroupsProvider.Group;
import dibd.storage.SubscriptionsProvider.FeedType;
import dibd.storage.SubscriptionsProvider.Subscription;
import dibd.util.Log;

public class FeedManager {
	
	private FeedManager() {
		// TODO Auto-generated constructor stub
	}
	
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
	
	/**
	 * Start pulling
	 * 
	 */
	public static void startPull(){
		if (Config.inst().get(Config.PEERING, true)) {
			//1) Pull daemon which pulling threads for replays without ones 
			final int pullThreadsAmount = 10; //TODO:make configurable
			for(int i = 0; i < pullThreadsAmount; i++){
        		(new PullDaemon()).start();
        	}
			
			
			//List<Group> groups = StorageManager.groups.getAll();
			/*List<Group> gr = new ArrayList<Group>();
			for (Group g : StorageManager.groups.getAll())
				if (g.getHosts().size() != 0)
					PullDaemon.queueForPush(g, StorageManager.current().getLastPostOfGroup(g));
			*/
			
			
			
			//Set<Subscription> subs= StorageManager.peers.getAll();
			//Set<Group> groupsp = StorageManager.groups.groupsPerPeer(sub);
			
			//1) Pull new articles at startup
        	for (Subscription sub : StorageManager.peers.getAll()) {
        		if (sub.getFeedtype() != FeedType.PUSH){
        			PullAtStart pf;
        			try {
						pf = new PullAtStart(sub);
					} catch (UnknownHostException | NumberFormatException e) {
						Log.get().log(Level.SEVERE, "Wrong proxy configuration: {0}", e);
						break;
					}
        			
        			pf.start(); //thread per subscription
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
        	//final int pushThreadsAmount = Math.max(4, 2 *
              //      Runtime.getRuntime().availableProcessors());
        	final int pushThreadsAmount = 10; //TODO:make configurable
        	for(int i = 0; i < pushThreadsAmount; i++){
        		(new PushDaemon()).start();
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
	        outputStream.write(host.getBytes());
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
	 * Returned socket may be sslsocket if TLSEnabled
	 * 
	 * @param socket
	 * @param TLSEnabled
	 * @param host
	 * @param feedtype
	 * @param charset
	 * @return
	 * @throws IOException
	 */
	public static Socket getHelloFromServer(Socket socket, boolean TLSEnabled, String host, FeedType feedtype, Charset charset) throws IOException{
		
		PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), charset));
		BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), charset));
		
		String line = in.readLine();
		if (line == null || !line.startsWith("200") || !line.startsWith("201" )){
			Log.get().log(Level.WARNING, "{0} From host {1} bad hello response: {2}",
					new Object[]{feedtype.name(), host, line});
			throw new IOException();
		}
		
		if (TLSEnabled){
			SSLSocket sslsocket = TLS.createSSLClientSocket(socket);
			
			out.print("STARTTLS"+NNTPConnection.NEWLINE);
			out.flush();
			line = in.readLine();
			if (line == null || !line.startsWith("382")) { //"382 Continue with TLS negotiation"
				Log.get().log(Level.WARNING, "{0} from host {1} STARTTLS response: {2}",
						new Object[]{feedtype.name(), host, line});
				throw new IOException();
			}
			
			SSLSession session = sslsocket.getSession(); //handshake
			
			try {
	        	X509Certificate cert = (X509Certificate) session.getPeerCertificates()[0]; //I am not sure how to check that it is right cert
	        } catch (SSLPeerUnverifiedException e) {
	        	Log.get().log(Level.WARNING, "{0} From host: {1} TLS did not present a valid certificate",
	        			new Object[]{feedtype.name(), host});
	        	throw new IOException();
	        }
			//ready for encrypted communication
			
			//new encrypted streams
			//this.out = new PrintWriter(new OutputStreamWriter(sslsocket.getOutputStream(), this.charset));
			//this.in = new BufferedReader(new InputStreamReader(sslsocket.getInputStream(), this.charset));
			return sslsocket;
		}else
			return socket;
		
	}

}
