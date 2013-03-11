package controllers;

import play.mvc.*;
import play.data.*;
import utils.Pipeline2Engine;
import models.*;

/**
 * Helps with configuring the Web UI for the first time.
 * 
 * Configure database -> configure administrative account -> set webservice endpoint -> set upload directory -> welcome page!
 * 
 * @author jostein
 */
public class FirstUse extends Controller {
	
	/**
	 * GET /firstuse
	 * @return
	 */
	public static Result getFirstUse() {
		
		if (isFirstUse()) {
			if (!"desktop".equals(Application.deployment()) && !"server".equals(Application.deployment()))
				// Application mode is not set
				
				return ok(views.html.FirstUse.setDeployment.render(form(Administrator.SetDeploymentForm.class)));
			
			else if ("server".equals(Application.deployment())) {
				// Server mode
				
				if (User.find.where().eq("admin", true).findRowCount() == 0) {
					return ok(views.html.FirstUse.createAdmin.render(form(Administrator.CreateAdminForm.class)));
				}
				
				if (Setting.get("dp2ws.endpoint") == null) {
					return ok(views.html.FirstUse.setWS.render(form(Administrator.SetWSForm.class)));
				}
				
				if (Setting.get("uploads") == null) {
					return ok(views.html.FirstUse.setStorageDirs.render(form(Administrator.SetUploadDirForm.class)));
				}
				
			} else if ("desktop".equals(Application.deployment())) {
				// Desktop mode
				
				User admin = User.find.where().eq("email", "email@example.com").findUnique();
				if (admin == null) {
					admin = new User("email@example.com", "Administrator", "password", true);
				} else {
					admin.admin = true;
				}
				admin.save(Application.datasource);
				admin.login(session());
			}
			
		}
		
		User user = User.authenticate(request(), session());
		if (user == null || !user.admin) {
			return redirect(routes.Login.login());
		}
		
		if ("desktop".equals(Application.deployment()) && Pipeline2Engine.getState() != Pipeline2Engine.State.RUNNING) {
			user.flashBrowserId();
			return ok(views.html.FirstUse.configureDP2.render(Pipeline2Engine.errorMessages));
		}
		
		return redirect(routes.FirstUse.welcome());
	}
	
	public static Result welcome() {
		if (FirstUse.isFirstUse())
			return redirect(routes.FirstUse.getFirstUse());

		User user = User.authenticate(request(), session());
		if (user == null)
			return redirect(routes.Login.login());

		user.flashBrowserId();
		return ok(views.html.FirstUse.welcome.render());
	}
	
	public static Result postFirstUse() {
		String formName = request().body().asFormUrlEncoded().containsKey("formName") ? request().body().asFormUrlEncoded().get("formName")[0] : "";
		
		if ("setDeployment".equals(formName)) {
			if (!isFirstUse())
				return redirect(routes.FirstUse.getFirstUse());
			
			Form<Administrator.SetDeploymentForm> filledForm = form(Administrator.SetDeploymentForm.class).bindFromRequest();
			Administrator.SetDeploymentForm.validate(filledForm);
			
			if (filledForm.hasErrors()) {
				return badRequest(views.html.FirstUse.setDeployment.render(filledForm));
			
			} else {
				String deployment = filledForm.field("deployment").valueOr("unknown");
				Setting.set("deployment", deployment);
				
				if ("desktop".equals(deployment)) {
					User admin = new User("email@example.com", "Administrator", "password", true);
					admin.save(Application.datasource);
					admin.login(session());
					
					// Set some default configuration options
					Setting.set("users.guest.name", "Guest");
					Setting.set("users.guest.allowGuests", "true");
					Setting.set("users.guest.showGuestName", "false");
					Setting.set("users.guest.showEmailBox", "false");
					Setting.set("users.guest.shareJobs", "true");
					Setting.set("users.guest.automaticLogin", "true");
					Setting.set("mail.enable", "false");
					Setting.set("uploads", System.getProperty("user.dir") + System.getProperty("file.separator") + "uploads" + System.getProperty("file.separator"));
				}
				
				return redirect(routes.FirstUse.getFirstUse());
			}
		}
		
		if ("createAdmin".equals(formName)) {
			if (!isFirstUse())
				return redirect(routes.FirstUse.getFirstUse());
			
			Form<Administrator.CreateAdminForm> filledForm = form(Administrator.CreateAdminForm.class).bindFromRequest();
			Administrator.CreateAdminForm.validate(filledForm);
			
			if (filledForm.hasErrors()) {
				return badRequest(views.html.FirstUse.createAdmin.render(filledForm));
			
			} else {
				User admin = new User(filledForm.field("email").valueOr(""), "Administrator", filledForm.field("password").valueOr(""), true);
				admin.save(Application.datasource);
				admin.login(session());
				
				// Set some default configuration options
				Setting.set("users.guest.name", "Guest");
				Setting.set("users.guest.allowGuests", "false");
				Setting.set("users.guest.showGuestName", "true");
				Setting.set("users.guest.showEmailBox", "true");
				Setting.set("users.guest.shareJobs", "false");
				Setting.set("users.guest.automaticLogin", "false");
				Setting.set("mail.enable", "false");
				
				return redirect(routes.FirstUse.getFirstUse());
			}
		}
		
		User user = User.authenticate(request(), session());
		if (user == null || !user.admin) {
			return redirect(routes.Login.login());
		}
		
		if ("setWS".equals(formName)) {
			Form<Administrator.SetWSForm> filledForm = form(Administrator.SetWSForm.class).bindFromRequest();
			Administrator.SetWSForm.validate(filledForm);
			
			if (filledForm.hasErrors()) {
	        	return badRequest(views.html.FirstUse.setWS.render(filledForm));
	        	
	        } else {
	        	Administrator.SetWSForm.save(filledForm);
	        	return redirect(routes.FirstUse.getFirstUse());
	        }
		}
		
		if ("setStorageDirs".equals(formName)) {
			Form<Administrator.SetUploadDirForm> filledForm = form(Administrator.SetUploadDirForm.class).bindFromRequest();
			Administrator.SetUploadDirForm.validate(filledForm);
			
			if(filledForm.hasErrors()) {
	        	return badRequest(views.html.FirstUse.setStorageDirs.render(filledForm));
	        	
	        } else {
	        	Administrator.SetUploadDirForm.save(filledForm);
	        	return redirect(routes.FirstUse.getFirstUse());
	        }
		}
		
		return getFirstUse();
	}
	
	/**
	 * Returns true if this is the first time that the Web UI are used (i.e. there are no registered users).
	 * @return
	 */
	public static boolean isFirstUse() {
		return User.findAll().size() == 0 || Setting.get("dp2ws.endpoint") == null || Setting.get("uploads") == null || "desktop".equals(Application.deployment()) && (Pipeline2Engine.cwd == null || !Pipeline2Engine.State.RUNNING.equals(Pipeline2Engine.getState()));
	}
	
	public static void configureDesktopDefaults() {
		Setting.set("uploads", System.getProperty("user.dir") + System.getProperty("file.separator") + "uploads" + System.getProperty("file.separator"));
		Setting.set("dp2ws.endpoint", controllers.Application.DEFAULT_DP2_ENDPOINT_LOCAL);
		Setting.set("dp2ws.authid", "");
		Setting.set("dp2ws.secret", "");
		Setting.set("dp2ws.tempdir", System.getProperty("user.dir") + controllers.Application.SLASH + "local.temp" + controllers.Application.SLASH);
		Setting.set("dp2ws.resultdir", System.getProperty("user.dir") + controllers.Application.SLASH + "local.results" + controllers.Application.SLASH);
	}
}
