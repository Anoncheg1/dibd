package dibd.test;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.Console;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.mail.MessagingException;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeUtility;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;




public class TLSStandAloneTest{

	/** This holds the certificate of the CA used to sign the new certificate. The object is created in the constructor. 
	 * @throws KeyStoreException 
	 * @throws CertificateException 
	 * @throws UnrecoverableEntryException */
//	private X509Certificate caCert;

	
	private static final String selfChainStore = "SelfKeyStore";
	private static final String peersPubKeyStore = "TrustedKeyStore";//Public Trusted Certificates
	private static final String certpassword = "password";
	private static final String name = "test.com";
	
	private static void TLS() throws IOException, InterruptedException{
		
		
        
        
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
        	//

        	//1)Gen if not exist.    
        	//System.out.println(Arrays.asList(keytoolArgs));
        	Process p1 = Runtime.getRuntime().exec(genkeypair);
        	p1.waitFor();
        	if (p1.exitValue() != 0){ //fail
        		BufferedReader br=new BufferedReader(new InputStreamReader(p1.getInputStream()));
        		//String line =null;
        		//while((line=br.readLine())!=null)
        		System.out.println(br.readLine());

        	}
        	if (!new File(selfPubCrt).exists()){
        		//export sertificate to PEM file
        		String keytoolArgs2[] = { "keytool", "-exportcert", "-alias",
        				name, "-keystore", selfChainStore,
        				"-keypass", certpassword, "-storepass", certpassword,
        				"-rfc", "-file", selfPubCrt };
        		//System.out.println(Arrays.asList(keytoolArgs2));
        		Process p2 = Runtime.getRuntime().exec(keytoolArgs2);
        		p2.waitFor();
        		if (p2.exitValue() != 0){//fail
        			BufferedReader br=new BufferedReader(new InputStreamReader(p2.getInputStream()));
        			//String line =null;
        			//while((line=br.readLine())!=null)
        			System.out.println(br.readLine());
        		}
        	}
        }
        
        
        //check peers public cert dir	        
        File pCrtDir = new File(peersCrtDir);
        if (!pCrtDir.exists()){//not existed
        	pCrtDir.mkdir();
        }else{//folder exist

        	//2) Create Peers KeyStore by import of cert files
        	
        	//keytool -importcert -file overchan-myhost.com.crt -alias publ -keypass password -keystore keystore.jks -storepass password -noprompt
        	String importPubCrt[] = { "keytool", "-importcert", "-file", "volatFile",	//3 volatile
        			"-alias", "volatName", "-keystore", peersPubKeyStore,				//5 volatile
        			"-keypass", certpassword, "-storepass", certpassword, "-noprompt"};

        	File[] pubkeys= pCrtDir.listFiles();
        	if (pubkeys != null && pubkeys.length != 0) //for every key import
        		for (File peerCrt : pubkeys){
        			importPubCrt[3] = peerCrt.getPath();
        			importPubCrt[5] = peerCrt.getName();
        			Process p3 = Runtime.getRuntime().exec(importPubCrt);
        			p3.waitFor();
        			if (p3.exitValue() != 0){//fail
        				BufferedReader br=new BufferedReader(new InputStreamReader(p3.getInputStream()));
        				System.out.println(br.readLine());
        			}
        		}
        }
	}
	

	
	
	
	
public static boolean connect() throws KeyStoreException, NoSuchAlgorithmException, CertificateException, FileNotFoundException, IOException, UnrecoverableKeyException, InterruptedException{
		

		if (new File(selfChainStore).exists() && new File(peersPubKeyStore).exists()){
			
			//synchronized(socketChannel.blockingLock()){
			
			
			

	        KeyStore ksKeys = KeyStore.getInstance(KeyStore.getDefaultType());
	        ksKeys.load(new FileInputStream(selfChainStore), certpassword.toCharArray());
	        KeyStore ksTrust = KeyStore.getInstance(KeyStore.getDefaultType());
	        ksTrust.load(new FileInputStream(peersPubKeyStore), certpassword.toCharArray());
	        

	        // KeyManagers decide which key material to use
	        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
	        kmf.init(ksKeys, certpassword.toCharArray());
	        

	        // TrustManagers decide whether to allow connections
	        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
	        //TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
	        tmf.init(ksTrust);
	        System.out.println("provider:"+tmf.getProvider());
	        

	        SSLContext sslContext = SSLContext.getInstance("TLS");
	        //sslContext.set
	        for (TrustManager tm: tmf.getTrustManagers()){
	        	X509ExtendedTrustManager tm2 = (X509ExtendedTrustManager)tm;
	        	
	        }
	        try {
	        	sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new java.security.SecureRandom());
	        	//sslContext.getSocketFactory().createSocket(s, host, port, autoClose);
	        } catch (KeyManagementException e1) {
	        	// TODO Auto-generated catch block
	        	e1.printStackTrace();
	        }
	        
	        
	        
	        SocketChannel socketChannel = SocketChannel.open(new InetSocketAddress(InetAddress.getByName("main.com"),7119));
	        Socket socket = socketChannel.socket();
	        //socket.setSoTimeout(20000);//20 sec
	        
	        Charset charset = Charset.forName("UTF-8");
	        PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), charset));
	        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), charset));
	        
	        //sslContext.
	        SSLSocket sslsocket = (SSLSocket) sslContext.getSocketFactory().createSocket(socket, null, false);
	        System.out.println(sslContext.getSocketFactory().createSocket(socket, null, false).getClass());
	        sslsocket.setUseClientMode(true);//client
	        sslsocket.setNeedClientAuth(true);
	        System.out.println("wtf0");
	        System.out.println(in.readLine());
	        System.out.println("wtf");
	        out.write("STARTTLS\r\n");
	        out.flush();
	        //System.out.print("hm");
	        
	        System.out.println(in.readLine());
	        System.out.println("w1");
	        SSLSession session = sslsocket.getSession();
	        System.out.println("w2");	
	        X509Certificate cert;
	        try {
	          cert = (X509Certificate) session.getPeerCertificates()[0];
	          System.out.println(cert.getPublicKey());
	        } catch (SSLPeerUnverifiedException e) {
	          System.err.println(session.getPeerHost() + " did not present a valid certificate.");
	          return false;
	        }
	        OutputStream sout = sslsocket.getOutputStream();
	        BufferedReader sin = new BufferedReader(new InputStreamReader(sslsocket.getInputStream(), Charset.forName("UTF-8")));
	        //System.out.println(sin.readLine());

	        System.out.println("sending anything here!!!!!!!!");
	        sout.write("article 156628\r\n".getBytes("UTF-8"));
	        
	        //System.out.println("the end");
	        //int t = sslsocket.getInputStream().available();
	        //byte[] bb = new byte[16];
	        //sslsocket.getInputStream().read(bb);
	        //System.out.println(" "+ new String(bb,"UTF-8"));
	        //System.out.println(sin.readLine());
	        
	        //InputStream inp= sslsocket.getInputStream();
	        
	        for(;;){
	        	String s = sin.readLine();
	        	System.out.println(s);
	        	//Thread.sleep(2000);
	        	//sout.flush();
	        	//System.out.println(inp.read());
	        	if(s == null || s == ".")
	        		break;
	        }
	        
	        sslsocket.shutdownInput();
	        sslsocket.shutdownOutput();
	        sslsocket.close();
	        /*
	        // Create buffers
	        ByteBuffer myAppData = ByteBuffer.allocate(session.getApplicationBufferSize());
	        ByteBuffer myNetData = ByteBuffer.allocate(session.getPacketBufferSize());
	        ByteBuffer peerAppData = ByteBuffer.allocate(session.getApplicationBufferSize());
	        ByteBuffer peerNetData = ByteBuffer.allocate(session.getPacketBufferSize());
	        
	        // Create the engine
	        SSLEngine engine = sslContext.createSSLEngine();
	        engine.setUseClientMode(true);
	        
	        System.out.println("ddd");
	        myAppData.put(ByteBuffer.wrap("hello\r\n".getBytes("UTF-8")));
	        myAppData.flip();
	        while (myAppData.hasRemaining()) {
	        	System.out.println("wtf");
	            // Generate SSL/TLS encoded data (handshake or application data)
	            SSLEngineResult res = engine.wrap(myAppData, myNetData);

	            // Process status of call
	            if (res.getStatus() == SSLEngineResult.Status.OK) {
	                myAppData.compact();

	                // Send SSL/TLS encoded data to peer
	                while(myNetData.hasRemaining()) {
	                    int num = socketChannel.write(myNetData);
	                    System.out.print("num"+num);
	                    if (num == 0) {
	                        // no bytes written; try again later
	                    }
	                }
	            }else if (res.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW || res.getStatus() == SSLEngineResult.Status.CLOSED)
	            	return true;

	            // Handle other status:  BUFFER_OVERFLOW, CLOSED
	            //...
	        }
	        //out.write(myNetData.array());
	        
	       */
	        
	        
	        
	        /*
	        int num = socketch.read(peerNetData);
	        if (num == -1) {
	            // The channel has reached end-of-stream
	        } else if (num == 0) {
	            // No bytes read; try again ...
	        } else {
	            // Process incoming data
	            peerNetData.flip();
	            SSLEngineResult res = engine.unwrap(peerNetData, peerAppData);

	            if (res.getStatus() == SSLEngineResult.Status.OK) {
	                peerNetData.compact();

	                if (peerAppData.hasRemaining()) {
	                    // Use peerAppData
	                }
	            }else if (res.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW || res.getStatus() == SSLEngineResult.Status.CLOSED)
	            	return true;

	            // Handle other status:  BUFFER_OVERFLOW, CLOSED
	            //...
	        }
	        */
	        
	        
	        
	        
	        
	        //System.out.println(in.readLine());
	        
	        //SSLEngine engine = sslContext.createSSLEngine();
	        //engine.setUseClientMode(false);
	        
	        /*SSLSession session = engine.getSession();
	        ByteBuffer myAppData = ByteBuffer.allocate(session.getApplicationBufferSize());
	        ByteBuffer myNetData = ByteBuffer.allocate(session.getPacketBufferSize());
	        ByteBuffer peerAppData = ByteBuffer.allocate(session.getApplicationBufferSize());
	        ByteBuffer peerNetData = ByteBuffer.allocate(session.getPacketBufferSize());

	        doHandshake(socketChannel, engine, myNetData, peerNetData);*/
	        
	        //Socket socket2 = sslContext.getSocketFactory().createSocket(socket, socket.getInetAddress().getHostName(), socket.getPort(), false);//.createSocket(socket, , true);
	        //sslContext.getSocketFactory().createSocket(socket, socket.getInetAddress().getHostName(), socket.getPort(), false);
	        //socket.
	        System.out.println("wtf");
	        /*SSLSocket sslsocket = (SSLSocket) sslContext.getSocketFactory().createSocket(socket, null, false);
	        
	        //sslsocket.startHandshake();
	        System.out.println("wtf2");
	        SSLSession session = sslsocket.getSession();
	        /*for (Provider p: Security.getProviders())
	        	System.out.println(p+"#"+p.getName()+"#"+p.getInfo());*/
	        /*
	        X509Certificate cert;
	        try {
	          cert = (X509Certificate) session.getPeerCertificates()[0];
	        } catch (SSLPeerUnverifiedException e) {
	          System.err.println(session.getPeerHost() + " did not present a valid certificate.");
	          //return false;
	        }
	        System.out.println(session.getCipherSuite().equals("SSL_NULL_WITH_NULL_NULL"));*/
	        //session.get
	        //System.out.println(session.getPeerCertificates()[0].getPublicKey());
	        /*
	        Certificate[] cchain = session.getPeerCertificates();
	        System.out.println("The Certificates used by peer");
	        for (int i = 0; i < cchain.length; i++) {
	          System.out.println(((X509Certificate) cchain[i]).getSubjectDN());
	        }*/
	        /*
	        System.out.println("Peer host is " + session.getPeerHost());
	        System.out.println("Cipher is " + session.getCipherSuite());
	        System.out.println("Protocol is " + session.getProtocol());
	        System.out.println("ID is " + new BigInteger(session.getId()));
	        System.out.println("Session created in " + session.getCreationTime());
	        System.out.println("Session accessed in " + session.getLastAccessedTime());
	        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
	        String x = in.readLine();
	        System.out.println(x);*/
	        //sch.configureBlocking(false);
	        //session
	        
	        /*System.out.println("h:"+socket.getInetAddress().getHostName()+":"+socket2.getPort());
	        System.out.println("h2:"+socket2.getInetAddress().getHostName()+":"+socket2.getPort());
	        System.out.println(
	        		new BufferedReader(new InputStreamReader(socket2.getInputStream(), Charset.forName("UTF-8"))).readLine()
	        		);*/
	        
	        return true;

			}
		//}
		return false;
		
	}

	public static void main(String[] args) throws InterruptedException, NoSuchAlgorithmException, IOException, KeyStoreException, CertificateException, UnrecoverableEntryException, MessagingException{
		TLS();
		connect();
	}
	    
}
