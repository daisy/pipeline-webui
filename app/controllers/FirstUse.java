package controllers;

import java.util.Random;

import controllers.Administrator.SetLocalDP2DirForm;
import play.Logger;
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
	
	private static String deployment = null;
	
	/**
	 * GET /firstuse
	 * @return
	 */
	public static Result getFirstUse() {
		
		if (isFirstUse()) {
			session("userid", null);
			if (!"desktop".equals(deployment()) && !"server".equals(deployment()))
				return ok(views.html.FirstUse.setDeployment.render(form(Administrator.SetDeploymentForm.class)));
			else if ("server".equals(deployment())) {
				return ok(views.html.FirstUse.createAdmin.render(form(Administrator.CreateAdminForm.class)));
			}
		}
		
		User user = User.authenticate(request(), session());
		if (user == null || !user.admin) {
			return redirect(routes.Login.login());
		}
		
		if ("desktop".equals(deployment()) && (Setting.get("dp2ws.endpoint") == null || "".equals(Setting.get("dp2ws.endpoint")))) {
			Long browserId = new Random().nextLong();
			NotificationConnection.createBrowserIfAbsent(user.id, browserId);
			Logger.debug("Browser: user #"+user.id+" opened browser window #"+browserId);
			flash("browserId",""+browserId);
			SetLocalDP2DirForm.startDP2Locator(user.id, browserId);
			return ok(views.html.FirstUse.setLocalDP2Dir.render(form(SetLocalDP2DirForm.class)));
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
					Setting.set("dp2ws.sameFilesystem", "true");
					Setting.set("mail.enable", "false");
					Setting.set("uploads", System.getProperty("user.dir") + System.getProperty("file.separator") + "uploads" + System.getProperty("file.separator"));
				}
				
				return redirect(routes.FirstUse.getFirstUse());
			}
		}
		
		if ("setLocalDP2Dir".equals(formName)) {
			Form<Administrator.SetLocalDP2DirForm> filledForm = form(Administrator.SetLocalDP2DirForm.class).bindFromRequest();
			Administrator.SetLocalDP2DirForm.validate(filledForm);
			
			if (filledForm.hasErrors()) {
	        	return badRequest(views.html.FirstUse.setLocalDP2Dir.render(filledForm));
	        	
	        } else {
	        	// Set some more default configuration options
//				Setting.set("dp2ws.endpoint", "http://localhost:9000/");
//				Setting.set("dp2ws.authid", "");
//				Setting.set("dp2ws.secret", "");
//	        	Setting.set("dp2ws.tempDir", System.getProperty("user.dir") + System.getProperty("file.separator") + "local.temp" + System.getProperty("file.separator"));
//	        	Setting.set("dp2ws.resultDir", System.getProperty("user.dir") + System.getProperty("file.separator") + "local.results" + System.getProperty("file.separator"));
	        	Administrator.SetLocalDP2DirForm.save(filledForm);
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
		return User.findAll().size() == 0 || "desktop".equals(deployment()) && Setting.get("dp2ws.endpoint") == null;
	}
	
	/**
	 * Returns a buffered value of the deployment type instead of having to check the DB each time using Setting.get("deployment").
	 * @return
	 */
	public static String deployment() {
		return deployment != null ? deployment : Setting.get("deployment");
	}

}
