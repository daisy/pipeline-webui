package controllers;

import play.mvc.*;
import play.data.*;

import models.*;

public class Login extends Controller {
  
    // -- Authentication
    
	public static class LoginForm {
        public String email;
        public String password;
        
        public String validate() {
            if (User.authenticateUnencrypted(email, password, session()) == null) {
                return "Invalid e-mail address or password";
            }
            return null;
        }
    }

    /**
     * Login page.
     */
    public static Result login() {
    	if (FirstUse.isFirstUse()) {
    		if ("server".equals(Application.deployment()) && User.find.where().eq("admin", true).findRowCount() > 0);
    			// Server mode and admin exists; require login
    		else
    			return redirect(routes.FirstUse.getFirstUse());
    	}
    	
    	if ("desktop".equals(Application.deployment())) {
			User.find.where().eq("admin", true).findUnique().login(session());
			return redirect(routes.FirstUse.welcome());
		}
    	
    	User.parseUserId(session());
    	User user = User.authenticate(request(), session());
    	User.flashBrowserId(user);
		return ok(views.html.Login.login.render(play.data.Form.form(LoginForm.class)));
    }
    
    /**
     * Handle login form submission.
     */
    public static Result authenticate() {
        Form<LoginForm> loginForm = play.data.Form.form(LoginForm.class).bindFromRequest();
        
    	User user = User.authenticateUnencrypted(loginForm.field("email").valueOr(""), loginForm.field("password").valueOr(""), session());
        if (loginForm.hasErrors()) {
        	User.flashBrowserId(user);
            return badRequest(views.html.Login.login.render(loginForm));
        } else {
        	user.login(Controller.session());
            return redirect(routes.Jobs.newJob());
        }
    }
    
    /**
     * Handle login form submission for guest logins.
     */
    public static Result authenticateGuest() {
    	if (!"true".equals(models.Setting.get("users.guest.allowGuests"))) {
    		User.flashBrowserId(null);
    		return badRequest(views.html.Login.login.render(play.data.Form.form(LoginForm.class)));
    	}
    	
    	User.loginAsGuest(Controller.session());
    	
        return redirect(routes.Jobs.newJob());
    }
    
    public static Result resetPassword() {
    	String email = request().queryString().containsKey("email") ? request().queryString().get("email")[0] : "";
    	User user = User.findByEmail(email);
    	
    	if ("".equals(email)) {
    		flash("error", "You must enter an e-mail address.");
    		User.flashBrowserId(user);
    		return badRequest(views.html.Login.login.render(play.data.Form.form(LoginForm.class)));
    		
    	} else if (user == null) {
    		flash("error", "There is no user using that e-mail address; did you type it correctly?");
    		User.flashBrowserId(user);
    		return badRequest(views.html.Login.login.render(play.data.Form.form(LoginForm.class)));
    		
    	} else {
    		user.makeNewActivationUid();
    		user.save(Application.datasource);
			String resetUrl = routes.Account.showResetPasswordForm(user.email, user.getActivationUid()).absoluteURL(request());
			String html = views.html.Account.emailResetPassword.render(resetUrl).body();
			String text = "Go to this link to change your password: "+resetUrl;
			if (Account.sendEmail("Reset your password", html, text, user.name, user.email))
				flash("success", "An e-mail has been sent to "+email+" with further instructions. Please check your e-mail.");
			else
				flash("error", "Was unable to send the e-mail. Please notify the owners of this website so they can fix their e-mail settings.");
			User.flashBrowserId(user);
    		return ok(views.html.Login.login.render(play.data.Form.form(LoginForm.class)));
    	}
    }

    /**
     * Logout and clean the session.
     */
    public static Result logout() {
    	if ("server".equals(Application.deployment())) {
	        session().clear();
	        flash("success", "You've been logged out");
	        
	        if ("true".equals(Setting.get("users.guest.automaticLogin")))
				User.loginAsGuest(session());
	        
	        return redirect(routes.Login.login());
    	} else {
    		return Application.error(FORBIDDEN, "You can't log out when running in desktop mode", null, null);
    	}
    }

}
