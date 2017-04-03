/*
 *   dibd NNTP implementation for decentralized ImageBoard
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
package dibd;

import java.io.File;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.sql.Driver;
import java.sql.DriverManager;
import java.util.Date;
import java.util.Enumeration;
import java.util.logging.Level;

import dibd.config.Config;
import dibd.daemon.ChannelLineBuffers;
import dibd.daemon.CommandSelector;
import dibd.daemon.ConnectionCollector;
import dibd.daemon.NNTPDaemon;
import dibd.daemon.TLS;
import dibd.feed.FeedManager;
import dibd.storage.AttachmentProvider;
import dibd.storage.GroupsProvider;
import dibd.storage.NNTPCacheProvider;
import dibd.storage.StorageManager;
import dibd.storage.StorageProvider;
import dibd.storage.SubscriptionsProvider;
import dibd.util.Log;
import dibd.util.io.Resource;

/**
 * Startup class of the daemon.
 *
 * @author 
 * @since dibd/1.0.1
 */
public final class App {

    /** Version information of the dibd daemon */
    public static final String VERSION = "dibd/1.0.4";

    /** The server's startup date */
    public static final Date STARTDATE = new Date();

    /**
     * The main entrypoint.
     *
     * @param args
     * @throws InterruptedException
     */
    public static void main(String[] args) throws InterruptedException{
        System.out.println(VERSION);
        Thread.currentThread().setName("Mainthread");

        // Command line arguments
        int port = -1;

        for (int n = 0; n < args.length; n++) {
            switch (args[n]) {
                case "-c":
                case "-config": {
                    Config.inst().set(Config.LEVEL_CLI, Config.CONFIGFILE,
                            args[++n]);
                    System.out.println("Using config file " + args[n]);
                    break;
                }
                case "-dumpjdbcdriver": {
                    System.out.println("Available JDBC drivers:");
                    Enumeration<Driver> drvs = DriverManager.getDrivers();
                    while (drvs.hasMoreElements()) {
                        System.out.println(drvs.nextElement());
                    }
                    return;
                }
                /*case "-pulldays": {//how many days since last pull request?
                	Config.inst().set(Config.PULLDAYS,
                            args[++n]);
                	System.out.println("Using pull for " + args[n]+" days");
                    break;
                }*/
                case "-h":
                case "-help": {
                    printArguments();
                    return;
                }
                case "-p": {
                    port = Integer.parseInt(args[++n]);
                    break;
                }
                case "-plugin-storage": {
                    System.out
                            .println("Warning: -plugin-storage is not implemented!");
                    break;
                }
                case "-plugin-command": {
                    try {
                        CommandSelector.addCommandHandler(args[++n]);
                    } catch (ClassNotFoundException | InstantiationException | IllegalAccessException ex) {
                        StringBuilder strBuf = new StringBuilder();
                        strBuf.append("Could not load command plugin: ");
                        strBuf.append(args[n]);
                        Log.get().warning(strBuf.toString());
                        Log.get().log(Level.INFO, "App.java", ex);
                    }
                    break;
                }
                case "-v":
                case "-version":
                    // Simply return as the version info is already printed above
                    return;
            }
        }
        
        String hostname = Config.inst().get(Config.HOSTNAME, null);
        if (hostname == null || hostname.isEmpty())
        	throw new Error("No hostname");//Host name is important for message-id to determinate whether this Article is local or from peer

        // Load the storage backend
        String database = Config.inst().get(Config.STORAGE_DATABASE, null);
        if (database == null)
        	throw new Error("No storage backend configured (dibd.storage.database)");

        //StorageManager
        String provName = Config.inst().get(Config.LEVEL_FILE,
                    Config.STORAGE_PROVIDER,
                    "dibd.storage.impl.JDBCStorageProvider");
        StorageProvider sprov = StorageManager.loadProvider(provName);
        StorageManager.enableProvider(sprov);
        StorageManager.enableSubscriptionsProvider(new SubscriptionsProvider());//peers.conf
        StorageManager.enableGroupsProvider(new GroupsProvider());//groups.conf
        //ImageMagic initialization
        String IMpath = Config.inst().get(Config.IMAGEMAGICPATH, "/usr/bin/"); 
        File path = new File(IMpath);
        if(!path.exists()){
        	IMpath = "/usr/bin/";
        	if(!new File(IMpath).exists()){
        		IMpath="C:\\Programs\\ImageMagick;C:\\Programs\\exiftool";
        		if(! new File(IMpath.split(";")[0]).exists())
        			throw new Error("Fail to locate ImageMagic");
        	}
        }	
        try {
        	StorageManager.enableAttachmentProvider(
        			new AttachmentProvider("attachments/", IMpath));
        			//new AttachmentProvider(Config.inst().get(Config.ATTACHMENTSPATH, "attachments/"), IMpath));
        	StorageManager.enableNNTPCacheProvider(
        			new NNTPCacheProvider(Config.inst().get(Config.NNTPCACHEPATH, "nntpcache/")));
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
        
        //if TLS enabled
        try {
        	//System.out.println("Creating tls");
			TLS.createTLSContext(); //initialize
		} catch (UnrecoverableKeyException | KeyManagementException | KeyStoreException | NoSuchAlgorithmException
				| CertificateException | IOException e) {
			
			Log.get().log(Level.SEVERE, "Cant initialize TLS context: {0}", e);
            //System.exit(1);
		}

        ChannelLineBuffers.allocateDirect();        
        
        // Add shutdown hook
        Thread downhook = new Thread(new ShutdownHook());
        Runtime.getRuntime().addShutdownHook(downhook);
        
        // Start the listening daemon
        if (port <= 0) {
            port = Config.inst().get(Config.PORT, 119);
        }else
        	Config.inst().set(Config.PORT, String.valueOf(port));
        final NNTPDaemon daemon = NNTPDaemon.createInstance(port);
        
        if (Config.inst().get(Config.PEERING, false)){
        	//Pull new articles. Just before the final start.
        	(new FeedManager()).start();

        	FeedManager.startPushDaemons();
        }
        
        daemon.start();
        // Start Connections purger thread...
        ConnectionCollector.getInstance().start();
        
        // Wait for main thread to exit (setDaemon(false))
        daemon.join();
        //downhook.join();
    }

    private static void printArguments() {
        String usage = Resource.getAsString("usage", true);
        System.out.println(usage);
    }

    private App() {
    }
}
