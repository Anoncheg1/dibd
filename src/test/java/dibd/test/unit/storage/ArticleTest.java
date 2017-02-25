package dibd.test.unit.storage;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.mail.internet.MimeUtility;

import org.junit.Test;
import org.mockito.Mockito;
import dibd.config.Config;
import dibd.daemon.NNTPInterface;
import dibd.storage.AttachmentProvider;
import dibd.storage.StorageBackendException;
import dibd.storage.StorageManager;
import dibd.storage.StorageNNTP;
import dibd.storage.article.Article;

public class ArticleTest {
	
	private StorageNNTP storage; //mock
	private NNTPInterface conn; //mock
	private AttachmentProvider aprov;//mock
	
	public ArticleTest(){
		//try {
		this.storage = Mockito.mock(StorageNNTP.class);
		StorageManager.enableProvider(new TestingStorageProvider(storage));
		this.conn = Mockito.mock(NNTPInterface.class);
		this.aprov = Mockito.mock(AttachmentProvider.class);
		StorageManager.enableAttachmentProvider(aprov);
		Config.inst().set(Config.HOSTNAME, "127.0.0.1"); //added to path 
			
			//a = new Article(1, "midrandom", "host.com", null, "subject", "message", 
				//	"Thu, 02 May 2013 12:16:44 +0000", "host!host2", "local.test", 2);
		//} catch (ParseException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
		//}
	}
	
	@Test
    public void methodGetSource() throws IOException {
		//test file attachment
		String group = "local.test";
		String fileName = "fakefakefakefakefakefakefakefake";
		when(this.aprov.readFile(group, fileName)).thenReturn(fileName.getBytes());//file contains fileName

		int thread_id = 777;
		Article resa1 = new Article(null, thread_id, "<message-id@host.com>", "host.com", null, "сабджект", "фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы\nфывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы\nфывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы\nфывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы\nфывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы\nфывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы", 
				1466589502, "host!host2", group, null, null); //without image
		
		Article resa2 = new Article(null, thread_id, "<message-id@host.com>", "host.com", "петрик <собака@бфка>", "сабджект", "message", 
				1466589502, "host!host2", group, fileName, "img/png");//with image
		
		Article retA= new Article(thread_id, thread_id, "<ref-message-id@foo.bar>", "foo.bar", null, "subject", "message", 
				1466589502, "", group, null, null);
		try {
			when(storage.getArticle(null, thread_id)).thenReturn(retA);
		} catch (StorageBackendException e) {e.printStackTrace();}
				
		String res = null; //without image 1)
		String resWF = null;//with image 2)
        try {
			res = resa1.buildNNTPMessage(Charset.forName("UTF-8"),0);
			resWF = resa2.buildNNTPMessage(Charset.forName("UTF-8"),0);
		} catch (IOException e) {
			org.junit.Assert.fail("IOException "+e.getMessage());
		}
        
        //TEST FOR ARTICLE WITHOUT file 1)
        
        StringBuilder buf = new StringBuilder();
        buf.append("MIME-Version: 1.0").append("\r\n");
        buf.append("Date: Wed, 22 Jun 2016 09:58:22 +0000").append("\r\n");
        buf.append("Message-ID: <message-id@host.com>").append("\r\n");
        buf.append("Newsgroups: "+group).append("\r\n");
        buf.append("Subject: "+MimeUtility.encodeWord("сабджект")).append("\r\n");
        buf.append("References: <ref-message-id@foo.bar>").append("\r\n");
        buf.append("Path: 127.0.0.1!host!host2").append("\r\n");
        buf.append("Content-Type: text/plain; charset=utf-8").append("\r\n");
        buf.append("Content-Transfer-Encoding: 8bit").append("\r\n\r\n");
        buf.append("фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы\nфывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы\nфывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы\nфывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы\nфывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы\nфывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы");
        //String ss= "фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы\nфывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы\nфывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы\nфывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы\nфывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы\nфывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы фывфывфы";
        //System.out.println(new String(res));
        //System.out.println(buf.toString());
        assertTrue(buf.toString().equalsIgnoreCase(new String(res)));
        
        //TEST FOR ARTICLE WITH FILE 2)
        
        //split resWF to parts
        String wf = new String(resWF);
        String boundary = null;
        Pattern pattern = Pattern.compile("boundary=\"(.*)\"");
        Matcher matcher = pattern.matcher(wf);
        if (matcher.find())
        	boundary = matcher.group(1);
        String part[] = wf.split("\r\n--"+boundary+"\r\n");
        
        buf = new StringBuilder();
        buf.append("mime-version: 1.0").append("\r\n");
        buf.append("from: "+MimeUtility.encodeWord("петрик <собака@бфка>")).append("\r\n");
        buf.append("date: Wed, 22 Jun 2016 09:58:22 +0000").append("\r\n");
        buf.append("message-id: <message-id@host.com>").append("\r\n");
        buf.append("newsgroups: "+group).append("\r\n");
        buf.append("subject: "+MimeUtility.encodeWord("сабджект")).append("\r\n");
        buf.append("references: <ref-message-id@foo.bar>").append("\r\n");
        buf.append("path: 127.0.0.1!host!host2").append("\r\n");
        buf.append("content-transfer-encoding: 8bit").append("\r\n");
        buf.append("content-type: multipart/mixed; boundary=\"\"")
        .append("\r\n"); //boundary cutted
        assertTrue(buf.toString().equalsIgnoreCase(part[0].replace(boundary, ""))); //headers part
        
        buf = new StringBuilder();
        buf.append("content-type: text/plain; charset=utf-8").append("\r\n");
        buf.append("content-transfer-encoding: base64").append("\r\n\r\n");
        buf.append("bWVzc2FnZQ==");
        assertTrue(buf.toString().equalsIgnoreCase(part[1])); //message part
        
        buf = new StringBuilder();
        buf.append("content-type: img/png").append("\r\n");
        buf.append("content-disposition: attachment; filename=\""+fileName+"\"").append("\r\n");
        buf.append("content-transfer-encoding: base64").append("\r\n\r\n"); //empty line between headers and body
        buf.append("ZmFrZWZha2VmYWtlZmFrZWZha2VmYWtlZmFrZWZha2U=").append("\r\n"); // no need empty line
        buf.append("----"); //left after removing boundary (not important at all)
        
        assertTrue(buf.toString().equalsIgnoreCase(part[2].replace(boundary, ""))); //image part
        
        //String boundary = new String(resWF).re
        //System.out.println(new String(resWF));
        //
    }
}
