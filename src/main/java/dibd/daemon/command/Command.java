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
import dibd.storage.StorageBackendException;
//TODO:Ключевое слово команды и аргументы отделяются одним или более пробелов или TAB 
/**
 * Interface for pluggable NNTP commands handling classes.
 * 
 * @author Christian Lins
 * @since sonews/0.6.0
 */
public interface Command {
	
	//String getHelpString();

    /**
     * @return true if this instance can be reused.(Always true == pipelined)
     */
    boolean hasFinished();

    /**
     * Returns capability string that is implied by this command class. MAY
     * return null if the command is required by the NNTP standard.
     */
    String impliedCapability();

    
    /**
     * Is here any state state variable in command
     */
    boolean isStateful();
    
    // поддерживаемые комманды в одном классе
    String[] getSupportedCommandStrings();

    //void processLine(NNTPConnection conn, String line, byte[] rawLine)
      //      throws IOException, StorageBackendException;

	void processLine(NNTPInterface conn, String line, byte[] raw) throws IOException, StorageBackendException;
}
