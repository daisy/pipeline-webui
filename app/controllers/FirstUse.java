package controllers;

import play.mvc.*;
import play.data.*;
import models.*;

/**
 * Helps with configuring the Web UI for the first time.
 * 
 * Configure database -> create admin account -> set webservice endpoint -> set upload directory -> welcome page!
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
			return ok(views.html.FirstUse.createAdmin.render(form(Administrator.CreateAdminForm.class)));
		}
		
		User user = User.authenticate(request(), session());
		if (user == null || !user.admin) {
			return redirect(routes.Login.login());
		}
		
		if (Setting.get("dp2ws.endpoint") == null) {
			return ok(views.html.FirstUse.setWS.render(form(Administrator.SetWSForm.class)));
		}
		
		if (Setting.get("uploads") == null) {
			return ok(views.html.FirstUse.setUploadDir.render(form(Administrator.SetUploadDirForm.class)));
		}
		
		return ok(views.html.FirstUse.welcome.render());
	}
	
	public static Result postFirstUse() {
		String formName = request().body().asFormUrlEncoded().containsKey("formName") ? request().body().asFormUrlEncoded().get("formName")[0] : "";
		
		if ("createAdmin".equals(formName)) {
			if (!isFirstUse())
				return redirect(routes.FirstUse.getFirstUse());
			
			Form<Administrator.CreateAdminForm> filledForm = form(Administrator.CreateAdminForm.class).bindFromRequest();
			Administrator.CreateAdminForm.validate(filledForm);
			
			if (filledForm.hasErrors()) {
				return badRequest(views.html.FirstUse.createAdmin.render(filledForm));
			
			} else {
				User admin = new User(filledForm.field("email").valueOr(""), "Administrator", filledForm.field("password").valueOr(""), true);
				admin.save();
				admin.login(session());
				
				// Set some default configuration options
				Setting.set("users.guest.name", "Guest");
				Setting.set("users.guest.allowGuests", "false");
				Setting.set("users.guest.showGuestName", "true");
				Setting.set("users.guest.showEmailBox", "true");
				Setting.set("users.guest.shareJobs", "false");
				Setting.set("users.guest.automaticLogin", "false");
				Setting.set("dp2ws.sameFilesystem", "false");
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
		
		if ("setUploadDir".equals(formName)) {
			Form<Administrator.SetUploadDirForm> filledForm = form(Administrator.SetUploadDirForm.class).bindFromRequest();
			Administrator.SetUploadDirForm.validate(filledForm);
			
			if(filledForm.hasErrors()) {
	        	return badRequest(views.html.FirstUse.setUploadDir.render(filledForm));
	        	
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
		return User.findAll().size() == 0;
	}

}
