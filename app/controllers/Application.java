package controllers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

import models.Setting;
import models.User;
import play.Configuration;
import play.Logger;
import play.mvc.*;

public class Application extends Controller {
	
	public static final String DEFAULT_DP2_ENDPOINT_LOCAL = "http://localhost:8181/ws";
	public static final String DEFAULT_DP2_ENDPOINT_REMOTE = "http://localhost:8182/ws";
	public static final String SLASH = System.getProperty("file.separator");
	public static final String DP2_START = "/".equals(SLASH) ? "./dp2 help" : "cmd /c start /B cmd /c dp2.exe help";
	public static final String DP2_HALT = "/".equals(SLASH) ? "./dp2 halt" : "cmd /c start /B cmd /c dp2.exe halt";
	
	public static final String datasource = Configuration.root().getString("dp2.datasource");
	public static org.daisy.pipeline.client.models.Alive alive = null;
	
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
			themeName = Setting.get("appearance.theme");
		return themeName;
	}
	
	private static String deployment = null;
	/**
	 * Returns a buffered value of the deployment type instead of having to check the DB each time using Setting.get("deployment").
	 * @return
	 */
	public static String deployment() {
		return deployment != null ? deployment : Setting.get("deployment");
	}
}
