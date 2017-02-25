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
package dibd.daemon.command;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;

import dibd.config.Config;
import dibd.daemon.NNTPConnection;
import dibd.daemon.NNTPInterface;
import dibd.storage.Headers;
import dibd.storage.StorageBackendException;
import dibd.storage.StorageManager;
import dibd.storage.GroupsProvider.Group;
import dibd.storage.article.Article;
import dibd.util.Log;

/**
 * Class handling the ARTICLE, BODY and HEAD commands.
 * 
 * For ARTICLE it prints cached NNTP Article from peers.
 * 
 * First we interpret number as internal if it's not found we trying to fake old format.
 * There may be errors for old numbers and "current" staff here.
 * 
 * Common usage: 
 * Article message-id
 * Article internal-global-id
 * 
 * First form (message-id specified)
     220 0|n message-id    Article follows (multi-line)
     430                   No article with that message-id

   Second form (article number specified)
     220 n message-id      Article follows (multi-line)
     412                   No newsgroup selected
     423                   No article with that number

   Third form (current article number used)
     220 n message-id      Article follows (multi-line)
     412                   No newsgroup selected
     420                   Current article number is invalid
     
      HEAD
      220 changed 221
      BODY
      220 changed 222
 * 
 * @author Christian Lins
 * @author Dennis Schwerdel
 * @since n3tpd/0.1
 */
public class ArticleCommand implements Command {

    @Override
    public String[] getSupportedCommandStrings() {
        return new String[] { "ARTICLE", "BODY", "HEAD" };
    }

    @Override
    public boolean hasFinished() {
        return true;
    }

    @Override
    public String impliedCapability() {
        return null;
    }

    @Override
    public boolean isStateful() {
        return false;
    }

    // TODO: Refactor this method to reduce its complexity!
    @Override
    public void processLine(NNTPInterface conn, final String line, byte[] raw)
            throws IOException, StorageBackendException {
        final String[] command = line.split("\\p{Space}+");

        Article article = null;
        if (command.length == 1) {
            article = conn.getCurrentArticle();
            if (article == null) {
                conn.println("420 no current article has been selected");
                return;
            }
        // Message-ID
        } else if (Headers.matchMsgId(command[1])) {
			article = StorageManager.current().getArticle(command[1], null);
            if (article == null) {
                conn.println("430 no such article found");
                return;
            }
        // Message Number
        } else {
            
            try {
                int id = Integer.parseInt(command[1]);
                //1) number as internal number
                article = StorageManager.current().getArticle(null, id);
                //2) number as old format for current group
                if (article == null){
                	Group currentGroup = conn.getCurrentGroup();
                	if (currentGroup == null) {
                        conn.println("412 no group selected");
                        return;
                    }else{
                    	List<Integer> li= StorageManager.current().getArticleNumbers(currentGroup.getInternalID(), id);
                    	try{
                    		id = li.get(0);
                    	}catch(IndexOutOfBoundsException ex){
                    		conn.println("420 no article found in current group");
                    		return;
                    	}
                    	article = StorageManager.current().getArticle(null, id);
                    	if (article == null){
                    		conn.println("500 no article found in current group");
                    		return;
                    	}
                    }	
                }
            } catch (NumberFormatException ex) {
            	conn.println("501 number format error");
            }

            if (article == null) {
                conn.println("423 no such article number in this group");
                return;
            }
            conn.setCurrentArticle(article);
        }
        
        
        
        //MAIN PART
        if (command[0].equalsIgnoreCase("ARTICLE")) {
            String ok = "220 " + article.getId() + " " + article.getMessageId()
            + " article retrieved - head and body follow";
            
            //TODO:use local_and_fresh boolean
            /*if (! article.getMsgID_host()
            		.equals(Config.inst().get(Config.HOSTNAME, null))){
            	//NNTP Cache
            	FileInputStream fs = StorageManager.nntpcache.getFileStream(article);
            	if (fs != null)
            		conn.print(fs, article.getMessageId());
            	else{
            		conn.println("500 Internal server problem.");
            		return;
            	}*/
            //1) check cache first even if article was ours.
            FileInputStream fs = StorageManager.nntpcache.getFileStream(article);
        	if (fs != null){
        		conn.println(ok);
        		conn.print(fs, article.getMessageId());
        	//2)check if message is ours
        	}else{
        		if (article.getMsgID_host()
        				.equals(Config.inst().get(Config.HOSTNAME, null))){
        			conn.println(ok);
        			//we build our article if it was not received by partial threads.
        			conn.println(article.buildNNTPMessage(conn.getCurrentCharset(), 0));
        		}else{
        			Log.get().log(Level.SEVERE, "{0} article was not found in cache and do not have our hostname. NNPTCache is corrupted",
        					article.getMessageId());
        			conn.println("500 Internal server problem.");
        		}
        	}
            
        } else if (command[0].equalsIgnoreCase("HEAD")) {
        	conn.println("221 " + article.getId() + " " + article.getMessageId()
        	+ " Headers follow (multi-line)");

        	conn.println(article.buildNNTPMessage(conn.getCurrentCharset(), 1));
        }else if (command[0].equalsIgnoreCase("BODY")) {
        	conn.println("222 " + article.getId() + " " + article.getMessageId()
        	+ " Body follows (multi-line)");
        	
        	conn.println(article.buildNNTPMessage(conn.getCurrentCharset(), 2));
        }
        conn.println(".");
    }
}
