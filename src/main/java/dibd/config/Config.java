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
    public static final String ARTICLE_MAXSIZE = "dibd.article.maxsize";
    /**
     * BackendConfig key constant. Value: Amount of news that are feeded per
     * run.
     */
    public static final String EVENTLOG = "dibd.eventlog";

    public static final String PEERING = "dibd.feed";
    public static final String FEED_NEWSPERRUN = "dibd.feed.newsperrun";//notused
    public static final String FEED_PULLINTERVAL = "dibd.feed.pullinterval";//notused
    public static final String PULLDAYS = "dibd.peering.pulldays";
    //TODO:configurable for every peer
    public static final String PROXYHTTP = "dibd.feed.proxy.http";
    public static final String PROXYSOCKS = "dibd.feed.proxy.socks";
    public static final String TLSENABLED = "dibd.feed.tlsenabled"; //TLS or public globally. No variants for now.

    public static final String HOSTNAME = "dibd.hostname";
    public static final String PORT = "dibd.port";
    public static final String TIMEOUT = "dibd.timeout";
    public static final String LOGLEVEL = "dibd.loglevel";
    //It can be increased or decreased without lose. VALUES 1-7
    public static final String ID_F_COUNT = "dibd.id_f_count"; //count of F symbol in hex internal article Id

    /*
    public static final String MLPOLL_DELETEUNKNOWN = "dibd.mlpoll.deleteunknown";
    public static final String MLPOLL_HOST = "dibd.mlpoll.host";
    public static final String MLPOLL_PASSWORD = "dibd.mlpoll.password";
    public static final String MLPOLL_USER = "dibd.mlpoll.user";

    public static final String MLSEND_ADDRESS = "dibd.mlsend.address";
    public static final String MLSEND_RW_FROM = "dibd.mlsend.rewrite.from";
    public static final String MLSEND_RW_SENDER = "dibd.mlsend.rewrite.sender";
    public static final String MLSEND_HOST = "dibd.mlsend.host";
    public static final String MLSEND_PASSWORD = "dibd.mlsend.password";
    public static final String MLSEND_PORT = "dibd.mlsend.port";
    public static final String MLSEND_USER = "dibd.mlsend.user";
    public static final String MLSEND_AUTH = "dibd.mlsend.auth";
    */
    //public static final String ATTACHMENTSPATH = "dibd.path.attachments";//used in web and can't be accessed
    public static final String NNTPCACHEPATH = "dibd.path.nntpcache";
    public static final String NNTPALLOW_UNAUTORIZED = "dibd.nntp.allow_unautorized_post";
    

    /**
     * Key constant. If value is "true" every I/O is written to logfile (which
     * is a lot!)
     */
    public static final String DEBUG = "dibd.debug";

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
    
    /** Web used in JDBCDatabase */
    public static final String THREADS_PER_PAGE = "dibd.web.threads_per_page";
    public static final String PAGE_COUNT = "dibd.web.page_count";
    public static final String MAX_REPLAYS = "dibd.web.max_replays";

    /**
     * Key constant. Value is the name of the host which is allowed to use the
     * XDAEMON command; default: "localhost"
     */
    public static final String XDAEMON_HOST = "sonews.xdaemon.host";

    /** The config key for the filename of the logfile */
    public static final String LOGFILE = "dibd.log";
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

    @Override
    public String get(final String key, final String def) {
        String val = CommandLineConfig.getInstance().get(key, null);

        if (val == null) {
            val = FileConfig.getInstance().get(key, def);
        }

        if (val == null) {
            Log.get().log(Level.WARNING, "Returning default value for {0}", key);
        }
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
