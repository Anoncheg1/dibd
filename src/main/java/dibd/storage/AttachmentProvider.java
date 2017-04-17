package dibd.storage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Level;

import org.im4java.core.ConvertCmd;
import org.im4java.core.IMOperation;
import org.im4java.core.IdentifyCmd;
import org.im4java.process.OutputConsumer;
import org.im4java.process.ProcessStarter;

import dibd.storage.GroupsProvider.Group;
import dibd.util.Log;

//TODO:something with svg+xml format
//user@localhost:~$ convert -thumbnail 200 Roda\ Emosi\ Plutchik.svg a.png
public class AttachmentProvider {
	private final File attachmentDir;
	
	public static enum Atype {img, thm};//original attachments and thumbnails
	
	/**
	 * Get path to attachment file.
	 * 
	 * @param groupName
	 * @param fileName
	 * @param attach
	 * @return
	 */
	public File getPath(final String groupName, final String fileName, final Atype attach){
		String fname;
		if (attach.equals(Atype.img))
			fname = fileName.replaceFirst("\\..*", "");
		else
			fname = fileName;
		return new File(new File(new File(attachmentDir, groupName), attach.toString()), fname);
	}
	
	
	/**
	 * Initializate attachments and thumbnailer
	 *
	 * @param savePath main folder
	 * @param IMpath
	 * @throws Error 
	 */
	public AttachmentProvider(final String savePath, final String IMpath) throws Error {
		
		ProcessStarter.setGlobalSearchPath(IMpath);
		
		attachmentDir = new File(savePath);
		attachmentDir.mkdir();
		
        List<Group> boards = StorageManager.groups.getAll();
 		for (Group g : boards){
 			File grDir = new File(attachmentDir, g.getName());
 			grDir.mkdir();
 			File gi = new File(grDir, Atype.img.toString());
 			gi.mkdir();
 			File gt = new File(grDir, Atype.thm.toString());
 			gt.mkdir();
 			if (! gi.exists() || ! gt.exists())
 				throw new Error("Attachment directory "+grDir.getAbsolutePath()+"does not exist");
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
	
	public volatile String[] res = null; //used only in createThumbnail. must be locked with
	public volatile String form = null; //used only in createThumbnail. must be locked with
	public volatile boolean multi = false; //for gif if true multiframe 
	
	public void createThumbnail(String groupName, String fileName, String media_type){
		if (media_type.substring(0, 6).equals("image/") || ! fileName.contains(".")){//we will add other formats later
			try{
				File sourceFile = getPath(groupName, fileName, Atype.img);   //1
				File thumbNailFile = getPath(groupName, fileName, Atype.thm);    //2

				assert(sourceFile.exists());
				
				///// identifying resolution  ///////
				IdentifyCmd icmd = new IdentifyCmd();
				IMOperation op = new IMOperation();
				op.addImage(sourceFile.getCanonicalPath());
				
				OutputConsumer ocanon = new OutputConsumer(){ //anonymouse class
					@Override
					public void consumeOutput(InputStream is) throws IOException {
						Scanner sc = null;
						try{
							sc = new java.util.Scanner(is,"UTF-8");
							java.util.Scanner scanner = sc.useDelimiter("\\A");
							///home/user/nogirls.jpg JPEG 960x887 960x887+0+0 8-bit sRGB 137KB 0.000u 0:00.000
							String theString = scanner.hasNext() ? scanner.next() : "";
							String[] ident = theString.split(" ");
							res = ident[2].split("x");
							form = ident[3];
							if (ident[0].contains("["))
								multi = true;
								
						}finally{
							if(sc != null)
								sc.close();
						}
					}		
				};
				icmd.setOutputConsumer(ocanon);
				
				if (thumbNailFile.exists())
					thumbNailFile.delete(); //may be from old deletet thread
				
				boolean gif = media_type.toLowerCase().contains("gif");
				
				String forml;
				boolean multi; 
				synchronized(this){ //rez lock
					icmd.run(op);
					
					//check resolution is normal
					if (res != null){
						int width = Integer.parseInt(res[0]);
						int height = Integer.parseInt(res[1]);
						
						if (width > 11000 || height > 11000) //TODO:make configurable
							return; //ABORT

						if (gif && this.multi)
							if ((width <= 50) && (height <= 50)){//if small gif just copy
								Files.copy(sourceFile.toPath(), thumbNailFile.toPath());
								return;
							}
					}
					res = null;
					forml = this.form;
					this.form = null;
					multi = this.multi;
					this.multi = false;
				}
				if (forml != null && ! forml.contains("no decode delegate for this image format")){
					///////   creating thumbnail  ////// 
					ConvertCmd cmd = new ConvertCmd();

					if (sourceFile.exists() && !thumbNailFile.exists() ) {

						op = new IMOperation();
						op.addImage(sourceFile.getCanonicalPath());

						if (gif && multi)
							op.thumbnail(null,50);//horizontal and vertical density
						else
							op.thumbnail(null,200);//horizontal and vertical density
						op.addImage(thumbNailFile.getCanonicalPath());
						//System.out.println(op.getCmdArgs());
						cmd.run(op);
					}
				}
			} catch (Exception e) {
				Log.get().log(Level.WARNING, "Can not create thumbnail: {0}", e.getLocalizedMessage());
			}
		}
	}
	
	
	
	public void saveFile (String groupName, String fileName, File file) throws IOException{
		assert(file.exists());
		File fdest = this.getPath(groupName, fileName, Atype.img);
		Files.move(file.toPath(), fdest.toPath(), StandardCopyOption.REPLACE_EXISTING);
	}
	
	/*public byte[] readFile (String groupName, String fileName) throws IOException{
		File ftoread = getPath(groupName, fileName, Atype.img);
		assert(ftoread.exists());
		return Files.readAllBytes(ftoread.toPath());
	}*/
	
	/**
	 * Delete attachment and thumbnail if exist
	 * 
	 * @param groupName
	 * @param fileName
	 */
	public void delFile (String groupName, String fileName){
		File fileImg = getPath(groupName, fileName, Atype.img); //img
		File fileThm = getPath(groupName, fileName, Atype.thm); //thm
		if(!fileImg.delete())
			Log.get().warning("Can not detete image "+groupName+" "+fileName);
		if(fileThm.exists())
			if(!fileThm.delete())
				Log.get().warning("Can not detete thumbnail "+groupName+" "+fileName);
	}
}
