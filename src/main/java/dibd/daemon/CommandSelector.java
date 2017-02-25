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

package dibd.daemon;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import dibd.daemon.command.Command;
import dibd.daemon.command.UnsupportedCommand;
import dibd.util.Log;
import dibd.util.io.Resource;

/**
 * Selects the correct command processing class.
 *  one global Map<String, Class<?>> - slow
 *  and one for every Connection worker thread:
 *    Map<String, Command> - fast
 *
 * @author Christian Lins
 * @since sonews/1.0
 */
public class CommandSelector {

    private static Map<Thread, CommandSelector> instances = new ConcurrentHashMap<>();
    private static Map<String, Class<?>> commandClassesMapping = new ConcurrentHashMap<>();

    static {
        String classesRes = Resource.getAsString("commands.conf", true);
        if (classesRes == null) {
            Log.get().log(Level.SEVERE, "Could not load command classes list");
        } else {
            String[] classes = classesRes.split("\n");
            for (String className : classes) {
                if (className.charAt(0) == '#') {
                    // Skip comments
                    continue;
                }

                try {
                    addCommandHandler(className);
                } catch (ClassNotFoundException ex) {
                    Log.get().log(Level.WARNING, "Could not load command class: {0}", className);
                } catch (InstantiationException ex) {
                    Log.get().log(Level.SEVERE, "Could not instantiate command class: {0}", className);
                } catch (IllegalAccessException ex) {
                    Log.get().log(Level.SEVERE, "Could not access command class: {0}", className);
                }
            }
        }
    }

    /**
     * for plugin commands
     * 
     * @param className
     * @throws ClassNotFoundException
     * @throws InstantiationException
     * @throws IllegalAccessException
     */
    public static void addCommandHandler(String className)
            throws ClassNotFoundException, InstantiationException, IllegalAccessException {
        Class<?> clazz = Class.forName(className);
        Command cmd = (Command) clazz.newInstance();
        String[] cmdStrs = cmd.getSupportedCommandStrings();
        if (cmdStrs == null) {
            Log.get().log(Level.WARNING, "CommandHandler class does not support any command: {0}", className);
            return;
        }

        for (String cmdStr : cmdStrs) {
            commandClassesMapping.put(cmdStr, clazz);
        }
    }

    public static Set<String> getCommandNames() {
        return commandClassesMapping.keySet();
    }

    public static CommandSelector getInstance() {
        CommandSelector csel = instances.get(Thread.currentThread());
        if (csel == null) {
            csel = new CommandSelector();
            instances.put(Thread.currentThread(), csel);
        }
        return csel;
    }

    
    
    
    private final Map<String, Command> commandMapping = new HashMap<>(); //вызванные команды потока
    private final Command unsupportedCmd = new UnsupportedCommand();	

    private CommandSelector() {}

    //TODO: Higher priority to stateless methods now. Change it to stateful:IHAVE,POST. 
    public Command get(String commandName) {
        try {
            commandName = commandName.toUpperCase();
            Command cmd = this.commandMapping.get(commandName); //была ли вызвана уже

            if (cmd == null) { //если нет добавляем
                Class<?> clazz = commandClassesMapping.get(commandName);
                if (clazz == null) {
                    Log.get().log(Level.INFO, "No class found for command: {0}", commandName);
                    cmd = this.unsupportedCmd;
                } else {
                    cmd = (Command) clazz.newInstance();
                    this.commandMapping.put(commandName, cmd);
                }  
            } else if (cmd.isStateful()) {//there is not many: IHAVE,POST,HELP
                cmd = cmd.getClass().newInstance();
            }

            return cmd;
        } catch (InstantiationException | IllegalAccessException ex) {
            Log.get().log(Level.SEVERE,"Could not load command handling class", ex);
            return this.unsupportedCmd;
        }
    }
}