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
package dibd.config;

import java.util.logging.Level;

import dibd.util.Log;

/**
 * Configuration facade class.
 *
 * @author Christian Lins
 * @since sonews/1.0
 */
public class Config extends AbstractConfig {

    public static final int LEVEL_CLI = 1;
    public static final int LEVEL_FILE = 2;

    public static final String CONFIGFILE = "dibd.configfile";
    /**
     * BackendConfig key constant. Value is the maximum article size in
     * megabytes.
     */
    public static final String MAX_ARTICLE_SIZE = "dibd.article.maxsize"; //MB. attachment size for web, article size for article
    public static final String MAX_MESSAGE_SIZE = "dibd.article.maxmessagesize"; //limit = dibd.article.maxsize*dibd.maxsize_multiplier
    /**
     * log
     */
    public static final String LOGDIR = "dibd.log.dir";
    public static final String LOGLEVEL = "dibd.log.level";
    /**
     * Feed
     */
    public static final String PEERING = "dibd.feed";
    public static final String PULLINTERVAL = "dibd.feed.pullinterval";//pullat start will run again after ? hours
    //TODO:configurable for every peer
    public static final String PROXYHTTP = "dibd.feed.proxy.http";
    public static final String PROXYSOCKS = "dibd.feed.proxy.socks";
    public static final String TLSENABLED = "dibd.feed.tlsenabled"; //TLS or public globally. No variants for now.
    /**
     * Core socket
     */
    public static final String HOSTNAME = "dibd.hostname";
    public static final String PORT = "dibd.port";
    public static final String TIMEOUT = "dibd.timeout";
    
    /**
     * Core database
     */
    /** Key constant. Value is classname of the JDBC driver */
    public static final String STORAGE_DBMSDRIVER = "dibd.storage.dbmsdriver";
    /** Key constant. Value is JDBC connect String to the database. */
    public static final String STORAGE_DATABASE = "dibd.storage.database";
    public static final String STORAGE_HOST = "dibd.storage.host";
    /** Key constant. Value is the username for the DBMS. */
    public static final String STORAGE_USER = "dibd.storage.user";
    /** Key constant. Value is the password for the DBMS. */
    public static final String STORAGE_PASSWORD = "dibd.storage.password";
    public static final String STORAGE_PORT     = "dibd.storage.port";
    public static final String STORAGE_PROVIDER = "dibd.storage.provider";
    public static final String ID_F_COUNT = "dibd.id_f_count"; //count of F symbol in hex internal article Id

    
    public static final String NNTPALLOW_UNAUTORIZED = "dibd.nntp.allow_unautorized_post";
    public static final String IMAGEMAGICPATH = "dibd.path.imagemagic";
    public static final String ATTACHDIR = "dibd.attach.dir";
    public static final String NNTPCACHEDIR = "dibd.nntpcache.dir";
    
    
    
    /** Web used in JDBCDatabase */
    public static final String THREADS_PER_PAGE = "dibd.web.threads_per_page";
    public static final String PAGE_COUNT = "dibd.web.page_count";
    public static final String MAX_REPLAYS = "dibd.web.max_replays";
    public static final String REPLAYSONBOARD = "dibd.web.replays_on_board";

    /**
     * Key constant. Value is the name of the host which is allowed to use the
     * XDAEMON command; default: "localhost"
     */
    public static final String XDAEMON_HOST = "sonews.xdaemon.host";

    /*public static final String[] AVAILABLE_KEYS = { ARTICLE_MAXSIZE, EVENTLOG,
    		PEERING, FEED_NEWSPERRUN, FEED_PULLINTERVAL, HOSTNAME, MLPOLL_DELETEUNKNOWN,
            MLPOLL_HOST, MLPOLL_PASSWORD, MLPOLL_USER, MLSEND_ADDRESS,
            MLSEND_HOST, MLSEND_PASSWORD, MLSEND_PORT, MLSEND_RW_FROM,
            MLSEND_RW_SENDER, MLSEND_USER, PORT, TIMEOUT, XDAEMON_HOST };*/
    private static final Config instance = new Config();

    public static Config inst() {
        return instance;
    }

    private Config() {
    }

    /**
     * For log initialization only in Log.class
     * 
     * @param key
     * @param def
     * @return
     */
    public String getSilent(final String key, final String def) {
    	String val = CommandLineConfig.getInstance().get(key, null);

        if (val == null)
            val = FileConfig.getInstance().get(key, def);
        
        return val;
    }
    
    @Override
    public String get(final String key, final String def) {
        String val = CommandLineConfig.getInstance().get(key, null);

        if (val == null)
            val = FileConfig.getInstance().get(key, def);//TODO:check with null def and no test for optimization
        
        if (val == null) 
        	Log.get().log(Level.WARNING, "Returning default value for {0}, {1}", new Object[]{key, val});
        	
        return val;
    }

    public String get(final int maxLevel, final String key, final String def) {
        String val = CommandLineConfig.getInstance().get(key, null);

        if (val == null && maxLevel >= LEVEL_FILE) {
            val = FileConfig.getInstance().get(key, null);
        }

        return val != null ? val : def;
    }

    @Override
    public void set(final String key, final String val) {
        set(LEVEL_FILE, key, val);
    }

    public void set(final int level, final String key, final String val) {
        switch (level) {
            case LEVEL_CLI: {
                CommandLineConfig.getInstance().set(key, val);
                break;
            }
            case LEVEL_FILE: {
                FileConfig.getInstance().set(key, val);
                break;
            }
        }
    }
}
