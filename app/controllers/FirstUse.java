package controllers;

import java.io.File;

import play.mvc.*;
import play.data.*;
import play.data.validation.Constraints.Required;
import models.*;

public class FirstUse extends Controller {
	
	final static Form<User> createAdminForm = form(User.class);
	final static Form<SetWSForm> setWSForm = form(SetWSForm.class);
	final static Form<SetUploadDirForm> setUploadDirForm = form(SetUploadDirForm.class);
	
	public static class SetWSForm {
        @Required
        public String endpoint;
        
        @Required
        public String authid;
        
        public String secret;
    }
	
	public static class SetUploadDirForm {
        @Required
        public String uploaddir;
    }
	
	/**
	 * GET /firstuse
	 * @return
	 */
	public static Result getFirstUse() {
		if (!isFirstUse()) {
			User user = User.authenticate(session("email"), session("password"));
			if (user == null || !user.admin) {
				return redirect(routes.Login.login());
				
			} else {
				return redirect(routes.FirstUse.getWelcome());
			}
		}
		
		return redirect(routes.FirstUse.getCreateAdmin());
	}
	
	/**
	 * GET /firstuse/createadmin
	 * @return
	 */
	public static Result getCreateAdmin() {
		if (!isFirstUse()) {
			User user = User.authenticate(session("email"), session("password"));
			if (user == null || !user.admin) {
				return redirect(routes.Login.login());
				
			} else {
				return redirect(routes.FirstUse.getWelcome());
			}
		}
		
		return ok(views.html.FirstUse.createAdmin.render(form(User.class)));
	}
	
	/**
	 * POST /firstuse/createadmin
	 * @return
	 */
	public static Result postCreateAdmin() {
		if (!isFirstUse()) {
			User user = User.authenticate(session("email"), session("password"));
			if (user == null || !user.admin) {
				return redirect(routes.Login.login());
				
			} else {
				return redirect(routes.FirstUse.getWelcome());
			}
		}
		
		Form<User> filledForm = createAdminForm.bindFromRequest();
        
        if (User.findByEmail(filledForm.field("email").valueOr("")) != null)
            filledForm.reject("email", "That e-mail address is already taken");
    	
    	if (!filledForm.field("password").valueOr("").equals("") && !filledForm.field("password").valueOr("").equals(filledForm.field("repeatPassword").value()))
    		filledForm.reject("repeatPassword", "Password doesn't match.");
        
        if (filledForm.hasErrors()) {
        	return badRequest(views.html.FirstUse.createAdmin.render(filledForm));
        	
        } else {
        	User admin = new User(filledForm.field("email").valueOr(""), "Administrator", filledForm.field("password").valueOr(""), true);
        	admin.save();
        	session("name", admin.name);
        	session("email", admin.email);
        	session("password", admin.password);
        	session("admin", admin.admin+"");
        	session("setws", "true");
        	return redirect(routes.FirstUse.getSetWS());
        }
		
	}
	
	/**
	 * GET /firstuse/setws
	 * @return
	 */
	public static Result getSetWS() {
		if (isFirstUse())
			return redirect(routes.FirstUse.getCreateAdmin());
		
		User user = User.authenticate(session("email"), session("password"));
		if (user == null || !user.admin)
			return redirect(routes.Login.login());
		
		return ok(views.html.FirstUse.setWS.render(form(SetWSForm.class)));
	}
	
	/**
	 * POST /firstuse/setws
	 * @return
	 */
	public static Result postSetWS() {
		User user = User.authenticate(session("email"), session("password"));
		if (user == null || !user.admin)
			return redirect(routes.Login.login());
		
		Form<SetWSForm> filledForm = setWSForm.bindFromRequest();
    	
    	if (filledForm.field("endpoint").valueOr("").equals(""))
    		filledForm.reject("endpoint", "Invalid endpoint URL.");
        
        if(filledForm.hasErrors()) {
        	return badRequest(views.html.FirstUse.setWS.render(filledForm));
        	
        } else {
        	Setting.set("dp2ws.endpoint", filledForm.field("endpoint").valueOr(""));
        	Setting.set("dp2ws.authid", filledForm.field("authid").valueOr(""));
        	Setting.set("dp2ws.secret", filledForm.field("secret").valueOr(""));
        	return redirect(routes.FirstUse.getSetUploadDir());
        }
	}
	
	/**
	 * GET /firstuse/setuploaddir
	 * @return
	 */
	public static Result getSetUploadDir() {
		if (isFirstUse())
			return redirect(routes.FirstUse.getCreateAdmin());
		
		User user = User.authenticate(session("email"), session("password"));
		if (user == null || !user.admin)
			return redirect(routes.Login.login());
			
		return ok(views.html.FirstUse.setUploadDir.render(form(SetUploadDirForm.class)));
	}
	
	/**
	 * POST /firstuse/setuploaddir
	 * @return
	 */
	public static Result postSetUploadDir() {
		User user = User.authenticate(session("email"), session("password"));
		if (user == null || !user.admin) {
			return redirect(routes.Login.login());
			
		}
		
		Form<SetUploadDirForm> filledForm = setUploadDirForm.bindFromRequest();
    	
    	if (filledForm.field("uploaddir").valueOr("").equals(""))
    		filledForm.reject("uploaddir", "Invalid upload directory path.");
    	
    	String uploadPath = filledForm.field("uploaddir").valueOr("");
    	if (!uploadPath.endsWith(System.getProperty("file.separator")))
    		uploadPath += System.getProperty("file.separator");
    	File dir = new File(uploadPath);
    	if (!dir.exists())
    		filledForm.reject("uploaddir", "The directory does not exist.");
    	if (!dir.isDirectory())
    		filledForm.reject("uploaddir", "The path does not point to a directory.");
        
        if(filledForm.hasErrors()) {
        	session("setws", "true");
        	return badRequest(views.html.FirstUse.setUploadDir.render(filledForm));
        	
        } else {
        	Setting.set("uploads", uploadPath);
        	return redirect(routes.FirstUse.getWelcome());
        }
	}
	
	/**
	 * GET /firstuse/welcome
	 * @return
	 */
	public static Result getWelcome() {
		if (!isFirstUse()) {
			User user = User.authenticate(session("email"), session("password"));
			if (user == null || !user.admin) {
				return redirect(routes.Login.login());
				
			} else {
				return ok(views.html.FirstUse.welcome.render());
			}
		}
		
		return redirect(routes.FirstUse.getCreateAdmin());
	}
	
	/**
	 * Returns true if there are no registered users (i.e. this is the first time that the Web UI is used).
	 * @return
	 */
	public static boolean isFirstUse() {
		return User.findAll().size() == 0;
	}

}
