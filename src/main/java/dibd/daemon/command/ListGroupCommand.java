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
import java.util.List;

import dibd.daemon.NNTPInterface;
import dibd.storage.StorageBackendException;
import dibd.storage.StorageManager;
import dibd.storage.GroupsProvider.Group;

/**
 * Class handling the LISTGROUP command.
 *
 */
public class ListGroupCommand implements Command {

    @Override
    public String[] getSupportedCommandStrings() {
        return new String[] { "LISTGROUP" };
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
    public void processLine(NNTPInterface conn, final String commandName,
            byte[] raw) throws IOException, StorageBackendException {
        final String[] command = commandName.split("\\p{Space}+");

        Group group = null;
        int start = 0;
        if (command.length >= 2){
        	group = StorageManager.groups.get(command[1]);
        	if (group == null){
        		group = conn.getCurrentGroup();
        		start = Integer.parseInt(command[1]);
        	}
        }else
        	group = conn.getCurrentGroup();
        
        if (group == null) {
            conn.println("412 no group selected; use GROUP <group> command");
            return;
        }

        List<Integer> ids = group.getArticleNumbers((command.length >= 3 ? Integer.parseInt(command[2]) : start)); // command[2] - start
        if(ids != null){
        	conn.println("211 " + ids.size() + " " + group.getFirstArticleNumber()
        	+ " " + group.getLastArticleNumber()
        	+ " list of article numbers follow");
        	for (int id : ids) {
        		if(id != 0)
        			conn.println(Integer.toString(id));
        	}
        }else
        	conn.println("211 " + 0 + " " + group.getFirstArticleNumber()
        	+ " " + group.getLastArticleNumber()
        	+ " list of article numbers follow");
        conn.println(".");
    }
}
