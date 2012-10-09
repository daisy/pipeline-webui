package controllers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Date;

import models.Setting;
import models.User;
import play.Configuration;
import play.Logger;
import play.mvc.*;

public class Application extends Controller {
	
	public static final String datasource = Configuration.root().getString("dp2.datasource");
	
	/** Last activity (job running / user requests page) */
	public static Date lastRequest = new Date();
	
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
	
	public static Result theme(String filename) {
		if ("".equals(themeName())) {
			return redirect(routes.Assets.at(filename));
			
		} else {
			String theme = Application.themeName();
			File file = new File("themes/"+theme+"/"+filename);
			if (file.exists()) {
				try {
					if (filename.endsWith("css"))
						response().setContentType("text/css");
					else if (filename.endsWith("png"))
						response().setContentType("image/png");
					else if (filename.endsWith("jpg") || filename.endsWith("jpeg"))
						response().setContentType("image/jpeg");
					else if (filename.endsWith("gif"))
						response().setContentType("image/gif");
					else if (filename.endsWith("js"))
						response().setContentType("application/javascript");
					
					return ok(new FileInputStream(file));
					
				} catch (FileNotFoundException e) {
					Logger.error("Could not open file input stream for '"+filename+"' in theme '"+theme+"'.", e);
					return redirect(routes.Assets.at(filename));
				}
			} else {
				return redirect(routes.Assets.at(filename));
			}
		}
	}
	
	public static Result error(int status, String name, String description, String message) {
		return status(status, views.html.error.render(status, name, description, message));
	}
	
	public static Result redirect(String path, String file) {
		return movedPermanently(path+file);
	}
	
	public static String themeName = null;
	public static String themeName() {
		if (themeName == null)
			themeName = Setting.get("branding.theme");
		return themeName;
	}
}
