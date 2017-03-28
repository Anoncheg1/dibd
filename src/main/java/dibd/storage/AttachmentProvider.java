package dibd.storage;

import java.awt.Image;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Level;

import javax.imageio.ImageIO;

import org.im4java.core.ConvertCmd;
import org.im4java.core.IMOperation;
import org.im4java.process.ProcessStarter;

import dibd.storage.GroupsProvider.Group;
import dibd.util.Log;

//TODO:something with svg+xml format
//user@localhost:~$ convert -thumbnail 200 Roda\ Emosi\ Plutchik.svg a.png
public class AttachmentProvider {
	private final String attachmentPath;
	/**
	 * "/img/"
	 */
	private static final String aimg = "/img/";
	/**
	 * "/thm/"
	 */
	private static final String thumbnails = "/thm/";
	
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
				.append(aimg)
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
				.append(thumbnails)
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
 				if(!bdir.mkdir())
 					throw new Error("Can't create attachments direcotry for group which is not exist");
 	        }
 			File img = new File(patha+"/img/");
				if (!img.exists()){
					if(!img.mkdir())
						throw new Error("Can't create attachments direcotry for /img/ which is not exist");
				}
				File thm = new File(patha+"/thm/");
				if (!thm.exists()){
					if(!thm.mkdir())
						throw new Error("Can't create attachments direcotry for /thm/ which is not exist");
				}
 		}
		
	}
	
	/**
	 * If format is not supported for thumbnail then new format will be returned
	 * (used in ArticleWeb)
	 * fix for svg files.   if we have svg we convert to png
	 * 
	 * @param fileName
	 * @return
	 */
	public String checkSupported(String fileName){
		String f[] = fileName.split("[.]");
		if (f.length == 2 && f[1].toLowerCase().contains("svg"))
			fileName = f[0]+".png";
		return fileName;
	}
	
	public void createThumbnail(String groupName, String fileName, String media_type){
		if (media_type.substring(0, 6).equals("image/")){//we will add other formats later
			try{
				String pathSource = getAPath(groupName, fileName);   //1
				String pathTarget = getTnPath(groupName, fileName);    //2

				File sourceFile = new File(pathSource);
				File thumbNailFile = new File(pathTarget);
				
				//TODO: what about horizontal?
				//check height of image. if image is small we just copy.
				boolean gif = false;
				Image image = ImageIO.read(sourceFile);
				if (image != null){
					int height = image.getHeight(null);
					gif = media_type.toLowerCase().contains("gif");
					if (height > 0 && (!gif && height < 200 || gif && height <= 40)){ //-1 error
						Files.copy(sourceFile.toPath(), thumbNailFile.toPath());
						return;
					}
				}
				ConvertCmd cmd = new ConvertCmd();
				
				if (sourceFile.exists() && !thumbNailFile.exists() ) {
					
					IMOperation op = new IMOperation();
					op.addImage(sourceFile.getCanonicalPath());
					
					if (gif)
						op.thumbnail(null,40);//horizontal and vertical density
					else
						op.thumbnail(null,200);//horizontal and vertical density
					op.addImage(thumbNailFile.getCanonicalPath());
					//System.out.println(op.getCmdArgs());
					cmd.run(op);
				}
			} catch (Exception e) {
				Log.get().log(Level.WARNING, "Can not create thumbnail: {0}", e);
			}
		}
	}
	
	
	
	public void saveFile (String groupName, String fileName, byte[] data) throws IOException{
		String fullFileName = this.getAPath(groupName, fileName);
		File ofile = new File(fullFileName);
		if (ofile.createNewFile()) {

			FileOutputStream fos = new FileOutputStream(ofile);
			try{
				fos.write(data);
				fos.flush();
			}finally{
				fos.close();
			}

		}
	}
	
	public byte[] readFile (String groupName, String fileName) throws IOException{
		Path path = Paths.get(this.getAPath(groupName, fileName));
		return Files.readAllBytes(path);
	}
	
	/**
	 * Delete attachment and thumbnail if exist
	 * 
	 * @param groupName
	 * @param fileName
	 */
	public void delFile (String groupName, String fileName){
		File fileImg = new File(getAPath(groupName, fileName)); //img
		File fileThm = new File(this.getTnPath(groupName, fileName)); //thm
		if(!fileImg.delete())
			Log.get().warning("Can not detete image "+groupName+" "+fileName);
		if(fileThm.exists())
			if(!fileThm.delete())
				Log.get().warning("Can not detete thumbnail "+groupName+" "+fileName);
	}
}
