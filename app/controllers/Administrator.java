package controllers;

import java.util.List;

import models.User;
import play.Logger;
import play.data.Form;
import play.data.validation.Constraints.Required;
import play.mvc.*;

public class Administrator extends Controller {
	
	final static Form<User> editUserForm = form(User.class);
	
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
		
		Form<User> filledForm = editUserForm.bindFromRequest();
		
		flash("adminsettings.userview", filledForm.field("userid").valueOr(""));
		
		boolean changedName = false;
		boolean changedEmail = false;
		boolean changedAdmin = false;
		
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
		
		if (!(user.admin+"").equals(filledForm.field("admin").valueOr(""))) {
			// Changed admin
			changedAdmin = true;
			
			String admin = filledForm.field("admin").valueOr("");
			if (!admin.equals("true") && !admin.equals("false")) {
				filledForm.reject("admin", "The user must either *be* an admin, or *not be* an admin");
			}
			
			if (filledForm.field("userid").valueOr("").equals(user.id+"")) {
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
        	User otherUser = User.findById(Long.parseLong(filledForm.field("userid").valueOr("")));
        	if (changedName)
        		otherUser.name = filledForm.field("name").valueOr("");
        	if (changedEmail)
        		otherUser.email = filledForm.field("email").valueOr("");
        	if (changedAdmin)
        		otherUser.admin = filledForm.field("admin").valueOr("").equals("true");
        	otherUser.save();
        	if (otherUser.id == user.id) {
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
		
		Form<User> filledForm = editUserForm.bindFromRequest();
		
		Logger.info("TODO: Not implemented: resetPassword("+userId+")");
		
		return TODO;
	}
	
	public static Result deleteUser(Long userId) {
		return TODO;
	}
	
}
