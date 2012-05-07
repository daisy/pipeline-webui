package controllers;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.jackson.node.ObjectNode;

import models.User;

import play.Logger;
import play.libs.Json;
import play.mvc.*;
import play.mvc.Http.MultipartFormData.FilePart;
import play.mvc.Http.*;

public class Uploads extends Controller {
	
	public static Result getUpload(Long id) {
		if (FirstUse.isFirstUse())
    		return redirect(routes.FirstUse.getFirstUse());
		
		User user = User.authenticate(session("email"), session("password"));
		if (user == null)
			return redirect(routes.Login.login());
		
		models.Upload upload = models.Upload.find.byId(id);
		Map<String,String> fileMap;
		if (upload == null) {
			Logger.debug("Upload #"+id+" was not found.");
			fileMap = new HashMap<String,String>();
		} else {
			Logger.debug("Listing files from upload #"+id+"...");
			fileMap = upload.listFiles();
			Logger.debug("Found "+fileMap.size()+" files in upload #"+id);
		}
		
		List<Map<String,String>> jsonFileset = new ArrayList<Map<String,String>>();
		for (String filename : fileMap.keySet()) {
			Map<String,String> file = new HashMap<String,String>();
			file.put("href", filename);
			file.put("contentType", fileMap.get(filename));
			jsonFileset.add(file);
		}
		return ok(play.libs.Json.toJson(jsonFileset));
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
		
		/* // Multiple files per upload request (not supported with the current JavaScript upload library)
		Map<String,Long> uploadIds = new HashMap<String,Long>();
		for (FilePart upload : files) {
			File file = upload.getFile();
			if ("".equals(file.getName()) || file.length() == 0)
				continue;
			Logger.info("File = "+file.getName()+" | Size = "+file.length()+" | contentType = "+upload.getContentType());
			uploadIds.put(upload.getFilename(), models.Upload.store(upload));
		}
		*/
		
		Long uploadId = models.Upload.store(files.get(0));
		
		response().setContentType("text/html");
		return ok("{\"success\":true,\"uploadId\":"+uploadId+"}");

    }

}
