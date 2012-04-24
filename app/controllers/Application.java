package controllers;

import models.User;
import play.mvc.*;

public class Application extends Controller {
	
	public static Result index() {
		if (FirstUse.isFirstUse())
    		return redirect(routes.FirstUse.getFirstUse());
		
		User user = User.authenticate(session("email"), session("password"));
		if (user == null)
			return redirect(routes.Login.login());
		
		return redirect(routes.Scripts.getScripts());
	}
	
	public static Result error(int status, String name, String description, String message) {
		return status(status, views.html.error.render(status, name, description, message));
	}
	
}
