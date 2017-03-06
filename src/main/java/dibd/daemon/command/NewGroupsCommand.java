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

import dibd.daemon.NNTPInterface;
import dibd.storage.GroupsProvider.Group;
import dibd.storage.StorageBackendException;
import dibd.storage.StorageManager;

/**
 * Class handling the NEWGROUPS command.
 * 
 * NEWGROUPS date time [GMT]
 *    Responses
 *    231    List of new newsgroups follows (multi-line)
 * 
 * @author Christian Lins
 * @author Dennis Schwerdel
 * @since n3tpd/0.1
 */
public class NewGroupsCommand implements Command {

    @Override
    public String[] getSupportedCommandStrings() {
        return new String[] { "NEWGROUPS", "NEWSGROUPS" }; // NEWSGROUPS is a nntpchan bug
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

    @Override
    public void processLine(NNTPInterface conn, final String line, byte[] raw)
            throws IOException, StorageBackendException {
        final String[] command = line.split(" ");

        if (command.length >= 3) {
        	if(command[0].equalsIgnoreCase("newsgroups"))
        		conn.println("231 newgroups not newSgroups damn you!");
        	else
        		conn.println("231 list of new newsgroups follows");
            for( Group g : StorageManager.groups.getAll()){
            	String writeable = g.isWriteable() ? " y" : " n";
            	conn.println(g.getName()+" "
            			+ g.getLastArticleNumber() + " "
                        + g.getFirstArticleNumber() + writeable);
            }

            // Currently we do not store a group's creation date;
            // so we return an empty list which is a valid response
            conn.println(".");
        } else {
            conn.println("500 invalid command usage");
        }
    }
}
