package controllers;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import models.Job;
import models.Setting;
import models.Template;
import models.User;
import models.UserSetting;
import play.Logger;
import play.mvc.Controller;
import play.mvc.Result;

import com.avaje.ebean.ExpressionList;

public class Templates extends Controller {

	public static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	
	public static ExpressionList<Job> findWhere() {
		return Job.find.where().eq("status", "TEMPLATE");
	}
	
	public static Result getTemplates() {
		if (FirstUse.isFirstUse())
			return redirect(routes.FirstUse.getFirstUse());
		
		User user = User.authenticate(request(), session());
		if (user == null || (user.id < 0 && !"true".equals(Setting.get("users.guest.shareJobs"))))
			return redirect(routes.Login.login());
		
		if (user.admin)
			flash("showOwner", "true");
		flash("userid", user.id+"");
		
		User.flashBrowserId(user);
		return ok(views.html.Templates.getTemplates.render());
	}
	
	public static Result getTemplatesJson() {
		if (FirstUse.isFirstUse())
			return unauthorized("unauthorized");
		
		User user = User.authenticate(request(), session());
		if (user == null || (user.id < 0 && !"true".equals(Setting.get("users.guest.shareJobs"))))
			return unauthorized("unauthorized");
		
		List<Template> templates = Template.list(user, false);
		List<Object> templatesJsonFriendly = new ArrayList<Object>();
		for (Template template : templates) {
			templatesJsonFriendly.add(template.asJsonifyableObject(user, false));
		}
		
		return ok(play.libs.Json.toJson(templatesJsonFriendly));
	}
	
	public static Result getTemplateJson(String ownerIdOrSharedDirName, String templateName) {
		if (FirstUse.isFirstUse())
			return unauthorized("unauthorized");
		
		User user = User.authenticate(request(), session());
		if (user == null)
			return unauthorized("unauthorized");
		
		Template template;
		if (ownerIdOrSharedDirName.matches("^\\d+$")) {
			template = Template.get(user, Long.parseLong(ownerIdOrSharedDirName), templateName);
			
		} else {
			template = Template.get(user, ownerIdOrSharedDirName, templateName);
		}		

		List<Object> templateJsonFriendly = new ArrayList<Object>();
		templateJsonFriendly.add(template.asJsonifyableObject(user, true));
		
		return ok(play.libs.Json.toJson(templateJsonFriendly));
	}

	// route is same as Jobs.postJob - user auth etc. happens there
	public static Result postTemplate(User user, Job job, org.daisy.pipeline.client.models.Job clientlibJob) {
		
		Template template = Template.create(clientlibJob, user);
		
		if ("NEW".equals(job.getStatus())) {
			job.delete();
		}
		
		String highlightTemplateName = template.name == null ? "" : ""+template.name;
		String highlightTemplateOwner = template.ownerId == null ? "" : ""+template.ownerId;
	    try {
	    	if (highlightTemplateName != null) {
	    		highlightTemplateName = URLEncoder.encode(highlightTemplateName, "UTF-8")
							                      .replaceAll("\\+", "%20")
							                      .replaceAll("\\%21", "!")
							                      .replaceAll("\\%27", "'")
							                      .replaceAll("\\%28", "(")
							                      .replaceAll("\\%29", ")")
							                      .replaceAll("\\%7E", "~");
	    	}
	    } catch (UnsupportedEncodingException e) { /* This exception should never occur. */ }

		Logger.debug("return redirect(controllers.routes.Templates.getTemplates());");
		Logger.debug("highlightTemplate:"+template.name);
		Logger.debug("highlightTemplateOwner:"+template.ownerId);
		flash("highlightTemplateName", highlightTemplateName);
		flash("highlightTemplateOwner", ""+highlightTemplateOwner);
		return redirect(controllers.routes.Templates.getTemplates());
		
	}
	
	public static Result deleteTemplate(String ownerIdOrSharedDirName, String templateName) {
		if (FirstUse.isFirstUse())
			return unauthorized("unauthorized");
		
		User user = User.authenticate(request(), session());
		if (user == null)
			return unauthorized("unauthorized");
		
		Template template;
		if (ownerIdOrSharedDirName.matches("^\\d+$")) {
			template = Template.get(user, Long.parseLong(ownerIdOrSharedDirName), templateName);
			
		} else {
			return play.mvc.Results.forbidden("You cannot delete shared templates");
		}
		
		template.delete();
		
		return ok();
	}
	
	public static Result downloadTemplate(String ownerIdOrSharedDirName, String templateName) {
		if (FirstUse.isFirstUse())
			return unauthorized("unauthorized");
		
		User user = User.authenticate(request(), session());
		if (user == null)
			return unauthorized("unauthorized");
		
		Template template;
		if (ownerIdOrSharedDirName.matches("^\\d+$")) {
			template = Template.get(user, Long.parseLong(ownerIdOrSharedDirName), templateName);
			
		} else {
			template = Template.get(user, ownerIdOrSharedDirName, templateName);
		}
		
		File zip = template.asZip();
		
		if (zip != null && zip.exists()) {
			response().setHeader("Content-Disposition", "attachment; filename=\""+zip.getName()+"\"");
			response().setContentType("application/zip");
			long size = zip.length();
			if (size > 0) {
				response().setHeader("Content-Length", size+"");
			}
			
		} else {
			return internalServerError("Was unable to create zip for template.");
		}
		
		return ok(zip);
	}
	
	public static Result renameTemplate(Long ownerId, String templateName) {
		if (FirstUse.isFirstUse())
			return unauthorized("unauthorized");
		
		User user = User.authenticate(request(), session());
		if (user == null)
			return unauthorized("unauthorized");
		
		Map<String, String[]> params = request().body().asFormUrlEncoded();
		if (params == null) {
			Logger.error("Could not read form data: "+request().body().asText());
			return internalServerError("Could not read form data");
		}
		
		String newName = params.get("template-name")[0];
		if (newName == null || "".equals(newName)) {
			return badRequest("New name cannot be empty.");
		}
		
		if (newName.equals(templateName)) {
			return ok();
			
		}
		
		Template template = Template.get(user, ownerId, templateName);
		if (template == null) {
			User owner = User.findById(ownerId);
			String username = owner == null ? ownerId+"" : owner.name;
			return notFound("Template '"+templateName+"' (owned by '"+username+"') was not found.");
		}
		
		String error = template.rename(newName);
		if (error == null) {
			return ok();
		} else {
			return internalServerError(error);
		}
	}
}
