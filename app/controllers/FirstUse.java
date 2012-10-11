package controllers;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.daisy.pipeline.client.Alive;

import akka.actor.Cancellable;
import akka.util.Duration;

import play.Logger;
import play.Play;
import play.libs.Akka;
import play.mvc.*;
import play.data.*;
import utils.CommandExecutor;
import models.*;

/**
 * Helps with configuring the Web UI for the first time.
 * 
 * Configure database -> create admin account -> set webservice endpoint -> set upload directory -> welcome page!
 * 
 * @author jostein
 */
public class FirstUse extends Controller {
	
	/**
	 * GET /firstuse
	 * @return
	 */
	public static Result getFirstUse() {
		
		if (isFirstUse()) {
			session().remove("userid");
			if (!"desktop".equals(Application.deployment()) && !"server".equals(Application.deployment()))
				return ok(views.html.FirstUse.setDeployment.render(form(Administrator.SetDeploymentForm.class)));
			else if ("server".equals(Application.deployment())) {
				return ok(views.html.FirstUse.createAdmin.render(form(Administrator.CreateAdminForm.class)));
			} else if ("desktop".equals(Application.deployment())) {
				User admin = User.find.where().eq("email", "email@example.com").findUnique();
				if (admin == null) {
					admin = new User("email@example.com", "Administrator", "password", true);
				} else {
					admin.admin = true;
				}
				admin.save(Application.datasource);
				admin.login(session());
			}
		}
		
		User user = User.authenticate(request(), session());
		if (user == null || !user.admin) {
			return redirect(routes.Login.login());//loop
		}
		
		if ("desktop".equals(Application.deployment()) && (Setting.get("dp2fwk.dir") == null || "".equals(Setting.get("dp2fwk.dir")))) {
			Long browserId = user.flashBrowserId();
			startDP2Configurator(user.id, browserId);
			return ok(views.html.FirstUse.configureDP2.render());
		}
		
		if (Setting.get("dp2ws.endpoint") == null) {
			return ok(views.html.FirstUse.setWS.render(form(Administrator.SetWSForm.class)));
		}
		
		if (Setting.get("uploads") == null) {
			return ok(views.html.FirstUse.setUploadDir.render(form(Administrator.SetUploadDirForm.class)));
		}
		
		return ok(views.html.FirstUse.welcome.render());
	}
	
	public static Result postFirstUse() {
		String formName = request().body().asFormUrlEncoded().containsKey("formName") ? request().body().asFormUrlEncoded().get("formName")[0] : "";
		
		if ("setDeployment".equals(formName)) {
			if (!isFirstUse())
				return redirect(routes.FirstUse.getFirstUse());
			
			Form<Administrator.SetDeploymentForm> filledForm = form(Administrator.SetDeploymentForm.class).bindFromRequest();
			Administrator.SetDeploymentForm.validate(filledForm);
			
			if (filledForm.hasErrors()) {
				return badRequest(views.html.FirstUse.setDeployment.render(filledForm));
			
			} else {
				String deployment = filledForm.field("deployment").valueOr("unknown");
				Setting.set("deployment", deployment);
				
				if ("desktop".equals(deployment)) {
					User admin = new User("email@example.com", "Administrator", "password", true);
					admin.save(Application.datasource);
					admin.login(session());
					
					// Set some default configuration options
					Setting.set("users.guest.name", "Guest");
					Setting.set("users.guest.allowGuests", "true");
					Setting.set("users.guest.showGuestName", "false");
					Setting.set("users.guest.showEmailBox", "false");
					Setting.set("users.guest.shareJobs", "true");
					Setting.set("users.guest.automaticLogin", "true");
					Setting.set("dp2ws.sameFilesystem", "true");
					Setting.set("mail.enable", "false");
					Setting.set("uploads", System.getProperty("user.dir") + System.getProperty("file.separator") + "uploads" + System.getProperty("file.separator"));
				}
				
				return redirect(routes.FirstUse.getFirstUse());
			}
		}
		
		if ("createAdmin".equals(formName)) {
			if (!isFirstUse())
				return redirect(routes.FirstUse.getFirstUse());
			
			Form<Administrator.CreateAdminForm> filledForm = form(Administrator.CreateAdminForm.class).bindFromRequest();
			Administrator.CreateAdminForm.validate(filledForm);
			
			if (filledForm.hasErrors()) {
				return badRequest(views.html.FirstUse.createAdmin.render(filledForm));
			
			} else {
				User admin = new User(filledForm.field("email").valueOr(""), "Administrator", filledForm.field("password").valueOr(""), true);
				admin.save(Application.datasource);
				admin.login(session());
				
				// Set some default configuration options
				Setting.set("users.guest.name", "Guest");
				Setting.set("users.guest.allowGuests", "false");
				Setting.set("users.guest.showGuestName", "true");
				Setting.set("users.guest.showEmailBox", "true");
				Setting.set("users.guest.shareJobs", "false");
				Setting.set("users.guest.automaticLogin", "false");
				Setting.set("dp2ws.sameFilesystem", "false");
				Setting.set("mail.enable", "false");
				
				return redirect(routes.FirstUse.getFirstUse());
			}
		}
		
		User user = User.authenticate(request(), session());
		if (user == null || !user.admin) {
			return redirect(routes.Login.login());
		}
		
		if ("setWS".equals(formName)) {
			Form<Administrator.SetWSForm> filledForm = form(Administrator.SetWSForm.class).bindFromRequest();
			Administrator.SetWSForm.validate(filledForm);
			
			if (filledForm.hasErrors()) {
	        	return badRequest(views.html.FirstUse.setWS.render(filledForm));
	        	
	        } else {
	        	Administrator.SetWSForm.save(filledForm);
	        	return redirect(routes.FirstUse.getFirstUse());
	        }
		}
		
		if ("setUploadDir".equals(formName)) {
			Form<Administrator.SetUploadDirForm> filledForm = form(Administrator.SetUploadDirForm.class).bindFromRequest();
			Administrator.SetUploadDirForm.validate(filledForm);
			
			if(filledForm.hasErrors()) {
	        	return badRequest(views.html.FirstUse.setUploadDir.render(filledForm));
	        	
	        } else {
	        	Administrator.SetUploadDirForm.save(filledForm);
	        	return redirect(routes.FirstUse.getFirstUse());
	        }
		}
		
		return getFirstUse();
	}
	
	/**
	 * Returns true if this is the first time that the Web UI are used (i.e. there are no registered users).
	 * @return
	 */
	public static boolean isFirstUse() {
		return User.findAll().size() == 0 || "desktop".equals(Application.deployment()) && Setting.get("dp2fwk.dir") == null;
	}
	
	private static Cancellable dp2Locator = null;
	private static Date lastLocatorRun = null;
	private static void startDP2Configurator(final Long userId, final Long browserId) {
		if (dp2Locator != null)
			dp2Locator.cancel();
		
		dp2Locator = Akka.system().scheduler().schedule(
				Duration.create(0, TimeUnit.SECONDS),
				Duration.create(10, TimeUnit.SECONDS),
			new Runnable() {
				public void run() {
					if (lastLocatorRun != null && lastLocatorRun.after(new Date(new Date().getTime()-60000)))
						return; // wait for the previous instance of the thread to complete, or 60 seconds
					
					Map<String,Object> result = new HashMap<String,Object>();
					result.put("time", new Date());
					
					lastLocatorRun = new Date();
					NotificationConnection.pushAll(new Notification("dp2locator", 5));
					if (Setting.get("dp2fwk.dir") != null) {
						result.put("state", "SUCCESS"); // endpoint already configured
						result.put("endpoint", Setting.get("dp2ws.endpoint"));
						NotificationConnection.pushAll(new Notification("dp2locator", result));
						dp2Locator.cancel();
						return;
					}
					
					NotificationConnection.pushAll(new Notification("dp2locator", 10));
					File dp2dirFile = null;
					try {
						dp2dirFile = new File("").getAbsoluteFile().getParentFile();
						if (dp2dirFile != null && !new File(dp2dirFile.getAbsolutePath()+controllers.Application.SLASH+"cli"+controllers.Application.SLASH+"dp2").exists()) {
							dp2dirFile = null;
						}
					} catch (NullPointerException e) {
						// directory not found
					}
					if (Play.isDev()) dp2dirFile = new File("/home/jostein/Skrivebord/pipeline2-1.3/daisy-pipeline/");
					
					if (dp2dirFile == null) {
						result.put("state", "FWK_NOT_FOUND"); // fwk dir not found
						NotificationConnection.pushAll(new Notification("dp2locator", result));
						lastLocatorRun = null;
						return;
					}
					try {
						result.put("dp2dir", dp2dirFile.getCanonicalPath());
					} catch (IOException e) {
						Logger.error("Could not resolve cononical path name to dp2dir", e);
						result.put("dp2dir", dp2dirFile.getAbsolutePath());
					}
					
					NotificationConnection.pushAll(new Notification("dp2locator", 15));
					Logger.debug("Alive.isAlive("+controllers.Application.DEFAULT_DP2_ENDPOINT_REMOTE+")");
					if (Alive.isAlive(controllers.Application.DEFAULT_DP2_ENDPOINT_REMOTE)) {
						result.put("state", "PLEASE_STOP_FWK"); // please stop fwk (DEFAULT_DP2_ENDPOINT_REMOTE)
						result.put("endpoint", controllers.Application.DEFAULT_DP2_ENDPOINT_REMOTE);
						NotificationConnection.pushAll(new Notification("dp2locator", result));
						lastLocatorRun = null;
						return;
					}
					
					NotificationConnection.pushAll(new Notification("dp2locator", 40));
					result.put("endpoint", controllers.Application.DEFAULT_DP2_ENDPOINT_LOCAL);
					if (Alive.isAlive(controllers.Application.DEFAULT_DP2_ENDPOINT_LOCAL)) {
						Logger.debug("executing "+controllers.Application.DP2_HALT);
						int exitValue = CommandExecutor.executeCommandWithWorker(controllers.Application.DP2_HALT, new File(dp2dirFile, "cli"), 20000L);
						Logger.debug("exit value from "+controllers.Application.DP2_HALT+" is "+exitValue);
						if (exitValue != 0) {
							result.put("state", "PLEASE_STOP_FWK"); // please stop fwk (DEFAULT_DP2_ENDPOINT_LOCAL)
							NotificationConnection.pushAll(new Notification("dp2locator", result));
							lastLocatorRun = null;
							return;
						}
					}
					
					NotificationConnection.pushAll(new Notification("dp2locator", 75));
					Logger.debug("executing "+controllers.Application.DP2_START);
					int exitValue = CommandExecutor.executeCommandWithWorker(controllers.Application.DP2_START, new File(dp2dirFile, "cli"), 20000L);
					Logger.debug("exit value from "+controllers.Application.DP2_START+" is "+exitValue);
					if (exitValue != 0) {
						result.put("state", "UNABLE_TO_START_FWK"); // unable to start fwk; fwk is probably misconfigured
						NotificationConnection.pushAll(new Notification("dp2locator", result));
						lastLocatorRun = null;
						return;
					}
					
					NotificationConnection.pushAll(new Notification("dp2locator", 100));
					Setting.set("uploads", System.getProperty("user.dir") + System.getProperty("file.separator") + "uploads" + System.getProperty("file.separator"));
					Setting.set("dp2ws.endpoint", controllers.Application.DEFAULT_DP2_ENDPOINT_LOCAL);
					Setting.set("dp2ws.authid", "");
					Setting.set("dp2ws.secret", "");
					Setting.set("dp2ws.tempDir", System.getProperty("user.dir") + controllers.Application.SLASH + "local.temp" + controllers.Application.SLASH);
					Setting.set("dp2ws.resultDir", System.getProperty("user.dir") + controllers.Application.SLASH + "local.results" + controllers.Application.SLASH);
					Setting.set("dp2ws.sameFilesystem", "true");
					Setting.set("dp2fwk.dir", dp2dirFile.getAbsolutePath());
					result.put("state", "SUCCESS"); // successfully configured the framework/CLI communication
					NotificationConnection.pushAll(new Notification("dp2locator", result));
					
					dp2Locator.cancel();
					lastLocatorRun = null;
				}
			}
			);
	}
}
