package controllers;

import java.io.File;

import models.Setting;
import models.User;
import play.Configuration;
import play.mvc.*;

public class Application extends Controller {
	
	public static final String datasource = Configuration.root().getString("dp2.datasource");
	
	public static Result index() {
		if (FirstUse.isFirstUse())
    		return redirect(routes.FirstUse.getFirstUse());
		
		User user = User.authenticate(request(), session());
		if (user == null)
			return redirect(routes.Login.login());
		
		return redirect(routes.Scripts.getScripts());
	}
	
	public static Result about() {
		if (FirstUse.isFirstUse())
    		return redirect(routes.FirstUse.getFirstUse());
		
		return ok(views.html.about.render());
	}
	
	public static Result theme(String file) {
		if (file.endsWith(".css")) {
			String theme = Setting.get("branding.theme");
			if (new File("public/stylesheets/themes/"+theme+file).exists())
				return redirect(routes.Assets.at("stylesheets/themes/"+theme+file));
			return redirect(routes.Assets.at("stylesheets/"+file));
		}
		return redirect(routes.Assets.at(file));
	}
	
	public static Result themeImage(String file) {
		String theme = Setting.get("branding.theme");
		if (new File("public/images/themes/"+theme+file).exists())
			return redirect(routes.Assets.at("images/themes/"+theme+file));
		return redirect(routes.Assets.at("images/"+file));
	}
	
	public static Result error(int status, String name, String description, String message) {
		return status(status, views.html.error.render(status, name, description, message));
	}
	
	public static Result redirect(String path, String file) {
		return movedPermanently(path+file);
	}

}
