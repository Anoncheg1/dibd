/**
 * 
 */
package dibd.storage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;

import dibd.storage.GroupsProvider.Group;
import dibd.storage.article.Article;
import dibd.util.Log;

/**
 * Singleton
 * @author user
 *
 */
public class NNTPCacheProvider {
	private final String cachePath;
	
	private String buildPath(String groupName, String messageId){
		return new StringBuilder(this.cachePath)
				.append(groupName)
				.append("/")
				.append(messageId)
				.toString();
	}
	
	public NNTPCacheProvider(final String savePath) throws Exception{
		if (savePath.charAt(savePath.length()-1) != '/')
			this.cachePath = savePath + '/';
		else
			this.cachePath = savePath;
		
		
		// NNTP cache
        File ncdir = new File(this.cachePath);
        if (!ncdir.exists()){
        	if(!ncdir.mkdir()){
        		throw new Exception("Can't create NNTP Cache direcotry");        		
        	}
        }
        List<Group> boards = StorageManager.groups.getAll();
        for (Group gr : boards){
 			String p = this.cachePath + gr.getName();
 			File boardd = new File(p);
 			if (!boardd.exists()){
 				if(!boardd.mkdir()){
 	        		throw new Exception("Can't create NNTP Cache direcotry: "+p);        		
 	        	};
 	        }
        }
	}
	
	/**
	 * We cache with "\r\n" at the end.
	 * 
	 * @param groupName
	 * @param messageId
	 * @param data
	 * @throws IOException
	 */
	public void saveFile (String groupName, String messageId, byte[] data) throws IOException{
		File ofile = new File(buildPath(groupName, messageId));
		if (ofile.createNewFile()) {

			FileOutputStream fos = new FileOutputStream(ofile);
			fos.write(data);
			fos.close();

		}
	}

	/**
	 * Del article from cache from cache/group/ with message-id like {@literal <}random{@literal @}host{@literal >}
	 * 
	 * @param groupName
	 * @param messageId
	 */
	public void delArticle(String groupName, String messageId) {
		File fl = new File(buildPath(groupName, messageId));
		fl.delete();
	}

	/**
	 * Return FileInputStream or null if file can't be read or not exist.
	 * We cache with "\r\n" at the end".
	 * Do not forget to close FileInputStream,
	 * 
	 * @param article
	 * @return
	 */
	public FileInputStream getFileStream(Article article) {
		File f = new File(buildPath(article.getGroupName(), article.getMessageId()));
		try {
			return new FileInputStream(f);
		} catch (FileNotFoundException e) {
			Log.get().log(Level.WARNING, "NNTPCacheProvider.readFile(): {0}", e);
			return null;
		}		
	}
}
