/**
 * 
 */
package dibd.storage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
	private final File cachePath;
	
	private File buildPath(String groupName, String messageId){
		return new File(new File(cachePath, groupName), messageId);
	}

	public NNTPCacheProvider(final String savePath) throws Exception{
		cachePath = new File(savePath);
		cachePath.mkdir();
		
		assert(cachePath.exists());
		
		List<Group> boards = StorageManager.groups.getAll();
        for (Group gr : boards){
        	File grDir = new File(cachePath, gr.getName());
 			grDir.mkdir();
 			assert(grDir.exists());
        }
	}
	
	/**
	 * We cache with "\r\n" at the end.
	 * 
	 * @param groupName
	 * @param messageId
	 * @param tmpfile
	 * @throws IOException
	 */
	public File saveFile (String groupName, String messageId, File tmpfile) throws IOException{
		File newfile = buildPath(groupName, messageId);
		Files.move(tmpfile.toPath(), newfile.toPath(), StandardCopyOption.REPLACE_EXISTING);
		return newfile;
	}

	
	/**
	 * Del article from cache from cache/group/ with message-id like {@literal <}random{@literal @}host{@literal >}
	 * 
	 * @param groupName
	 * @param messageId
	 */
	public void delArticle(String groupName, String messageId) {
		File fl = buildPath(groupName, messageId);
		if(!fl.delete())
			Log.get().log(Level.WARNING, "Fail to delete article: {0}", messageId);
	}
	
	
	/**
	 * Roll back
	 * 
	 * @param fl
	 */
	public void delFile(File fl) {
		if(!fl.delete())
			Log.get().warning("Fail to delete article for RollBack");
	}

	/**
	 * Return FileInputStream or null if file can't be read or not exist.
	 * We cache with "\r\n" at the end".
	 * Do not forget to close FileInputStream,
	 * 
	 * Used in Article to check if cache exist even if article seems ours(maybe returned missing thread)
	 * 
	 * @param article
	 * @return null if not exist
	 */
	public FileInputStream getFileStream(Article article) {
		File f = buildPath(article.getGroupName(), article.getMessageId());
		try {
			return new FileInputStream(f);
		} catch (FileNotFoundException e) {
			return null;
		}		
	}
	
	/**
	 * create temp file that will be deleted on JVM exit, but it is recommended to delete it right after use.
	 * 
	 * @param messageId
	 * @return FileOutputStream
	 * @throws IOException 
	 */
	public File createTMPfile(String messageId) throws IOException {
		File temp = File.createTempFile(messageId, "");
		temp.deleteOnExit();
		if (temp.canWrite())
			return temp;
		else 
			throw new IOException("temp file is not writable");

	}
	
}
