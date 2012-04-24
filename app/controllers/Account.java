package controllers;

import models.User;
import play.Logger;
import play.data.Form;
import play.data.validation.ValidationError;
import play.mvc.*;

public class Account extends Controller {
	
	final static Form<User> editDetailsForm = form(User.class);
	
	public static Result overview() {
		if (FirstUse.isFirstUse())
    		return redirect(routes.FirstUse.getFirstUse());
		
		User user = User.authenticate(session("email"), session("password"));
		if (user == null)
			return redirect(routes.Login.login());
    	
		return ok(views.html.Account.overview.render(form(User.class)));
	}
	
	public static Result changeDetails() {
		if (FirstUse.isFirstUse())
    		return redirect(routes.FirstUse.getFirstUse());
		
		User user = User.authenticate(session("email"), session("password"));
		if (user == null)
			return redirect(routes.Login.login());
		
		Form<User> filledForm = editDetailsForm.bindFromRequest();
		
		boolean changedName = false;
		boolean changedEmail = false;
		boolean changedPassword = false;
		
		if (!user.name.equals(filledForm.field("name").valueOr(""))) {
			// Changed name
			changedName = true;
		}
		
		if (!user.email.equals(filledForm.field("email").valueOr(""))) {
			// Changed email
			changedEmail = true;
			
			if (User.findByEmail(filledForm.field("email").valueOr("")) != null)
	            filledForm.reject("email", "That e-mail address is already taken");
		}
    	
		if (!filledForm.field("newPassword").valueOr("").equals("")) {
			// Changed password
			changedPassword = true;
			
			if (!filledForm.field("newPassword").valueOr("").equals(filledForm.field("repeatPassword").valueOr(""))) {
				filledForm.reject("repeatPassword", "Passwords don't match.");
			
			} else if (filledForm.field("password").valueOr("").equals("")) {
				filledForm.reject("password", "You must enter your existing password, just so that we're extra sure that you are you.");
				
			} else {
				User oldUser = User.authenticateUnencrypted(user.email, filledForm.field("password").valueOr(""));
				if (oldUser == null)
					filledForm.reject("password", "The password you entered is wrong, please correct it and try again.");
			}
			
		} else if (filledForm.errors().containsKey("password")) {
			filledForm.errors().get("password").clear(); // No need to check the old password if the user isn't trying to set a new password
			Logger.debug("deleted all password error messages");
		}
		
		if (!changedName && !changedEmail && !changedPassword) {
			flash("success", "You did not submit any changes. No changes were made.");
			return redirect(routes.Account.overview());
			
		} else if (filledForm.hasErrors()) {
        	return badRequest(views.html.Account.overview.render(filledForm));
        	
        } else {
        	if (changedName)
        		user.name = filledForm.field("name").valueOr("");
        	if (changedEmail)
        		user.email = filledForm.field("email").valueOr("");
        	if (changedPassword)
        		user.setPassword(filledForm.field("newPassword").valueOr(""));
        	user.save();
        	session("name", user.name);
        	session("email", user.email);
        	session("password", user.password);
        	flash("success", "Your changes were saved successfully!");
        	return redirect(routes.Account.overview());
        }
	}
	
}
