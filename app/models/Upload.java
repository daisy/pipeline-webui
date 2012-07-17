package models;

import play.*;
import play.db.ebean.Model;
import play.mvc.Http.MultipartFormData.FilePart;
import utils.ContentType;
import utils.FileInfo;
import utils.Files;

import javax.persistence.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.*;

@Entity
public class Upload extends Model {
	
	@Id
	public Long id;
	
	public String absolutePath;
	public String contentType;
	public Date uploaded;
	
	public Long user;
	public String job;
	
	@Transient
	private List<FileInfo> fileList;
	
	private Upload() {
		this.uploaded = new Date();
	}
	
	public static Long store(FilePart upload, User user) {
		Upload u = new Upload();
		u.save(); // saving here generates the unique id for this upload
		
		File uploadDir = new File(Setting.get("uploads")+u.id);
		uploadDir.mkdirs();
		u.absolutePath = uploadDir.getAbsolutePath();
		
        File file = upload.getFile();
		File newFile = new File(uploadDir, upload.getFilename());
		file.renameTo(newFile);
		file = newFile;
		Logger.debug("Stored uploaded file as: "+file.getAbsolutePath()+" (=="+new File(uploadDir, upload.getFilename()).getAbsolutePath()+")");
		try {
			u.contentType = ContentType.probe(file.getName(), new FileInputStream(file));
		} catch (FileNotFoundException e) {
			Logger.error("Was unable to probe "+file.getAbsolutePath()+" : "+e.getMessage());
			u.contentType = upload.getContentType();
		}
		
		u.user = user.id;
		
		u.save();
		
		return u.id;
	}
	
	public List<FileInfo> listFiles() {
		if (fileList != null)
			return fileList;
		
		fileList = new ArrayList<FileInfo>();
		
		Logger.debug("opening "+this.absolutePath);
		File uploadDir = new File(this.absolutePath);
		if (uploadDir.isDirectory()) {
			Logger.debug(this.absolutePath+" is a directory...");
			File[] dirContents = uploadDir.listFiles();
			if (dirContents.length > 0) {
				Logger.debug(this.absolutePath+" contains 1 or more files...");
				File file = dirContents[0];
				
				if (isZip()) {
					Logger.debug(this.absolutePath+" is a ZIP file, listing ZIP entries...");
					fileList = Files.listZipFilesWithContentType(file);
					
				} else {
					Logger.debug(this.absolutePath+" is a normal file...");
					fileList.add(new FileInfo(file.getName(), this.contentType, file.length()));
				}
			}
		}
		
		return fileList;
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
	
	// -- Queries
	
	public static Finder<Long,Upload> find = new Finder<Long,Upload>(Long.class, Upload.class);
	
	/** Retrieve a Upload by its id. */
    public static Upload findById(Long id) {
        return find.where().eq("id", id).findUnique();
    }
    
//    /** Delete all Uploads belonging to a user */
//	public static void deleteUserUploads(User user) {
//		for (Upload upload : find.where().eq("user", user.id).findList()) {
//			upload.delete();
//		}
//	}
    
    @Override
    public void delete() {
    	File file = getFile();
    	if (file != null && file.exists())
    		file.delete();
    	super.delete();
    }
}
