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
            if(User.authenticateUnencrypted(email, password) == null) {
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
    		
    	} else if (User.authenticate(session("email"), session("password")) == null) {
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
        
        if(loginForm.hasErrors()) {
            return badRequest(views.html.Login.login.render(loginForm));
        } else {
        	session("name", user.name);
        	session("email", user.email);
        	session("password", user.password);
        	session("admin", user.admin+"");
            return redirect(routes.Scripts.getScripts());
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
