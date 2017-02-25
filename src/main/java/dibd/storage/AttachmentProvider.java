package dibd.storage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.im4java.core.ConvertCmd;
import org.im4java.core.IM4JavaException;
import org.im4java.core.IMOperation;
import org.im4java.process.ProcessStarter;

import dibd.config.Config;
import dibd.storage.GroupsProvider.Group;

//TODO:something with svg+xml format
//user@localhost:~$ convert -thumbnail 200 Roda\ Emosi\ Plutchik.svg a.png
public class AttachmentProvider {
	private final String attachmentPath;
	/**
	 * "/img/"
	 */
	private final String aimg = "/img/";
	/**
	 * "/thm/"
	 */
	private final String thumbnails = "/thm/";
	
	/**
	 * Get path to attachment src file.
	 * 
	 * @param groupName
	 * @param fileName
	 * @return path
	 */
	private String getAPath(String groupName, String fileName){
		return new StringBuilder(this.attachmentPath)
				.append(groupName)
				.append(this.aimg)
				.append(fileName)
				.toString(); //1
	}
	
	/**
	 * Get path to thumbnail of attachment file.
	 * 
	 * @param groupName
	 * @param fileName
	 * @return path
	 */
	private String getTnPath(String groupName, String fileName){
		return new StringBuilder(this.attachmentPath)
				.append(groupName)
				.append(this.thumbnails)
				.append(checkSupported(fileName))
				.toString(); //2
	}

	/**
	 * Initializate attachments and thumbnailer
	 *
	 * @param savePath main folder
	 * @param IMpath
	 * @throws Error 
	 */
	public AttachmentProvider(final String savePath, final String IMpath) throws Error {
		if (savePath.charAt(savePath.length()-1) != '/')
			this.attachmentPath = savePath + '/';
		else
			this.attachmentPath = savePath;
		ProcessStarter.setGlobalSearchPath(IMpath);
		
		//create dirs 
		File atdir = new File(this.attachmentPath);
        if (!atdir.exists()){
        	if(!atdir.mkdir()){
        		throw new Error("Can't create attachments direcotry which is not exist");        		
        	}
        }
        List<Group> boards = StorageManager.groups.getAll();
 		for (Group g : boards){
 			String patha = this.attachmentPath + g.getName();
 			File bdir = new File(patha);
 			if (!bdir.exists()){
 				bdir.mkdir();
 	        }
 			File img = new File(patha+"/img/");
				if (!img.exists()){
					img.mkdir();
				}
				File thm = new File(patha+"/thm/");
				if (!thm.exists()){
					thm.mkdir();
				}
 		}
		
	}
	
	/**
	 * If format is not supported for thumbnail then new format will be returned
	 * (used in ArticleWeb)
	 * @param fileName
	 * @return
	 */
	public String checkSupported(String fileName){
		String f[] = fileName.split("[.]");
		if (f[1].toLowerCase().contains("svg"))
			fileName = f[0]+".png";
		return fileName;
	}
	
	

	public void createThumbnail(String groupName, String fileName)
			throws IOException, InterruptedException, IM4JavaException {
		String pathSource = getAPath(groupName, fileName);   //1
		String pathTarget = getTnPath(groupName, fileName);    //2
		
		ConvertCmd cmd = new ConvertCmd();
		File sourceFile = new File(pathSource);
		File thumbNailFile = new File(pathTarget);
		if (sourceFile.exists() && !thumbNailFile.exists() ) {
			IMOperation op = new IMOperation();
			op.addImage(sourceFile.getCanonicalPath());
			op.thumbnail(null,200);
			op.addImage(thumbNailFile.getCanonicalPath());
			//System.out.println(op.getCmdArgs());
			cmd.run(op);
		}
	}
	
	
	
	public void saveFile (String groupName, String fileName, byte[] data) throws IOException{
		String fullFileName = this.getAPath(groupName, fileName);
		File ofile = new File(fullFileName);
		if (ofile.createNewFile()) {

			FileOutputStream fos = new FileOutputStream(ofile);
			fos.write(data);
			fos.close();

		}
	}
	
	public byte[] readFile (String groupName, String fileName) throws IOException{
		System.out.println(groupName+fileName);
		Path path = Paths.get(this.getAPath(groupName, fileName));
		return Files.readAllBytes(path);
	}
	
	public void delFile (String groupName, String fileName){
		File fileImg = new File(getAPath(groupName, fileName)); //img
		File fileThm = new File(getAPath(groupName, fileName)); //thm
		fileImg.delete();
		fileThm.delete();
	}
}
