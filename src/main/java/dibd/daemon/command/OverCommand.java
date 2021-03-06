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

import dibd.config.Config;
import dibd.daemon.NNTPInterface;
import dibd.storage.GroupsProvider.Group;
import dibd.storage.Headers;
import dibd.storage.ScrapLine;
import dibd.storage.StorageBackendException;
import dibd.storage.StorageManager;

/**
 * Class handling the OVER/XOVER command.
 * 
 * Description of the XOVER command:
 * 
 * <pre>
 * XOVER [range]
 *
 * The XOVER command returns information from the overview
 * database for the article(s) specified.
 *
 * The optional range argument may be any of the following:
 *              an article number
 *              an article number followed by a dash to indicate
 *                 all following
 *              an article number followed by a dash followed by
 *                 another article number
 *
 * If no argument is specified, then information from the
 * current article is displayed. Successful responses start
 * with a 224 response followed by the overview information
 * for all matched messages. Once the output is complete, a
 * period is sent on a line by itself. If no argument is
 * specified, the information for the current article is
 * returned.  A news group must have been selected earlier,
 * else a 412 error response is returned. If no articles are
 * in the range specified, a 420 error response is returned
 * by the server. A 502 response will be returned if the
 * client only has permission to transfer articles.
 *
 * Each line of output will be formatted with the article number,
 * followed by each of the headers in the overview database or the
 * article itself (when the data is not available in the overview
 * database) for that article separated by a tab character.  The
 * sequence of fields must be in this order: subject, author,
 * date, message-id, references, byte count, and line count. Other
 * optional fields may follow line count. Other optional fields may
 * follow line count. These fields are specified by examining the
 * response to the LIST OVERVIEW.FMT command. Where no data exists,
 * a null field must be provided (i.e. the output will have two tab
 * characters adjacent to each other). Servers should not output
 * fields for articles that have been removed since the XOVER database
 * was created.
 *
 * The LIST OVERVIEW.FMT command should be implemented if XOVER
 * is implemented. A client can use LIST OVERVIEW.FMT to determine
 * what optional fields  and in which order all fields will be
 * supplied by the XOVER command. 
 *
 * Note that any tab and end-of-line characters in any header
 * data that is returned will be converted to a space character.
 *
 * Responses:
 *
 *   224 Overview information follows
 *   412 No news group current selected
 *   420 No article(s) selected
 *   502 no permission
 *
 * OVER defines additional responses:
 *
 *  First form (message-id specified)
 *    224    Overview information follows (multi-line)
 *    430    No article with that message-id
 *
 *  Second form (range specified)
 *    224    Overview information follows (multi-line)
 *    412    No newsgroup selected
 *    423    No articles in that range
 *
 *  Third form (current article number used)
 *    224    Overview information follows (multi-line)
 *    412    No newsgroup selected
 *    420    Current article number is invalid
 *
 * </pre>
 * 
 * Consume too many resources.
 * 
 * @author Christian Lins
 * @since sonews/0.5.0
 */
public class OverCommand implements Command {

    //public static final int MAX_LINES_PER_DBREQUEST = 200;

    @Override
    public String[] getSupportedCommandStrings() {
        return new String[] { "OVER", "XOVER" };
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
    	Group group = conn.getCurrentGroup();
        if (group == null) {
            conn.println("412 no newsgroup selected");
        } else if ((Config.inst().get(Config.ALLOW_UNAUT_SCRAP, true) && Math.random() > 0.3) || conn.isTLSenabled()){ //slow down xover for nntpchan 
        	conn.println("224 Overview information follows (multi-line)");
        	//int allThreads = Config.inst().get(Config.THREADS_PER_PAGE, 5) * Config.inst().get(Config.PAGE_COUNT, 6);
        	List<ScrapLine> slist = StorageManager.current().scrapGroup(group, Integer.MAX_VALUE);//no limit
        	if ( ! slist.isEmpty()){
        		int i = 1;
        		int thread_id=-1;
        		String thread_mid=null;
        		//int thread_id=slist.get(0).thread_id;
        		//String thread_mid=slist.get(0).message_id;
        		for (ScrapLine sl: slist){
        			if (thread_id != sl.thread_id){
        				//new thread
        				thread_id = sl.thread_id;
        				thread_mid = sl.message_id;
        				conn.println(buildOverview(sl, i++, null));
        			}else
        				//replay
        				conn.println(buildOverview(sl, i++, thread_mid));
        			
        		}
        		slist.clear();//first overcommand class will stay alive in command selector.
        	}
        	conn.println(".");
        }else
        	conn.println("502 no permission");
        	
        
    }

    private String buildOverview(ScrapLine sl, long i, String thread_mid){
        StringBuilder overview = new StringBuilder();
        //1) number
        overview.append(i)
        .append('\t');
        //2) Subject
        /*String subject = art.getSubject();
        if (subject != null)
        	overview.append(MimeUtility.encodeWord(escapeString(subject)));*/
        overview.append('\t');
        //3) from
        /*String name = art.getA_name();
        if (name != null)
        	overview.append(escapeString(MimeUtility.encodeWord(name)));*/
        overview.append('\t');
        //4)date
        overview.append(Headers.formatDate(sl.article_post_time)).append('\t');
        //5)message-Id
        overview.append(sl.message_id.trim()).append('\t');
        //6) thread-Id
        if( thread_mid != null) //if replay
        	overview.append(thread_mid);
        //overview.append('\t');

        //String bytes = art.getHeader(Headers.BYTES)[0];
        /*String bytes = "";
        if ("".equals(bytes)) {
            bytes = "0";
        }
        overview.append(escapeString(bytes));
        overview.append('\t');

        //String lines = art.getHeader(Headers.LINES)[0];
        String lines = "";
        if ("".equals(lines)) {
            lines = "0";
        }
        overview.append(escapeString(lines));
        overview.append('\t');
        //overview.append(escapeString(art.getHeader(Headers.XREF)[0]));
*/
        // Remove trailing tabs if some data is empty
        return overview.toString().trim();
    }

    /*private String escapeString(String str) {
        String nstr = str.replace("\r", "");
        nstr = nstr.replace('\n', ' ');
        nstr = nstr.replace('\t', ' ');
        return nstr.trim();
    }*/
}
