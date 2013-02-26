package controllers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import models.Notification;
import models.NotificationConnection;
import models.User;

import play.Logger;
import play.mvc.*;
import play.mvc.Http.MultipartFormData.FilePart;
import play.mvc.Http.*;
import utils.FileInfo;

public class Uploads extends Controller {
	
	public static Result getUpload(Long id) {
		if (FirstUse.isFirstUse())
    		return redirect(routes.FirstUse.getFirstUse());
		
		User user = User.authenticate(request(), session());
		if (user == null)
			return redirect(routes.Login.login());
		
		Map<String,Object> result = getUploadInfo(id);
		
		return ok(play.libs.Json.toJson(result));
    }
	
	public static Map<String, Object> getUploadInfo(Long id) {
		models.Upload upload = models.Upload.find.byId(id);
		List<FileInfo> fileList;
		if (upload == null) {
			Logger.debug("Upload #"+id+" was not found.");
			fileList = new ArrayList<FileInfo>();
		} else {
			Logger.debug("Listing files from upload #"+id+"...");
			fileList = upload.listFiles();
			Logger.debug("Found "+fileList.size()+" files in upload #"+id);
		}
		
		Map<String,Object> result = new HashMap<String,Object>();
		result.put("id", id);
		result.put("fileName", upload.getFile().getName());
		result.put("contentType", upload.contentType);
		result.put("total", upload.getFile().length());
		List<Map<String,Object>> jsonFileset = new ArrayList<Map<String,Object>>();
		for (FileInfo fileInfo : fileList) {
			Map<String,Object> file = new HashMap<String,Object>();
			file.put("fileName", fileInfo.href);
			file.put("contentType", fileInfo.contentType);
			file.put("total", fileInfo.size);
			file.put("isXML", fileInfo.contentType != null && (fileInfo.contentType.equals("application/xml") || fileInfo.contentType.equals("text/xml") || fileInfo.contentType.endsWith("+xml")));
			jsonFileset.add(file);
		}
		result.put("fileset", jsonFileset);
		
		return result;
    }
	
	public static Result postUpload() {
		if (FirstUse.isFirstUse())
			return forbidden();
		
		User user = User.authenticate(request(), session());
		if (user == null)
			return forbidden();
		
        MultipartFormData body = request().body().asMultipartFormData();
        List<FilePart> files = body.getFiles();
        
        List<Map<String,Object>> filesResult = new ArrayList<Map<String,Object>>();
        
        for (FilePart file : files) {
        	Logger.debug(request().method()+" | "+file.getContentType()+" | "+file.getFilename()+" | "+file.getFile().getAbsolutePath());
        	
        	Map<String,Object> fileObject = new HashMap<String,Object>();
        	fileObject.put("name", file.getFilename());
        	fileObject.put("size", file.getFile().length());
        	filesResult.add(fileObject);
        	
        	String browserIdString = request().queryString().containsKey("browserId") ? request().queryString().get("browserId")[0] : null;
        	Long browserId = null;
        	if (browserIdString != null) try {
        		browserId = Long.parseLong(browserIdString);
        	} catch (NumberFormatException e) {
        		Logger.error("Could not parse browserId from upload: "+browserIdString, e);
        	}
        	
        	Long uploadId = models.Upload.store(file, user, browserId);
        	
        	NotificationConnection.push(user.id, browserId, new Notification("uploads", getUploadInfo(uploadId)));
        }
        
        Map<String,List<Map<String,Object>>> result = new HashMap<String,List<Map<String,Object>>>();
        result.put("files", filesResult);
        
		response().setContentType("text/html");
		return ok(play.libs.Json.toJson(result));
		
    }

}
