package models;

import play.*;
import play.db.ebean.Model;
import play.mvc.Http.MultipartFormData.FilePart;
//import play.libs.MimeTypes;
import utils.Files;

import javax.persistence.*;

import java.io.File;
import java.util.*;

@Entity
public class Upload extends Model {
    
	@Id
	public Long id;
	
	public String absolutePath;
	public String contentType;
	public Date uploaded;
	
	public static Finder<Long,Upload> find = new Finder<Long,Upload>(Long.class, Upload.class);
	
	private Upload() {
		this.uploaded = new Date();
	}
	
	public static Long store(FilePart upload) {
		Upload u = new Upload();
		u.save(); // saving here generates the unique id for this row
		
		File uploadDir = new File(Setting.get("uploads")+u.id);
		uploadDir.mkdir();
		u.absolutePath = uploadDir.getAbsolutePath();
		
		File file = upload.getFile();
		file.renameTo(new File(uploadDir, upload.getFilename()));
		Logger.debug("Stored uploaded file as: "+file.getAbsolutePath());
		u.contentType = upload.getContentType();
		
		u.save();
		
		return u.id;
	}
	
	public Map<String, String> listFiles() {
		Map<String, String> fileMap = new HashMap<String,String>();
		
		Logger.debug("opening "+this.absolutePath);
		File uploadDir = new File(this.absolutePath);
		if (uploadDir.isDirectory()) {
			Logger.debug(this.absolutePath+" is a directory...");
			File[] dirContents = uploadDir.listFiles();
			if (dirContents.length > 0) {
				Logger.debug(this.absolutePath+" contains 1 or more files...");
				File file = dirContents[0];
				
				if ("application/zip".equals(this.contentType)) {
					Logger.debug(this.absolutePath+" is a ZIP file, listing ZIP entries...");
					fileMap = Files.listZipFilesWithContentType(file);
					
				} else {
					Logger.debug(this.absolutePath+" is a normal file...");
					fileMap.put(file.getName(), this.contentType);
				}
			}
		}
		
		return fileMap;
	}
	
	public File getFile() {
		File uploadDir = new File(this.absolutePath);
		if (uploadDir.isDirectory()) {
			File[] dirContents = uploadDir.listFiles();
			if (dirContents.length > 0) {
				return dirContents[0];
			}
		}
		Logger.debug("Could not retrieve the upload with id "+this.id+" from "+this.absolutePath);
		return null;
	}	
	
	public boolean isZip() {
		return "application/zip".equals(this.contentType);
	}
}
