package controllers;

import models.User;
import play.mvc.*;

public class Account extends Controller {
	
	public static Result overview() {
		if (FirstUse.isFirstUse())
    		return redirect(routes.FirstUse.getFirstUse());
		
		User user = User.authenticate(session("email"), session("password"));
		if (user == null)
			return redirect(routes.Login.login());
    	
		return ok(views.html.Account.overview.render());
	}
	
}
