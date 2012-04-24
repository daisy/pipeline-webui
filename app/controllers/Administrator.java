package controllers;

import models.User;
import play.mvc.*;

public class Administrator extends Controller {
	
	public static Result settings() {
		if (FirstUse.isFirstUse())
    		return redirect(routes.FirstUse.getFirstUse());
		
		User user = User.authenticate(session("email"), session("password"));
		if (user == null || !user.admin)
			return redirect(routes.Login.login());
    	
		return ok(views.html.Administrator.settings.render());
	}
	
}
