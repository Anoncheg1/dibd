/*
 *   StarOffice News Server
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

package dibd.test.command;

import dibd.test.AbstractTest;

/**
 * Blackbox Test testing the LIST command
 * @author Christian Lins
 * @since sonews/0.5.0
 */
public class ListTest extends AbstractTest
{
  
  @Override
  public int runTest()
    throws Exception
  {
    println("LIST OVERVIEW.FMT");
    String line = readln();
    if(line.startsWith("215 "))
    {
      while(!line.equals("."))
      {
        line = readln();
      }
      return 0;
    }
    else
      return 1;
  }
  
}
