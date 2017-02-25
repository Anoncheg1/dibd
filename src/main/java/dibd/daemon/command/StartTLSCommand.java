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
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
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
import dibd.daemon.TLS;
import dibd.daemon.command.IhaveCommand.PostState;
import dibd.storage.StorageBackendException;
import dibd.storage.StorageManager;
import dibd.storage.StorageNNTP;
import dibd.storage.GroupsProvider.Group;
import dibd.storage.article.Article;

/**
 *  Syntax
 *     STARTTLS
 *
 *  Responses
 *
 *     382 Continue with TLS negotiation
 *     502 Command unavailable [1]
 *     580 Can not initiate TLS negotiation 
 *        If the server is unable to initiate the TLS negotiation for any
 *          reason (e.g., a server configuration or resource problem)
 *
 *  [1] If a TLS layer is already active, or if authentication has
 *  occurred, STARTTLS is not a valid command (see Section 2.2.2).
 *
 *  NOTE: Notwithstanding Section 3.2.1 of [NNTP], the server MUST NOT
 *  return either 480 or 483 in response to STARTTLS.
 * @author me
 * @since dibd/1.0.1
 */
public class StartTLSCommand implements Command {

	//enum HandshakeState { WaitForHandshake, Working, Finished };
	
	//private HandshakeState internalState = HandshakeState.WaitForHandshake;
	
	//private HandshakeState state = HandshakeState.WaitForHandshake;
	
	@Override
	public String[] getSupportedCommandStrings() {
		return new String[] { "STARTTLS" };
	}

	@Override
	public boolean hasFinished() {
		return true;
	}

	@Override
	public String impliedCapability() {
		return "STARTTLS";
	}

	@Override
	public boolean isStateful() {
		return false; 
	}

	
	
	@Override
	public void processLine(NNTPInterface conn, final String line, byte[] raw)
			throws IOException, StorageBackendException {

		if (line.equalsIgnoreCase("STARTTLS")){
			
			if (TLS.isContextCreated()){

				conn.println("382 Continue with TLS negotiation");
				TLS tls = conn.getTLS();
				if (tls == null){

					tls = new TLS((NNTPConnection)conn);

					if(tls.connect())
						conn.setTLS(tls);
					else //fail tls negotiation
						conn.println("580 Can not initiate TLS negotiation");
					
				}else{
					System.out.println("shutdown TLS1");
					tls.shutdown();
					System.out.println("shutdown TLS2");
				}

			}else{
				conn.println("580 Can not initiate TLS negotiation");
				return;
			}
		}
	}
}
