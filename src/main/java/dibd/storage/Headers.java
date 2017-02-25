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

package dibd.storage;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import dibd.daemon.NNTPConnection;
/**
 * Contains header constants. These header keys are no way complete but all
 * headers that are relevant for sonews.
 * 
 * @author Christian Lins
 * @since sonews/0.5.0
 */
public final class Headers {

    public static final String BYTES = "bytes";
    public static final String CONTENT_TYPE = "Content-Type";
    public static final String CONTENT_DISP = "Content-Disposition";
    public static final String CONTROL = "control";
    public static final String DATE = "Date";
    public static final String FROM = "From";
    public static final String LINES = "lines";
    public static final String LIST_POST = "list-post";
    public static final String MESSAGE_ID = "Message-ID";
    public static final String NEWSGROUPS = "Newsgroups";
    public static final String NNTP_POSTING_DATE = "nntp-posting-date";
    public static final String NNTP_POSTING_HOST = "nntp-posting-host";
    public static final String PATH = "Path";
    public static final String REFERENCES = "References";
    public static final String REPLY_TO = "Reply-To";
    public static final String SENDER = "Sender";
    public static final String SUBJECT = "Subject";
    public static final String SUPERSEDES = "subersedes";
    public static final String TO = "To";
    public static final String X_COMPLAINTS_TO = "x-complaints-to";
    public static final String X_LIST_POST = "x-list-post";
    public static final String X_TRACE = "x-trace";
    public static final String XREF = "xref";
    public static final String ENCODING = "Content-Transfer-Encoding"; //attachment header
    public static final String MIME_VERSION = "MIME-Version: 1.0";
    
	static private SimpleDateFormat dateFormat;
	static{ 
		dateFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z" );
		dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
	}

	/**
	 * from string to long in seconds
	 * 
	 * @param date
	 * @return
	 * @throws ParseException
	 */
	public static long ParseRawDate(String date) throws ParseException{
		return dateFormat.parse(date).getTime()/1000;
	}
	
	public static String formatDate(long time_epoch){
		Date d = new Date(time_epoch * 1000);
		return dateFormat.format(d);
	}
	
	/**
	 * Check message-id format.
	 * We accept only [0-9A-za-z.] in random
	 * and [0-9A-za-z.-] in host part.
	 * 
	 * 
	 * @param boolean
	 * @return true if ok.
	 */
	public static boolean matchMsgId(final String messageId){
		//String mId = messageId.replaceAll("[^\\w^<^>^@^.^-]", "");//clear message id from unsupported characters
		return messageId.matches(NNTPConnection.MESSAGE_ID_PATTERN);
	}

    private Headers() {
    }
}
