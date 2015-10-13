package controllers;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.daisy.pipeline.client.http.WS;
import org.daisy.pipeline.client.http.WSInterface;
import org.daisy.pipeline.client.utils.Files;

import controllers.Assets.Asset;
import models.Notification;
import models.NotificationConnection;
import models.Setting;
import models.User;
import play.*;
import play.mvc.*;
import views.html.*;

public class Application extends Controller {
	
	public static final boolean debug = "DEBUG".equals(Configuration.root().getString("logger.application"));
	
	public static final String DEFAULT_DP2_ENDPOINT = "http://localhost:8181/ws";
	public static final String SLASH = System.getProperty("file.separator");
	public static final String DP2DATA;
	static {
		String os = System.getProperty("os.name");
		String home = System.getProperty("user.home");
		
		// create temporary dir for webui
		String systemTemp = System.getProperty("java.io.tmpdir");
		String dp2temp = systemTemp;
		try {
			File systemTempDir = new File(systemTemp);
			systemTemp = systemTempDir.getCanonicalPath();
			
			File dp2tempDir = File.createTempFile("daisy-pipeline-webui-", null);
			if (dp2tempDir.exists()) {
				dp2tempDir.delete();
			}
			dp2tempDir.mkdirs();
			dp2temp = dp2tempDir.getCanonicalPath();
		} catch (IOException e) {
			Logger.error("Could not get canonical path for temporary directory", e);
		}
		
		// get data directory for webui
		String dp2data = System.getenv("DP2DATA");
		if (dp2data == null || "".equals(dp2data)) {
			if (os.startsWith("Windows")) {
				dp2data = System.getenv("APPDATA") + SLASH + "DAISY Pipeline 2";
				
			} else if (os.startsWith("Mac OS X")) {
				dp2data = home + "/Library/Application Support/DAISY Pipeline 2";
				
			} else { // Linux etc.
				dp2data = home + SLASH + ".daisy-pipeline";
			}
		}
		try {
			File dp2dataDir = new File(dp2data);
			if (dp2dataDir.exists()) {
				dp2dataDir.delete();
			}
			dp2dataDir.mkdirs();
			dp2data = dp2dataDir.getCanonicalPath();
			
		} catch (IOException e) {
			Logger.error("Could not get canonical path for "+dp2data, e);
		}
		DP2DATA = dp2data;
	}
	
	public static WSInterface ws = new WS();
	
	private static org.daisy.pipeline.client.models.Alive alive = null;
	
	public static final String version;
	static {
		 String v = Application.class.getPackage().getImplementationVersion();
		 if (v == null)
			 version = "dev";
		 else
			 version = v;
		 assert !(Play.isProd() && "dev".equals(version));
	}
	
	public static Result index() {
		if (FirstUse.isFirstUse())
    		return redirect(routes.FirstUse.getFirstUse());
		
		User user = User.authenticate(request(), session());
		if (user == null)
			return redirect(routes.Login.login());
		
		String landingPage = Setting.get("appearance.landingPage");
		if ("welcome".equals(landingPage)) return redirect(routes.FirstUse.welcome());
		if ("scripts".equals(landingPage)) return redirect(routes.Jobs.newJob());
		if ("jobs".equals(landingPage) && !(user.id <= -2 && !"true".equals(Setting.get("users.guest.shareJobs")))) return redirect(routes.Jobs.getJobs());
		if ("about".equals(landingPage)) return redirect(routes.Application.about());
		if ("admin".equals(landingPage) && user.admin) return redirect(routes.Administrator.getSettings());
		if ("account".equals(landingPage) && user.id >= 0) return redirect(routes.Account.overview());
		
		return redirect(routes.Jobs.newJob());
	}
	
	public static Result about() {
		if (FirstUse.isFirstUse())
    		return redirect(routes.FirstUse.getFirstUse());
		
		User user = User.authenticate(request(), session());
		User.flashBrowserId(user);
		
		File about = new File("about.html");
		if (about.exists()) {
			return ok(views.html.about.render(Files.read(about)));
		} else {
			return ok(views.html.about.render(null));
		}
	}
	
	public static Result theme(String filename) {
		if ("".equals(themeName())) {
			return redirect(routes.Assets.versioned(new Asset(filename)));
			
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
					return redirect(routes.Assets.versioned(new Asset(filename)));
				}
			} else {
				return redirect(routes.Assets.versioned(new Asset(filename)));
			}
		}
	}
	
	public static Result error(int status, String name, String description, String message) {
		User user = User.authenticate(request(), session());
		User.flashBrowserId(user);
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
	
	public static String titleLink() {
		String titleLink = Setting.get("appearance.titleLink");
		if ("welcome".equals(titleLink)) titleLink = routes.FirstUse.welcome().toString();
		else if ("scripts".equals(titleLink)) titleLink = routes.Jobs.newJob().toString();
		else if ("jobs".equals(titleLink)) titleLink = routes.Jobs.getJobs().toString();
		else if ("about".equals(titleLink)) titleLink = routes.Application.about().toString();
		else if ("admin".equals(titleLink)) titleLink = routes.Administrator.getSettings().toString();
		else if ("account".equals(titleLink)) titleLink = routes.Account.overview().toString();
		return titleLink;
	}
	
	public static String absoluteURL(String url) {
		String absoluteURL = Setting.get("absoluteURL"); // for instance "http://localhost:9000" (protocol+host)
		if (absoluteURL == null) {
			return null;
		}
		
		if (url.matches("[^/]+:/.*")) {
			// absolute
			url = url.replaceFirst("[^/]+:/+[^/]+", absoluteURL);
			return url;
			
		} else {
			// relative
			if (!url.startsWith("/")) {
				absoluteURL += "/";
			}
			return absoluteURL+url;
		}
	}

	public static boolean pipeline2EngineAvailable() {
		return Application.alive != null && !Application.alive.error;
	}
	
	public static org.daisy.pipeline.client.models.Alive getAlive() {
		return alive;
	}
	
	public static void setAlive(org.daisy.pipeline.client.models.Alive alive) {
		Application.alive = alive;
		NotificationConnection.pushAll(new Notification("dp2.engine", alive));
	}
}
