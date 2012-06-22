package controllers;

import java.util.Random;

import play.Logger;
import play.mvc.*;
import play.data.*;

import models.*;

public class Login extends Controller {
  
    // -- Authentication
    
	public static class LoginForm {
        public String email;
        public String password;
        
        public String validate() {
            if (User.authenticateUnencrypted(email, password) == null) {
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
    		return redirect(routes.FirstUse.getFirstUse());
    		
    	} else if (User.authenticate(session("userid"), session("email"), session("password")) == null) {
    		return ok(views.html.Login.login.render(form(LoginForm.class)));
    		
    	} else {
    		return redirect(routes.Application.index());
    	}
    }
    
    /**
     * Handle login form submission.
     */
    public static Result authenticate() {
        Form<LoginForm> loginForm = form(LoginForm.class).bindFromRequest();
        
    	User user = User.authenticateUnencrypted(loginForm.field("email").valueOr(""), loginForm.field("password").valueOr(""));
        if (loginForm.hasErrors()) {
            return badRequest(views.html.Login.login.render(loginForm));
        } else {
        	session("userid", user.id+"");
        	session("name", user.name);
        	session("email", user.email);
        	session("password", user.password);
        	session("admin", user.admin+"");
            return redirect(routes.Scripts.getScripts());
        }
    }
    
    private static Random randomGuestUserId = new Random();
    /**
     * Handle login form submission for guest logins.
     */
    public static Result authenticateGuest() {
    	if (!"true".equals(models.Setting.get("guest.allowGuests")))
    		return badRequest(views.html.Login.login.render(form(LoginForm.class)));
    	
    	session("userid", ""+(-1-randomGuestUserId.nextInt(2147483640)));
    	session("name", models.Setting.get("guest.name"));
    	session("email", "");
    	session("password", "");
    	session("admin", "false");
        return redirect(routes.Scripts.getScripts());
    }
    
    public static Result resetPassword() {
    	String email = request().queryString().containsKey("email") ? request().queryString().get("email")[0] : "";
    	User user = User.findByEmail(email);
    	
    	if ("".equals(email)) {
    		flash("error", "You must enter an e-mail address.");
    		return badRequest(views.html.Login.login.render(form(LoginForm.class)));
    		
    	} else if (user == null) {
    		flash("error", "There is no user using that e-mail address; did you type it correctly?");
    		return badRequest(views.html.Login.login.render(form(LoginForm.class)));
    		
    	} else {
    		user.makeNewActivationUid();
    		user.save();
			String resetUrl = routes.Account.showResetPasswordForm(user.email, user.getActivationUid()).absoluteURL(request());
			String html = views.html.Account.emailResetPassword.render(resetUrl).body();
			String text = "Go to this link to change your password: "+resetUrl;
			if (Account.sendEmail("Reset your password", html, text, user.name, user.email))
				flash("success", "An e-mail has been sent to "+email+" with further instructions. Please check your e-mail.");
			else
				flash("error", "Was unable to send the e-mail. Please notify the owners of this website so they can fix their e-mail settings.");
    		return ok(views.html.Login.login.render(form(LoginForm.class)));
    		
    	}
    }

    /**
     * Logout and clean the session.
     */
    public static Result logout() {
        session().clear();
        flash("success", "You've been logged out");
        return redirect(
            routes.Login.login()
        );
    }

}
