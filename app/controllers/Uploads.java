package controllers;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.jackson.node.ObjectNode;

import models.Upload;
import models.User;

import play.Logger;
import play.libs.Json;
import play.mvc.*;
import play.mvc.Http.MultipartFormData.FilePart;
import play.mvc.Http.*;
import utils.FileInfo;

public class Uploads extends Controller {
	
	public static Result getUpload(Long id) {
		if (FirstUse.isFirstUse())
    		return redirect(routes.FirstUse.getFirstUse());
		
		User user = User.authenticate(session("email"), session("password"));
		if (user == null)
			return redirect(routes.Login.login());
		
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
		result.put("uploadId", id);
		result.put("href", upload.getFile().getName());
		result.put("contentType", upload.contentType);
		result.put("size", upload.getFile().length());
		List<Map<String,Object>> jsonFileset = new ArrayList<Map<String,Object>>();
		for (FileInfo fileInfo : fileList) {
			Map<String,Object> file = new HashMap<String,Object>();
			file.put("href", fileInfo.href);
			file.put("contentType", fileInfo.contentType);
			file.put("size", fileInfo.size);
			jsonFileset.add(file);
		}
		result.put("fileset", jsonFileset);
		
		return ok(play.libs.Json.toJson(result));
    }
	
	public static Result postUpload() {
		if (FirstUse.isFirstUse())
    		return redirect(routes.FirstUse.getFirstUse());
		
		User user = User.authenticate(session("email"), session("password"));
		if (user == null)
			return redirect(routes.Login.login());
		
		Logger.debug(request().getHeader("Content-Type"));
		MultipartFormData body = request().body().asMultipartFormData();
		List<FilePart> files = body.getFiles();
		
		Long uploadId = models.Upload.store(files.get(0));
		
		response().setContentType("text/html");
		return ok("{\"success\":true,\"uploadId\":"+uploadId+"}");

    }

}
