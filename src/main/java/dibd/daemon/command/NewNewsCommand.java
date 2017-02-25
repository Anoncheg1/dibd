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
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import dibd.daemon.NNTPConnection;
import dibd.daemon.NNTPInterface;
import dibd.storage.StorageBackendException;
import dibd.storage.StorageManager;
import dibd.storage.StorageNNTP;
import dibd.storage.GroupsProvider.Group;
import dibd.storage.article.Article;

/**
 * Class handling the NEWNEWS command. Used for "pulling".
 * Indicating capability: NEWNEWS
 * 
 * Syntax
 *    NEWNEWS wildmat date time [GMT]
 *         
 * Responses
 *    230    List of new articles follows (multi-line)
 *                
 * Parameters
 *    wildmat    Newsgroups of interest
 *    date       Date in yymmdd or yyyymmdd format
 * 	  time       Time in hhmmss format
 *
 * The date is specified as 6 or 8 digits in the format [xx]yymmdd,
 * where xx is the first two digits of the year (19-99), yy is the last
 * two digits of the year (00-99), mm is the month (01-12), and dd is
 * the day of the month (01-31).  Clients SHOULD specify all four digits
 * of the year.  If the first two digits of the year are not specified
 * (this is supported only for backward compatibility), the year is to
 * be taken from the current century if yy is smaller than or equal to
 * the current year, and the previous century otherwise.
 * 
 * The time is specified as 6 digits in the format hhmmss, where hh is
 * the hours in the 24-hour clock (00-23), mm is the minutes (00-59),
 * and ss is the seconds (00-60, to allow for leap seconds).  The token
 * "GMT" specifies that the date and time are given in Coordinated
 * Universal Time [TF.686-1]; if it is omitted, then the date and time
 * are specified in the server's local timezone.  Note that there is no
 * way of using the protocol specified in this document to establish the
 * server's local timezone.
 * 
 * [C] NEWNEWS news.*,sci.* 19990624 000000 GMT
 * [S] 230 list of new articles by message-id follows
 * [S] <i.am.a.new.article@example.com>
 * [S] <i.am.another.new.article@example.com>
 * [S] .
 *
 * Old format:
 * [C] NEWNEWS news,sci (mmdd | yymmdd | yyyymmdd) hhmmss
 * New format:
 * [C] NEWNEWS news,sci 1464210306
 * NEW FEATURE:
 * !!! MUST RETURN FULL THREADS.!!!
 * THREAD message-id MUST BE BEFORE!! HIS REPLAYS message-ids
 * @author me
 * @since dibd/1.0.1
 */
public class NewNewsCommand implements Command {

	@Override
	public String[] getSupportedCommandStrings() {
		return new String[] { "NEWNEWS" };
	}

	@Override
	public boolean hasFinished() {
		return true;
	}

	@Override
	public String impliedCapability() {
		return "NEWNEWS";
	}

	@Override
	public boolean isStateful() {
		return false;
	}

	@Override
	public void processLine(NNTPInterface conn, final String line, byte[] raw)
			throws IOException, StorageBackendException {
		final String[] command = line.split("\\p{Space}+");
		//NEWNEWS wildmat date time [GMT]
		if (command.length == 3 || command.length >= 4) {
			long date;
			try {
				if (command.length == 3)
					date = parseDate(command[2], null); //epoch
				else
					date = parseDate(command[2], command[3]);
			} catch (ParseException e) {
				conn.println("500 invalid command usage: "+e.getMessage());
				return;
			}

			//news,sci without .*
			conn.println("230 List of new articles follows (multi-line)");
			for (String gn : command[1].split(",")){
				Group g = StorageManager.groups.get(gn);
				if (g != null)
					if(!g.isDeleted()){
						List<String> ids = StorageManager.current().getNewArticleIDs(g, date);
						
						for(String s : ids)
							conn.println(s);
					}
			}
			conn.println(".");
			return;
		}
		conn.println("500 invalid command usage");
	}


	/**
	 * Parse date and return Unix Epoch time in seconds
	 * old format = date:(mmdd | yymmdd | yyyymmdd) time:hhmmss  
	 * new format = date:Epoch time in seconds
	 * @param date
	 * @param time
	 * @return Epoch in seconds
	 * @throws ParseException
	 */
	private long parseDate(String date, String time) throws ParseException {
		//Date in mmdd or yymmdd or yyyymmdd format
		//Time in hhmmss format

		int dl = date.length();
		int year = Calendar.getInstance().get(Calendar.YEAR);

		//if (((dl >= 10 && dl <= 20) || date.contentEquals("0")) && time == null){ //1) new format
		if ( time == null && (dl >= 0 && dl <= 20)){ //1) new format
			return Long.parseLong(date); //epoch in seconds
		}else if (time != null && dl != 0 && time.length() != 0){ //2)old format
			switch (dl) {
				case 4: date = year+date; //mmdd
					break;
				case 6:{ //yymmdd
					int y = Integer.decode(date.substring(0, 2));
					if (y <= (year-2000))
						y+=2000;
					date = y+date.substring(2,6);
				};  break;
				case 8: break; //yyyymmdd
				default: throw new ParseException("wrong date length in old format",0);
			}
			
			/*
			if (dl == 4){
				date = year+date;
			}else if (dl == 6){
				int y = Integer.decode(date.substring(0, 2));
				if (y <= (year-2000))
					y+=2000;
				date = y+date.substring(2,6);
			}else
				

			if (time.length() != 6)
				throw new ParseException("wrong time length",0);
*/

			DateFormat Dformat = new SimpleDateFormat("yyyyMMdd");
			Dformat.setTimeZone(TimeZone.getTimeZone("UTC"));
			DateFormat Tformat = new SimpleDateFormat("kkmmss");
			Tformat.setTimeZone(TimeZone.getTimeZone("UTC"));

			Date dateAfter = Dformat.parse(date);
			Date timeAfter = Tformat.parse(time);
			return (timeAfter.getTime()+dateAfter.getTime())/1000;
		}else
			throw new ParseException("no time for old format",0);
	}
}
