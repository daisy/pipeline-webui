package controllers;

import java.util.List;

import models.User;
import play.Logger;
import play.data.Form;
import play.data.validation.Constraints.Required;
import play.data.validation.ValidationError;
import play.mvc.*;

public class Administrator extends Controller {
	
	final static Form<User> userForm = form(User.class);
	
	public static Result getSettings() {
		if (FirstUse.isFirstUse())
    		return redirect(routes.FirstUse.getFirstUse());
		
		User user = User.authenticate(session("email"), session("password"));
		if (user == null || !user.admin)
			return redirect(routes.Login.login());
    	
		List<User> users = User.find.orderBy("admin, name, email").findList();
		
		String tab = flash("adminsettings.userview");
		if (tab == null || "".equals(tab))
			tab = "global";
		
		return ok(views.html.Administrator.settings.render(form(User.class), users, tab));
	}
	
	public static Result updateUser() {
		if (FirstUse.isFirstUse())
    		return redirect(routes.FirstUse.getFirstUse());
		
		User user = User.authenticate(session("email"), session("password"));
		if (user == null)
			return redirect(routes.Login.login());
		
		Form<User> filledForm = userForm.bindFromRequest();
		User updateUser = User.findById(Long.parseLong(filledForm.field("userid").valueOr("")));
		if (updateUser == null) {
			flash("success", "Hmm, that's weird. The user was not found; nothing was changed...");
			return redirect(routes.Administrator.getSettings());
		}
		flash("adminsettings.userview", updateUser.id+"");
		
		boolean changedName = false;
		boolean changedEmail = false;
		boolean changedAdmin = false;
		
		if (!updateUser.name.equals(filledForm.field("name").valueOr(""))) {
			// Changed name
			changedName = true;
		}
		
		if (!updateUser.email.equals(filledForm.field("email").valueOr(""))) {
			// Changed email
			changedEmail = true;
			
			if (User.findByEmail(filledForm.field("email").valueOr("")) != null)
	            filledForm.reject("email", "That e-mail address is already taken");
		}
		
		if (!(updateUser.admin+"").equals(filledForm.field("admin").valueOr(""))) {
			// Changed admin
			changedAdmin = true;
			
			String admin = filledForm.field("admin").valueOr("");
			if (!admin.equals("true") && !admin.equals("false")) {
				filledForm.reject("admin", "The user must either *be* an admin, or *not be* an admin");
			}
			
			if (filledForm.field("userid").valueOr("").equals(updateUser.id+"")) {
				filledForm.reject("admin", "Only other admins can demote you to a normal user, you cannot do it yourself");
			}
		}
    	
		if (!changedName && !changedEmail && !changedAdmin) {
			flash("success", "You did not submit any changes. No changes were made.");
			return redirect(routes.Administrator.getSettings());
			
		} else if (filledForm.hasErrors()) {
        	return badRequest(views.html.Administrator.settings.render(filledForm,
        																	User.find.orderBy("admin, name, email").findList(),
        																	flash("adminsettings.userview")));
        	
        } else {
        	if (changedName)
        		updateUser.name = filledForm.field("name").valueOr("");
        	if (changedEmail)
        		updateUser.email = filledForm.field("email").valueOr("");
        	if (changedAdmin)
        		updateUser.admin = filledForm.field("admin").valueOr("").equals("true");
        	updateUser.save();
        	if (updateUser.id == user.id) {
	        	session("name", user.name);
	        	session("email", user.email);
        	}
        	flash("success", "Your changes were saved successfully!");
        	return redirect(routes.Administrator.getSettings());
        }
	}
	
	public static Result resetPassword(Long userId) {
		if (FirstUse.isFirstUse())
    		return redirect(routes.FirstUse.getFirstUse());
		
		User user = User.authenticate(session("email"), session("password"));
		if (user == null)
			return redirect(routes.Login.login());
		
		flash("adminsettings.userview", userId+"");
		
		User resetUser = User.findById(userId);
		
		if (resetUser.active) {
			// TODO: Send a mail to the user with a link to a "new password" page
			
//			Email email = new SimpleEmail();
			
			flash("success", "A password reset link was sent to "+resetUser.name);
		} else {
			// TODO: Send a mail to the user with a link to a "your first password" page
			flash("success", "An account activation link was sent to "+resetUser.name);
		}
		
		return redirect(routes.Administrator.getSettings());
	}
	
	public static Result deleteUser(Long userId) {
		if (FirstUse.isFirstUse())
    		return redirect(routes.FirstUse.getFirstUse());
		
		User user = User.authenticate(session("email"), session("password"));
		if (user == null)
			return redirect(routes.Login.login());
		
		User deleteUser = User.findById(userId);
		flash("adminsettings.userview", deleteUser.id+"");
		
		if (deleteUser == null) {
			flash("success", "Hmm, that's weird. The user was not found; nothing was changed...");
			return redirect(routes.Administrator.getSettings());
		}
		
		if ((deleteUser.id+"").equals(user.id+"")) {
			flash("error", "Only other admins can delete you, you cannot do it yourself");
			return redirect(routes.Administrator.getSettings());
		}
		
		deleteUser.delete();
		flash("adminsettings.userview", "global");
		flash("success", deleteUser.name+" was deleted");
		return redirect(routes.Administrator.getSettings());
	}
	
	public static Result createUser() {
		if (FirstUse.isFirstUse())
    		return redirect(routes.FirstUse.getFirstUse());
		
		User user = User.authenticate(session("email"), session("password"));
		if (user == null)
			return redirect(routes.Login.login());
		
		Form<User> filledForm = userForm.bindFromRequest();
        
		if (User.findByEmail(filledForm.field("email").valueOr("")) != null)
            filledForm.reject("email", "That e-mail address is already taken");
		
        if (filledForm.errors().containsKey("name") || filledForm.errors().containsKey("email")) {
        	return badRequest(views.html.Administrator.settings.render(filledForm,
																			User.find.orderBy("admin, name, email").findList(),
																			"adduser"));
        	
        } else {
        	User newUser = new User(filledForm.field("email").valueOr(""), filledForm.field("name").valueOr(""), "", filledForm.field("admin").valueOr("").equals("true"));
        	//TODO: sendUserCreatedPleaseSetYourPasswordMail(newUser)
        	newUser.save();
        	flash("adminsettings.userview", newUser.id+"");
        	flash("success", "User "+newUser.name+" created successfully!");
        	return redirect(routes.Administrator.getSettings());
        }
		
	}
	
}
