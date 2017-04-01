/**
 * 
 */
package dibd.storage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
	 * @param tmpfile
	 * @throws IOException
	 */
	public File saveFile (String groupName, String messageId, File tmpfile) throws IOException{
		String cachedPath = buildPath(groupName, messageId);
		File newfile = new File(cachedPath);
		Files.move(tmpfile.toPath(), newfile.toPath(), StandardCopyOption.REPLACE_EXISTING);
		return newfile;
	}

	
	/*	File ofile = new File(buildPath(groupName, messageId));
	if (! ofile.createNewFile()){ //we check that file must be created or overcreated.
		if(ofile.delete()){
			if(! ofile.createNewFile())
				throw new IOException("Can not create cache file after delete the same file. crazy "+messageId);
		}else
			throw new IOException("catch file already exist and can not be deleted "+messageId);
	}

	FileOutputStream fos = new FileOutputStream(ofile);
	try {
		fos.write(data);
	}finally{
		fos.close();
	}
	
	return ofile;*/
	
	/*public File saveFile (String groupName, String messageId, InputStream fis) throws IOException{
		FileOutputStream fos = null;
		try {
			File ofile = new File(buildPath(groupName, messageId));
			if (! ofile.createNewFile()){ //we check that file must be created or overcreated.
				if(ofile.delete()){
					if(! ofile.createNewFile())
						throw new IOException("Can not create cache file after delete the same file. crazy "+messageId);
				}else
					throw new IOException("catch file already exist and can not be deleted "+messageId);
			}

			fos = new FileOutputStream(ofile);

			int read = 0;
			byte[] bytes = new byte[1024];

			while ((read = fis.read(bytes)) != -1) {
				fos.write(bytes, 0, read);
			}

			return ofile;
		}finally{
			fis.close();
			if (fos != null)
				fos.close();
		}
		

	}*/

	/**
	 * Del article from cache from cache/group/ with message-id like {@literal <}random{@literal @}host{@literal >}
	 * 
	 * @param groupName
	 * @param messageId
	 */
	public void delArticle(String groupName, String messageId) {
		File fl = new File(buildPath(groupName, messageId));
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
		File f = new File(buildPath(article.getGroupName(), article.getMessageId()));
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
