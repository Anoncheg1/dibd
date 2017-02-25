/**
 * 
 */
package dibd.daemon;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.Socket;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import dibd.config.Config;
import dibd.util.Log;

/**
 * @author user
 *
 */
public class TLS{

	private static SSLContext sslContext = null;

	private static final String selfChainStore = "SelfKeyStore";
	private static final String certpassword = "password";

	public static boolean isContextCreated(){
		return sslContext != null;
	}
	
	/**
	 * Used for peering tls connection.
	 * Prepare socket for handshake negotiation.
	 * 
	 * @param socket
	 * @return
	 * @throws IOException
	 */
	public static SSLSocket createSSLClientSocket(Socket socket) throws IOException{
		//autoClose - close the underlying socket when this socket is closed.
		SSLSocket sslsocket = (SSLSocket) sslContext.getSocketFactory().createSocket(socket, null, true);
        sslsocket.setUseClientMode(true);//client
        sslsocket.setNeedClientAuth(true);
        return sslsocket;
	}

	/**
	 * Read keys or generate ones.
	 * Then create SSLContext for further usage.
	 * Must be call at main for initialization of TLS.
	 * 
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws KeyStoreException
	 * @throws NoSuchAlgorithmException
	 * @throws CertificateException
	 * @throws UnrecoverableKeyException
	 * @throws KeyManagementException
	 */
	public static void createTLSContext() throws IOException, InterruptedException, KeyStoreException, NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException, KeyManagementException{
		String name = Config.inst().get(Config.HOSTNAME, null);
		//keytool -genkey -keyalg RSA -alias selfsigned -keystore keystore.jks -storepass password -keypass password -validity 360 -keysize 2048 -dname "CN=s, OU=f, O=g, L=h, ST=j, C=a"
		//keytool -exportcert -alias selfsigned -rfc -keystore keystore.jks -storepass password -file cert

		//String userDetails = "CN=" + name + ", OU=FCT, O=UNL, L=Unknown, ST=Unknown, C=PT";
		String userDetails = "CN=" + name +", O=overchan";
		String certValidity = "" + 365; //1 years
		String genkeypair[] = { "keytool", "-genkeypair", "-alias",
				name, "-keystore", selfChainStore,
				"-keypass", certpassword, "-storepass", certpassword,
				"-keyalg", "RSA", "-keysize", "4096", "-dname",
				userDetails, "-validity", certValidity, "-ext", "EKU=serverAuth,clientAuth","-ext","BC:critical=ca:true" };


		String selfPubCrt = name+"-public.crt";
		String peersCrtDir = "peersTLSCertificates/";

		//Check if two keystores exist and generate or export public key
		//1) Generate SelfChainKeyStore and export self public key
		if (!new File(selfChainStore).exists()){

			//System.out.println(Arrays.asList(keytoolArgs));
			Process p1 = Runtime.getRuntime().exec(genkeypair);
			p1.waitFor();
			if (p1.exitValue() != 0) { //fail
				BufferedReader br=new BufferedReader(new InputStreamReader(p1.getInputStream()));
				Log.get().log(Level.WARNING, "Fail to generate key pair for self: {0}", br.readLine());
			}
		}

		//2) Export self public key to PEM file
		if (new File(selfChainStore).exists()){
			new File (selfPubCrt).delete();
			//export sertificate to PEM file
			String exportselfcrt[] = { "keytool", "-exportcert", "-alias",
					name, "-keystore", selfChainStore,
					"-keypass", certpassword, "-storepass", certpassword,
					"-rfc", "-file", selfPubCrt };
			//System.out.println(Arrays.asList(keytoolArgs2));
			Process p2 = Runtime.getRuntime().exec(exportselfcrt);
			p2.waitFor();
			if (p2.exitValue() != 0){//fail
				BufferedReader br=new BufferedReader(new InputStreamReader(p2.getInputStream()));
				Log.get().log(Level.WARNING, "Fail to export self key to PEM file: {0}", br.readLine());
			}
		}

		//export private key to pem
		//keytool -importkeystore -srckeystore keystore.jks -destkeystore pr.p12 -deststoretype PKCS12
		//openssl pkcs12 -in keystore.p12  -nodes -nocerts -out key.pem

		//check peers public cert dir	        
		File pCrtDir = new File(peersCrtDir);
		//List<InputStream> inputStreams = new ArrayList<>();
		File[] pubkeys = null;
		if (!pCrtDir.exists()){//not existed
			pCrtDir.mkdir();
		}else{//folder exist

			//3) Create Peers KeyStore by import of cert files

			//keytool -importcert -file overchan-myhost.com.crt -alias publ -keypass password -keystore keystore.jks -storepass password -noprompt
			/*String importPubCrt[] = { "keytool", "-importcert", "-file", "volatFile",	//3 volatile
        			"-alias", "volatName", "-keystore", peersPubKeyStore,				//5 volatile
        			"-keypass", certpassword, "-storepass", certpassword, "-noprompt"};*/

			pubkeys= pCrtDir.listFiles();

			//if (pubkeys != null && pubkeys.length != 0) //for every key import
			//for (File peerCrt : pubkeys){
				//inputStreams.add(new FileInputStream(peerCrt));
				/*
        			importPubCrt[3] = peerCrt.getPath();
        			importPubCrt[5] = peerCrt.getName();
        			Process p3 = Runtime.getRuntime().exec(importPubCrt);
        			p3.waitFor();
        			if (p3.exitValue() != 0){//fail
        				BufferedReader br=new BufferedReader(new InputStreamReader(p3.getInputStream()));
        				System.out.println(br.readLine());
        			}*/
			//}
		}


		//Init SSLContext
		if (new File(selfChainStore).exists() && pubkeys != null){

			KeyStore ksKeys = KeyStore.getInstance(KeyStore.getDefaultType());
			ksKeys.load(new FileInputStream(selfChainStore), certpassword.toCharArray());
			KeyStore ksTrust = KeyStore.getInstance(KeyStore.getDefaultType());
			//ksTrust.load(new FileInputStream(peersPubKeyStore), certpassword.toCharArray());
			ksTrust.load(null);
			for (File peerkey : pubkeys) {
				X509Certificate cert;
				InputStream inputStream = new FileInputStream(peerkey);
				try {
					CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
					cert = (X509Certificate)certificateFactory.generateCertificate(inputStream);
				}catch(CertificateException e){
					Log.get().log(Level.WARNING, "Can not parse peer certificate: {0}", peerkey.getName());
					continue;
				} finally {
					inputStream.close();
				}
				String alias = cert.getSubjectX500Principal().getName();
				ksTrust.setCertificateEntry(alias, cert);
			}

			// KeyManagers decide which key material to use
			KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
			kmf.init(ksKeys, certpassword.toCharArray());


			// TrustManagers decide whether to allow connections
			TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
			tmf.init(ksTrust);

			sslContext = SSLContext.getInstance("TLS");
			//X509ExtendedTrustManager
			sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new java.security.SecureRandom());
			

		}else
			throw new IOException("TLS.createTLSContext() there is no selfChainStore or peers sertificates.");




	}





	private final NNTPConnection conn;
	private final ChannelLineBuffers lineBuffers; //buffers of conn
	private final SocketChannel socketChannel;
	private final SSLEngine engine; 

	private ByteBuffer outNetBB; //ready for output // doHandshake and writeTLS
	private ByteBuffer inNetBB; //ready for input //doHandshake  and readTLS
	private ByteBuffer inAppBB; //readu for input
	private ByteBuffer hsBB;

	private boolean initialHSComplete; // Handshake complete status

	private HandshakeStatus initialHSStatus;
	
	private String[] peerNames;

	/**
	 * It can be accessed ONLY AFTER TLS initialization AND TLS.connect(); 
	 * 
	 * @return
	 */
	public String[] getPeerNames() {
		assert(this.peerNames != null && this.peerNames.length != 0);
		for(String s : peerNames)
			System.out.println("TLS.getPeerNames():"+s);
		return peerNames;
	}

	/**
	 * Connect.
	 * TLS class must be statically initialized before.
	 * 
	 * @param conn
	 * @throws IOException
	 */
	public TLS(final NNTPConnection conn){
		assert(TLS.sslContext != null); //TLS class must be statically initiated first.
		
		this.conn = conn;
		this.lineBuffers = conn.getBuffers();
		this.socketChannel = conn.getSocketChannel();

		// Create the engine
		this.engine = sslContext.createSSLEngine();
		this.engine.setUseClientMode(false);//server
		this.engine.setNeedClientAuth(true);

	}



	/**
	 * 	<PRE>
	 *               Application Data
	 *             src(hsBB)   inAppBB
	 *                |           ^
	 *                |     |     |
	 *                v     |     |
	 *           +----+-----|-----+----+
	 *           |          |          |
	 *           |       SSL|Engine    |
	 *   wrap()  |          |          |  unwrap()
	 *           | OUTBOUND | INBOUND  |
	 *           |          |          |
	 *           +----+-----|-----+----+
	 *                |     |     ^
	 *                |     |     |
	 *                v           |
	 *            outNetBB     inNetBB
	 *                   Net data
	 * </PRE>
	 */
	/**
	 * Connect, handshake, certificate check.
	 * If success TLS 
	 * Return host Common Name field in the Subject field
	 * 
	 * @return peer DNS(name) if success or null if fail
	 * @throws IOException
	 */
	public boolean connect() throws IOException {

		//NNTPConnection conn2 = (NNTPConnection) conn;
		//SocketChannel socketChannel = conn2.getSocketChannel();
		//READ LOCK
		//System.out.println("h1");
		//while(!conn.tryReadLock()){};
		//System.out.println("h2");
		//Switch ChannelReader and ChannelWriter to SSL reader and writer and set write key to NNTPConnect
		/*Selector readSelector = ChannelReader.getInstance().getSelector();
		socketChannel.keyFor(readSelector).cancel();
		//socketChannel.keyFor(readSelector).interestOps(0);//for Read we can temporarily disable Operations.
		Selector writeSelector = ChannelWriter.getInstance().getSelector();
		socketChannel.keyFor(writeSelector).cancel();*/
		//socketChannel.configureBlocking(true);


		/*SSLSocket sslsocket = (SSLSocket) sslContext.getSocketFactory().createSocket(socket, null, false);
	        sslsocket.setUseClientMode(false);//server
	        sslsocket.setNeedClientAuth(true);*/

		



		// Create the engine
		//SSLEngine engine = sslContext.createSSLEngine();
		//engine.setUseClientMode(false);//server
		//engine.setNeedClientAuth(true);
		//conn.setSSLEngine(engine);




		//do handshake

		//preparing outNetBB must be ready for output
		this.outNetBB = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
		this.outNetBB.position(0);//blocked for first
		this.outNetBB.limit(0);//blocked for first
		//System.out.println("pos"+outNetBB.position()+"lim"+outNetBB.limit());

		//preparing inNetBB
		this.inNetBB = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
		
		//ByteBuffer inputBuffer = lineBuffers.getInputBuffer();
		//inputBuffer.flip();//set ready to output
		//System.out.println("TLS.connect remains:"+inputBuffer.remaining());
		/*this.inNetBB.put(inputBuffer); //add received data right after STARTTLS request(just for case)*/
		//inputBuffer.clear();//ready for put again
		
		//preparing inAppBB
		this.inAppBB = ByteBuffer.allocate(engine.getSession().getApplicationBufferSize());
		//preparing handshake 0 buffer
		this.hsBB = ByteBuffer.allocate(0);
		initialHSComplete = false;
		initialHSStatus = HandshakeStatus.NEED_UNWRAP; //server

		//System.out.println("start handshake");

		Selector selector = null;
		SelectionKey sk = null;
		//	Selector selector = Selector.open();
		//SelectionKey sk = socketChannel.register(selector, SelectionKey.OP_READ);
		//socketChannel.register(selector, SelectionKey.OP_READ, null);

		//final Object gate = new Object();
		while(initialHSComplete != true){
			System.out.println("in while1");
			if (selector == null){
				selector = Selector.open();
				sk = socketChannel.register(selector, SelectionKey.OP_READ);		
			}else
				selector.select();
			System.out.println("in while2");
			try{
				doHandshake(sk);
			}catch(SSLHandshakeException ex){
				Log.get().log(Level.WARNING, "TLS.connect, fail in handshake: {0}", ex);
				conn.close();
				return false;
			}
			//System.out.println("while state: "+initialHSComplete);
			//synchronized (gate) {}
		}
		/*if (engine.getHandshakeStatus() == HandshakeStatus.NOT_HANDSHAKING)
				System.out.println("WTF NOT HANDSHAKED!");*/




		SSLSession session = engine.getSession();

		//SSLSession session = sslsocket.getSession();

		X509Certificate cert;
		try {
			cert = (X509Certificate) session.getPeerCertificates()[0];
		} catch (SSLPeerUnverifiedException e) {
			Log.get().log(Level.WARNING, "TLS.connect, did not present a valid certificate: {0}", e);
			return false;
		}

		System.out.println("Peer host is " + session.getPeerHost());
		System.out.println("Peer port is " + session.getPeerPort());
		System.out.println("Cipher is " + session.getCipherSuite());
		System.out.println("Protocol is " + session.getProtocol());
		System.out.println("ID is " + new BigInteger(session.getId()));
		System.out.println("Session created in " + session.getCreationTime());
		System.out.println("Session accessed in " + session.getLastAccessedTime());
		System.out.println("cert.getSigAlgName()"+cert.getSigAlgName());
		//System.out.println("cert"+cert.);
		//Remote Entity verification for every send receive data -man-in-middle
		//Are we secure?
		//https://docs.oracle.com/javase/8/docs/technotes/guides/security/jsse/JSSERefGuide.html#ciphersuitechoice
		  /* RFC 2818 HTTP Over TLS
		   * If a subjectAltName extension of type dNSName is present, that MUST
		   be used as the identity. Otherwise, the (most specific) Common Name
		   field in the Subject field of the certificate MUST be used. Although
		   the use of the Common Name is existing practice, it is deprecated and
		   Certification Authorities are encouraged to use the dNSName instead.*/
		//AltNames firts
		Collection<List<?>> altNames = null;
		try {
			altNames= cert.getSubjectAlternativeNames();
		} catch (CertificateParsingException e) {
			Log.get().log(Level.WARNING, "TLS.connect, error in parsing Subject AlternativeNames: {0}", e);
		}
		if (altNames != null){
			LinkedList<String> subjectAltList = new LinkedList<String>();
			for (List<?> aC : altNames) {
				//https://docs.oracle.com/javase/8/docs/api/java/security/cert/X509Certificate.html#getSubjectAlternativeNames--
				int type = ((Integer) aC.get(0)).intValue();

				if (type == 2) {//2- DNS 7- IP
					String s = (String) aC.get(1);
					subjectAltList.add(s);
				}
			}
			if(!subjectAltList.isEmpty()) {
				String[] subjectAlts = new String[subjectAltList.size()];
				subjectAltList.toArray(subjectAlts);
				
				this.peerNames = subjectAlts;
				return true;
			}
		}
		//}else{
		//Common Name second
		Matcher m = Pattern.compile("CN=([^,]+)").matcher(cert.getSubjectX500Principal().getName());
		if(m.find()){
			/*rfc4642 NNTP-TLS 
			 * The server MUST discard any
			   knowledge obtained from the client, such as the current newsgroup and
			   article number, that was not obtained from the TLS negotiation
			   itself.*/	
			conn.setCurrentArticle(null);
			conn.setCurrentGroup(null);
			conn.updateLastActivity();
			
			this.peerNames = new String[]{m.group(1)}; //TODO: why many? one name here?
			return true;
		}
		else{
			Log.get().log(Level.WARNING, "TLS.connect, no Common Name field in the Subject field");
			return false;
		}

		//System.out.println("Peer port is " + session.getPeerPort());
		//System.out.println("Cipher is " + session.getCipherSuite());
		//System.out.println("Protocol is " + session.getProtocol());
		//System.out.println("ID is " + new BigInteger(session.getId()));
		//System.out.println("Session created in " + session.getCreationTime());
		//
		



		//Good finish

		//socketChannel.configureBlocking(false);
		//conn.setTLS(this); //now ChannelReader will work different
		/*
		SelectionKey selKeyWrite = NNTPDaemon.registerSelector(writeSelector,
				socketChannel, SelectionKey.OP_WRITE);
		NNTPDaemon.registerSelector(readSelector, socketChannel,
				SelectionKey.OP_READ);
		//socketChannel.keyFor(readSelector).interestOps(SelectionKey.OP_READ);
		//readSelector.wakeup();
		Log.get().log(
				Level.INFO, "Connected: {0}", socketChannel.socket()
				.getRemoteSocketAddress());*/
		//conn.unlockReadLock();


		//conn.setWriteSelectionKey(selKeyWrite);
		
	}


	public boolean tryFlush(ByteBuffer bb) throws IOException {
		socketChannel.write(bb);
		return !bb.hasRemaining();
	}


	public SSLEngineResult.HandshakeStatus doTasks() {
		synchronized(this.engine){

			Runnable runnable;

			/*
			 * We could run this in a separate thread, but
			 * do in the current for now.
			 */
			while ((runnable = this.engine.getDelegatedTask()) != null) {
				runnable.run();
			}
			return this.engine.getHandshakeStatus();
		}
	}





	@SuppressWarnings("incomplete-switch")
	private boolean doHandshake(SelectionKey sk) throws IOException, SSLHandshakeException {

		SSLEngineResult result;

		if (initialHSComplete) {
			return initialHSComplete;
		}

		/*
		 * Flush out the outgoing buffer, if there's anything left in
		 * it.
		 */
		if (outNetBB.hasRemaining()) {

			if (!tryFlush(outNetBB)) {
				return false;
			}

			// See if we need to switch from write to read mode.
			switch (initialHSStatus) {

			// Is this the last buffer?
			case FINISHED:
				initialHSComplete = true;
				// Fall-through to reregister need for a Read.

			case NEED_UNWRAP:
				if (sk != null) {
					sk.interestOps(SelectionKey.OP_READ);
				}
				break;
			}

			return initialHSComplete;
		}

		
		switch (initialHSStatus) {

		case NEED_UNWRAP:
			if (socketChannel.read(inNetBB) == -1) {
				engine.closeInbound();
				return initialHSComplete;
			}

needIO:
				while (initialHSStatus == HandshakeStatus.NEED_UNWRAP) {

					inNetBB.flip();//ready for output
					result = engine.unwrap(inNetBB, inAppBB);
					inNetBB.compact();
					System.out.println("result"+result);
					
					initialHSStatus = result.getHandshakeStatus();

					switch (result.getStatus()) {

					case OK:
						switch (initialHSStatus) {
						case NOT_HANDSHAKING:
							throw new IOException(
									"Not handshaking during initial handshake");

						case NEED_TASK:
							initialHSStatus = doTasks();
							break;

						case FINISHED:
							initialHSComplete = true;
							break needIO;
						}

						break;

					case BUFFER_UNDERFLOW:
						// Resize buffer if needed.
						int netBBSize = engine.getSession().getPacketBufferSize();
						if (netBBSize > inNetBB.capacity()) {
							resizeInNetBB(netBBSize);
						}

						/*
						 * Need to go reread the Channel for more data.
						 */
						if (sk != null) {
							sk.interestOps(SelectionKey.OP_READ);
						}
						break needIO;

					case BUFFER_OVERFLOW:
						// Reset the application buffer size.
						int newInAppSize = engine.getSession().getApplicationBufferSize();
						if (inAppBB.remaining() < newInAppSize)
							resizeInAppBB(newInAppSize);    // expected room for unwrap
						break;

					default: //CLOSED:
						throw new IOException("Received" + result.getStatus() +
								"during initial handshaking");
					}
				}  // "needIO" block.
			System.out.println("after needIO "+initialHSStatus+" sk:"+(sk.interestOps()==SelectionKey.OP_READ));
			/*
			 * Just transitioned from read to write.
			 */
			if (initialHSStatus != HandshakeStatus.NEED_WRAP) {
				break;
			}

			// Fall through and fill the write buffers.

		case NEED_WRAP:
			/*
			 * The flush above guarantees the out buffer to be empty
			 */
			outNetBB.clear();
			result = engine.wrap(hsBB, outNetBB);
			outNetBB.flip();

			initialHSStatus = result.getHandshakeStatus();
			//			System.out.println("result wrap="+result);
			switch (result.getStatus()) {
			case OK:

				if (initialHSStatus == HandshakeStatus.NEED_TASK) {
					initialHSStatus = doTasks();
				}

				if (sk != null) {
					sk.interestOps(SelectionKey.OP_WRITE);
				}

				break;

			default: // BUFFER_OVERFLOW/BUFFER_UNDERFLOW/CLOSED:
				throw new IOException("Received" + result.getStatus() +
						"during initial handshaking");
			}
			break;

		default: // NOT_HANDSHAKING/NEED_TASK/FINISHED
			throw new RuntimeException("Invalid Handshaking State" +
					initialHSStatus);
		} // switch

		return initialHSComplete;
	}



	/**
	 * Try to transfer date from InAppBB to ChannelLineBuffers.inputBuffer 
	 * If inputBuffer don't have enough free space inAppBB will have  
	 *
	 *Return bytes cleared.
	 *
	 * @return
	 */
	private int clearInAppBB() {
		inAppBB.flip(); //ready for output
		int tmpSize =0;
		if (inAppBB.hasRemaining()){
			ByteBuffer inputBuffer = lineBuffers.getInputBuffer();
			tmpSize = inAppBB.remaining() < inputBuffer.remaining() ?
					inAppBB.remaining() : inputBuffer.remaining(); //the lesser of two
			byte[] tmp = new byte[tmpSize];
			try{
				inAppBB.get(tmp);//may left something
				inputBuffer.put(tmp);
			}catch(BufferOverflowException|BufferUnderflowException ex){
				Log.get().log(Level.SEVERE, ex.getLocalizedMessage(), ex);
				conn.close();
			}
			inAppBB.compact(); //ready for in again
		}else
			inAppBB.clear();
		
		return tmpSize;
		
	}


	/**
	 * READ
	 * 
	 * 
	 * 	<PRE>
	 *               Application Data
	 *             src(hsBB)   inAppBB
	 *                |           ^
	 *                |     |     |
	 *                v     |     |
	 *           +----+-----|-----+----+
	 *           |          |          |
	 *           |       SSL|Engine    |
	 *   wrap()  |          |          |  unwrap()
	 *           | OUTBOUND | INBOUND  |
	 *           |          |          |
	 *           +----+-----|-----+----+
	 *                |     |     ^
	 *                |     |     |
	 *                v           |
	 *            outNetBB     inNetBB
	 *                   Net data
	 * </PRE>
	 * @throws IOException 
	 */
	int readTLS() throws IOException{

		/*
				Log.get().log(Level.WARNING,
						"ChannelReader.unwrapTLS(): ApplicationBufferSize > BUFFER_SIZE*2 for {0}",
						sslEngine.getSession().getPeerHost());*/

		SSLEngineResult result;
		int processed = 0; //return value
		
		if (!initialHSComplete) {
            throw new IllegalStateException();
        }
		
		System.out.println("WE READ TLS");

		 int n = socketChannel.read(inNetBB);
		System.out.println("ChannelReader.unwrap readed:"+n);
		//if (socketChannel.read(inNetBB) == -1) {
		if (n == -1) {
			// The channel has reached end-of-stream
			try {
				engine.closeInbound(); // probably throws exception if connection closed
			} catch (SSLException e) {
				Log.get().log(Level.INFO, "Connection with TLS closed by: {0}", socketChannel.getRemoteAddress());
				shutdown();
				ConnectionCollector.getInstance().get(socketChannel).close();
			}  
			return -1;
		} else {

			//unwrap
			do {
				
				inNetBB.flip();//prepare for output
				
				//TODO:checkit
				/*
				if (engine.getHandshakeStatus() == HandshakeStatus.NOT_HANDSHAKING){
					throw new Error("readTLS NOT_HANDSHAKING");
				}*/
				result = engine.unwrap(inNetBB, inAppBB); //UNWRAP
				inNetBB.compact(); //reset as it was minus consumed

				switch (result.getStatus()) {
				case OK:
					processed += clearInAppBB(); //for call ConnectionWorker;
					if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
						doTasks(); //do task. I don't knew when it can be required.
					}
					
					break;

				case BUFFER_OVERFLOW:
					System.out.println("readTLS BUFFER_OVERFLOW bytes consumed:"+result.bytesConsumed());
					// Reset application data buffer.
					int newAppSize = engine.getSession().getApplicationBufferSize();
					if (newAppSize > inAppBB.capacity())
						resizeInAppBB(newAppSize);
					else
						processed += clearInAppBB();
					// retry the operation
					break;

				case BUFFER_UNDERFLOW:
					// Reset peer network packet buffer. Buffers was not modified. Just need to reed more.
					int newNetSize = engine.getSession().getPacketBufferSize();
					if (newNetSize > inNetBB.capacity())
						this.resizeInNetBB(newNetSize);
					// obtain more inbound network data and then retry the operation
					//it is normal
					break; //next read will support larger buffer.

					// Handle other status: CLOSED, OK
					//...
				case CLOSED:
					System.out.println("ChannelReader.unwrapTLS state CLOSED");
					//TODO:do something
					break;
				default:
					throw new IOException("sslEngine error during data read: " +
							result.getStatus());
				}

			} while ((inNetBB.position() != 0) &&
					result.getStatus() != Status.BUFFER_UNDERFLOW);
		}
		
		System.out.println("End of readTLS");
		System.out.println("outNetBB pos:"+outNetBB.position()+" "+" lim:"+outNetBB.limit()+" cap"+outNetBB.capacity());
		System.out.println("inNetBB pos:"+inNetBB.position()+" "+" lim:"+inNetBB.limit()+" cap"+inNetBB.capacity());
		System.out.println("inAppBB pos:"+inAppBB.position()+" "+" lim:"+inAppBB.limit()+" cap"+inAppBB.capacity());
		ByteBuffer inputBuffer = lineBuffers.getInputBuffer();
		System.out.println("inputBuffer pos:"+inputBuffer.position()+" "+" lim:"+inputBuffer.limit()+" cap"+inputBuffer.capacity());
		
		
		System.out.println("readTLS processed:"+ processed);
		return processed;

	}
	
	
	
	
	
	
	/*
	 * WRITE
	 * 
	 * 
	 * 	<PRE>
	 *               Application Data
	 *             src(hsBB)   inAppBB
	 *                |           ^
	 *                |     |     |
	 *                v     |     |
	 *           +----+-----|-----+----+
	 *           |          |          |
	 *           |       SSL|Engine    |
	 *   wrap()  |          |          |  unwrap()
	 *           | OUTBOUND | INBOUND  |
	 *           |          |          |
	 *           +----+-----|-----+----+
	 *                |     |     ^
	 *                |     |     |
	 *                v           |
	 *            outNetBB     inNetBB
	 *                   Net data
	 * </PRE>
	 * 
	 * Try to flush out any existing outbound data, then try to wrap
	 * anything new contained in the src buffer.
	 * <P>
	 * Return the number of bytes actually consumed from the buffer,
	 * but the data may actually be still sitting in the output buffer,
	 * waiting to be flushed.
	 */
	void writeTLS(ByteBuffer src) throws IOException {

		//ByteBuffer outNetBB = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
		//outNetBB.clear();


		do{ // There is data to be send "src"

			//first flush
			System.out.println("wtiteTLS flush1 "+outNetBB.remaining());
			if (outNetBB.hasRemaining() && !tryFlush(outNetBB)) {
				System.out.println("first flush2");
				//return retValue;
				break;//and return
			}
			System.out.println("WRITE wrap "+src.remaining());

			/*
			 * The data buffer is empty, we can reuse the entire buffer.
			 */
			outNetBB.clear();
			//System.out.println("2p :"+src.position()+" src.lim:"+src.limit());
			//src position will be src.oldpos+result.consumed
			SSLEngineResult result = engine.wrap(src, outNetBB); //wrap

			outNetBB.flip();

			switch (result.getStatus()) {

			case OK:
				//System.out.println("2p :"+src.position()+" src.lim:"+src.limit()+" consumed:"+result.bytesConsumed());
				//src.position(src.position()+result.bytesConsumed());
				if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
					doTasks(); //do task. I don't knew when it can be required.
				}
				break;

			default:
				throw new IOException("sslEngine error during data write: " +
						result.getStatus());
			}

			/*
			 * Try to flush the data, regardless of whether or not
			 * it's been selected.  Odds of a write buffer being full
			 * is less than a read buffer being empty.
			 */
			System.out.println("second first flush1 "+outNetBB.remaining());
			if (outNetBB.hasRemaining()) {
				tryFlush(outNetBB);
			}

			src = lineBuffers.getOutputBuffer();//next data

		}while (src != null);
		
		
		
	}



	private void resizeInNetBB(int netBBSize) {
		ByteBuffer bb = ByteBuffer.allocate(netBBSize);
		inNetBB.flip();
		bb.put(inNetBB);
		inNetBB = bb;
	}

	private void resizeInAppBB(int appBBsize) {
		// Expand buffer for large request
		ByteBuffer bb = ByteBuffer.allocate(inAppBB.capacity() * 2);
		inAppBB.flip();
		bb.put(inAppBB);
		inAppBB = bb;
	}

	public void shutdown() throws IOException {

		//if (!shutdown) {
		//            sslEngine.closeOutbound();
		//  shutdown = true;
		//}

		if (outNetBB.hasRemaining() && tryFlush(outNetBB)) {
			//return false;
		}

		/*
		 * By RFC 2616, we can "fire and forget" our close_notify
		 * message, so that's what we'll do here.
		 */
		for(int i = 0; i<3 && !engine.isInboundDone(); i++){ //3 retry

			engine.closeOutbound();
			outNetBB.clear();
			SSLEngineResult result = engine.wrap(hsBB, outNetBB);
			if (result.getStatus() != Status.CLOSED) {
				throw new SSLException("Improper close state");
			}
			outNetBB.flip();

			/*
			 * We won't wait for a select here, but if this doesn't work,
			 * we'll cycle back through on the next select.
			 */
			if (outNetBB.hasRemaining()) {
				tryFlush(outNetBB);
			}
		}
		conn.setTLS(null);

	}


}