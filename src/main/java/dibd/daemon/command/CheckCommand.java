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

import java.io.IOException;

import dibd.config.Config;
import dibd.daemon.NNTPConnection;
import dibd.daemon.NNTPInterface;
import dibd.storage.Headers;
import dibd.storage.StorageBackendException;
import dibd.storage.StorageManager;
import dibd.storage.article.Article;


/**
 * Class handling the CHECK command of CHECK TAKETHIS pair.
 * 
 * @author
 * 
 */
public class CheckCommand implements Command {
	
	@Override
    public String[] getSupportedCommandStrings() {
        return new String[] { "CHECK" };
    }

    @Override
    public boolean hasFinished() {
        return true;
    }

    @Override
    public String impliedCapability() {
        return "STREAMING";
    }

    @Override
    public boolean isStateful() {
        return false;
    }

    @Override
    public void processLine(NNTPInterface conn, final String line, byte[] raw)
            throws IOException, StorageBackendException {
    	System.out.println("CheckCommand "+line);
    	
    	if (Config.inst().get(Config.NNTPALLOW_UNAUTORIZED, false) || conn.isTLSenabled()){

    		final String[] command = line.split("\\p{Space}+");

    		if (command.length == 2) {
    			// Responses
    			//238 message-id   Send article to be transferred
    			//431 message-id   Transfer not possible; try again later
    			//438 message-id   Article not wanted
    			String messageId = command[1]; 
    			if(Headers.matchMsgId(messageId)){
    				//Message-Id
    				//TODO:may be better search in cache only?
    				Article art = StorageManager.current().getArticle(messageId, null);
    				if (art != null){
    					conn.println("438 "+messageId+" Article already exist");
    					return;
    				}else{
    					conn.println("238 "+messageId+" send article to be transferred");
    					return;
    				}
    			}	
    		}
    		conn.println("500 wrong command");
    	}else
    		conn.println("483 TLS required");
    }
}