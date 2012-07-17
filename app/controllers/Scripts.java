package controllers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import models.Notification;
import models.Setting;
import models.Upload;
import models.User;

import pipeline2.Pipeline2WSResponse;
import pipeline2.models.Script;
import pipeline2.models.script.*;
import pipeline2.models.script.arguments.ArgBoolean;
import pipeline2.models.script.arguments.ArgFile;
import pipeline2.models.script.arguments.ArgFiles;
import pipeline2.models.script.arguments.ArgString;
import pipeline2.models.script.arguments.ArgStrings;
import play.Logger;
import play.mvc.*;

public class Scripts extends Controller {
	
	public static Result getScripts() {
		if (FirstUse.isFirstUse())
    		return redirect(routes.FirstUse.getFirstUse());
		
		User user = User.authenticate(session("userid"), session("email"), session("password"));
		if (user == null)
			return redirect(routes.Login.login());
		
		Pipeline2WSResponse response = pipeline2.Scripts.get(Setting.get("dp2ws.endpoint"), Setting.get("dp2ws.authid"), Setting.get("dp2ws.secret"));
		
		if (response.status != 200) {
			return Application.error(response.status, response.statusName, response.statusDescription, response.asText());
		}
		
		List<Script> scripts = Script.getScripts(response);
		
		Long browserId = new Random().nextLong();
		synchronized (User.notificationQueues) {
			User.notificationQueues.putIfAbsent(user.id, new ConcurrentHashMap<Long,List<Notification>>());
			User.notificationQueues.get(user.id).putIfAbsent(browserId, new ArrayList<Notification>());
			Logger.debug("Browser: user #"+user.id+" opened browser window #"+browserId);
		}
		flash("browserId",""+browserId);
		return ok(views.html.Scripts.getScripts.render(scripts));
	}
	
	public static Result getScript(String id) {
		if (FirstUse.isFirstUse())
    		return redirect(routes.FirstUse.getFirstUse());
		
		User user = User.authenticate(session("userid"), session("email"), session("password"));
		if (user == null)
			return redirect(routes.Login.login());
		
		Pipeline2WSResponse response = pipeline2.Scripts.get(Setting.get("dp2ws.endpoint"), Setting.get("dp2ws.authid"), Setting.get("dp2ws.secret"), id);
		
		if (response.status != 200) {
			return Application.error(response.status, response.statusName, response.statusDescription, response.asText());
		}
		
		Script script = new Script(response);
		
		boolean uploadFiles = false;
		boolean hideAdvancedOptions = "true".equals(Setting.get("jobs.hideAdvancedOptions"));
		for (Argument arg : script.arguments) {
			if ("input".equals(arg.kind) || "anyFileURI".equals(arg.xsdType)) {
				uploadFiles = true;
			}
			if (hideAdvancedOptions && arg.required == false)
				arg.hide = true;
		}
		if (hideAdvancedOptions) {
			boolean hasHiddenOptions = false;
			for (Argument arg : script.arguments) {
				if (arg.hide) {
					hasHiddenOptions = true;
					break;
				}
			}
			if (!hasHiddenOptions)
				hideAdvancedOptions = false; // don't show "hide advanced options" control, if there are no advanced options
		}
		
		Long browserId = new Random().nextLong();
		synchronized (User.notificationQueues) {
			User.notificationQueues.putIfAbsent(user.id, new ConcurrentHashMap<Long,List<Notification>>());
			User.notificationQueues.get(user.id).putIfAbsent(browserId, new ArrayList<Notification>());
			Logger.debug("Browser: user #"+user.id+" opened browser window #"+browserId);
		}
		flash("browserId",""+browserId);
		return ok(views.html.Scripts.getScript.render(script, uploadFiles, hideAdvancedOptions));
	}
	
	public static class ScriptForm {
		
		public pipeline2.models.Script script;
		public Map<Long,Upload> uploads;
		public Map<String,List<String>> errors;
		
		public String guestEmail;
		
		//                                                          kind    position  part      name
		private static final Pattern PARAM_NAME = Pattern.compile("^([A-Za-z]+)(\\d*)([A-Za-z]*?)-(.*)$");
		private static final Pattern FILE_REFERENCE = Pattern.compile("^upload(\\d+)-file(\\d+)$");
		
		public ScriptForm(Long userId, Script script, Map<String, String[]> params) {
			this.script = script;
			
			// Get all referenced uploads from DB
			this.uploads = new HashMap<Long,Upload>();
			for (String uploadId : params.get("uploads")[0].split(",")) {
				if ("".equals(uploadId))
					continue;
				Upload upload = Upload.findById(Long.parseLong(uploadId));
				if (upload != null && upload.user.equals(userId))
					uploads.put(upload.id, upload);
			}
			
			// Parse all arguments
			for (String param : params.keySet()) {
				Matcher matcher = PARAM_NAME.matcher(param);
				if (!matcher.find()) {
					Logger.debug("Unable to parse argument parameter: "+param);
				} else {
					String kind = matcher.group(1);
					String name = matcher.group(4);
					Logger.debug(kind+": "+name);
					
					Argument argument = null;
					for (Argument arg : script.arguments) {
						Logger.debug(arg.name+" equals "+name+" ?");
						if (arg.name.equals(name)) {
							argument = arg;
							break;
						}
					}
					if (argument == null) {
						Logger.debug("'"+name+"' is not an argument for the script '"+script.id+"'; ignoring it");
						continue;
					}
					
					if ("anyFileURI".equals(argument.xsdType)) {
						if (argument.sequence) { // Multiple files
							ArgFiles argFiles = new ArgFiles(argument);
							for (int i = 0; i < params.get(param).length; i++) {
								matcher = FILE_REFERENCE.matcher(params.get(param)[i]);
								if (!matcher.find()) {
									Logger.debug("Unable to parse file reference: "+params.get(param)[i]);
								} else {
									Long uploadId = Long.parseLong(matcher.group(1));
									Integer fileNr = Integer.parseInt(matcher.group(2));
									argFiles.hrefs.add(uploads.get(uploadId).listFiles().get(fileNr).href);
								}
							}
							script.arguments.set(script.arguments.indexOf(argument), argFiles);

						} else { // Single file
							matcher = FILE_REFERENCE.matcher(params.get(param)[0]);
							if (!matcher.find()) {
								Logger.debug("Unable to parse file reference: "+params.get(param)[0]);
							} else {
								Long uploadId = Long.parseLong(matcher.group(1));
								Integer fileNr = Integer.parseInt(matcher.group(2));
								
								if (uploads.containsKey(uploadId)) {
									script.arguments.set(script.arguments.indexOf(argument), new ArgFile(argument, uploads.get(uploadId).listFiles().get(fileNr).href));
									
								} else {
									Logger.warn("No such upload: "+uploadId);
								}
								
							}
						}

					} else if ("boolean".equals(argument.xsdType)) {
						// Boolean
						script.arguments.set(script.arguments.indexOf(argument), new ArgBoolean(argument, new Boolean(params.get(param)[0])));

					} else if ("parameters".equals(argument.xsdType)) {
						// TODO: parameters are not implemented yet

					} else { // Unknown types are treated like strings

						if (argument.sequence) { // Multiple strings
							ArgStrings argStrings = new ArgStrings(argument);
							for (int i = 0; i < params.get(param).length; i++) {
								argStrings.add(params.get(param)[i]);
							}
							script.arguments.set(script.arguments.indexOf(argument), argStrings);

						} else { // Single string
							script.arguments.set(script.arguments.indexOf(argument), new ArgString(argument, params.get(param)[0]));
						}

					}
				}
			}
			
			if (userId < 0)
				this.guestEmail = params.get("guest-email")[0];
			
			this.errors = new HashMap<String, List<String>>();
		}
		
		public void validate() {
			if (guestEmail != null && !"".equals(guestEmail) && !guestEmail.matches("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,4}$")) {
				addError("guest-email", "Please enter a valid e-mail address.");
			}
			
			// TODO: validate arguments
		}
		
		public boolean hasErrors() {
			return errors.size() > 0;
		}
		
		public void addError(String field, String error) {
			if (!errors.containsKey(field))
				errors.put(field, new ArrayList<String>());
			errors.get(field).add(error);
		}
	}
	
}