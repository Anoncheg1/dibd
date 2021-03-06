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
import java.util.Set;

import dibd.daemon.CommandSelector;
import dibd.daemon.NNTPInterface;
import dibd.util.Log;
import dibd.util.io.Resource;

/**
 * This command provides a short summary of the commands that are understood by
 * this implementation of the server. The help text will be presented as a
 * multi-line data block following the 100 response code (taken from RFC).
 *
 * @author Christian Lins
 * @since sonews/0.5.0
 */
public class HelpCommand implements Command {

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
        return true;
    }

    @Override
    public String[] getSupportedCommandStrings() {
        return new String[] { "HELP" };
    }

    @Override
    public void processLine(NNTPInterface conn, final String line, byte[] raw)
            throws IOException {
        final String[] command = line.split("\\p{Space}+");
        conn.println("100 help text follows");

        if (command.length <= 1) {
            final String helpRes = Resource.getAsString("helptext", true);
            if (helpRes == null) {
                Log.get().warning("helpers/helptext could not be loaded");
                conn.println("500 Internal Server Error");
                return;
            }

            final String[] help = helpRes.split("\n");
            for (String hstr : help) {
                conn.println(hstr);
            }

            Set<String> commandNames = CommandSelector.getCommandNames();
            for (String cmdName : commandNames) {
                conn.println(cmdName);
            }
        } else {
            Command cmd = CommandSelector.getInstance().get(command[1]);
            if (cmd instanceof HelpfulCommand) {
                conn.println(((HelpfulCommand) cmd).getHelpString());
            } else {
                conn.println("No further help information available.");
            }
        }

        conn.println(".");
    }
}
