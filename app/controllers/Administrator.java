package controllers;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import akka.actor.Cancellable;
import akka.util.Duration;

import models.Notification;
import models.NotificationConnection;
import models.Setting;
import models.User;
import play.Logger;
import play.data.Form;
import play.data.format.Formats;
import play.data.validation.Constraints;
import play.data.validation.Constraints.Required;
import play.data.validation.ValidationError;
import play.libs.Akka;
import play.mvc.*;
import utils.Pipeline2Engine;

public class Administrator extends Controller {
	
	public static class AdminForms {
		public Form<CreateAdminForm> createAdminForm = Administrator.createAdminForm;
		public Form<SetWSForm> setWSForm = Administrator.setWSForm;
		public Form<SetUploadDirForm> setStorageDirsForm = Administrator.setStorageDirsForm;
		public Form<ConfigureEmailForm> configureEmailForm = Administrator.configureEmailForm;
		public Form<User> userForm = Administrator.userForm;
		public Form<GuestUser> guestForm = Administrator.guestForm;
		public Form<GlobalPermissions> globalForm = Administrator.globalForm;
		public Form<SetMaintenanceForm> setMaintenanceForm = Administrator.setMaintenanceForm;
		public Form<ConfigureAppearanceForm> configureAppearanceForm = Administrator.configureAppearanceForm;
	}
	
	public final static Form<SetDeploymentForm> setDeploymentForm = form(SetDeploymentForm.class);
	public static class SetDeploymentForm {
		@Constraints.Required
		@Formats.NonEmpty
		public String deployment;

		public static void validate(Form<SetDeploymentForm> filledForm) {
			if (!"desktop".equals(filledForm.field("deployment").value()) &&
				!"server".equals(filledForm.field("deployment").value()))
				filledForm.reject("deployment", "You must choose whether you're using the Web UI on your own computer or on a server.");
		}
	}
	
	public final static Form<CreateAdminForm> createAdminForm = form(CreateAdminForm.class);
	public static class CreateAdminForm {
		@Constraints.Required
		@Formats.NonEmpty
		@Constraints.Email
		public String email;

		@Constraints.Required
		@Constraints.MinLength(6)
		public String password;
		
		@Constraints.MinLength(6)
		public String repeatPassword;
		
		public static void validate(Form<CreateAdminForm> filledForm) {
			if (User.findByEmail(filledForm.field("email").valueOr("")) != null)
				filledForm.reject("email", "That e-mail address is already taken");
			
			if (!filledForm.field("password").valueOr("").equals("") && !filledForm.field("password").valueOr("").equals(filledForm.field("repeatPassword").value()))
				filledForm.reject("repeatPassword", "Password doesn't match.");
		}
	}
	
	public final static Form<SetWSForm> setWSForm = form(SetWSForm.class);
	public static class SetWSForm {
        @Required
        public String endpoint;
        
        public String authid;
        
        public String secret;
        
        public String tempDir;
        
        public String resultDir;
        
        public static void validate(Form<SetWSForm> filledForm) {
        	if (filledForm.field("endpoint").valueOr("").equals(""))
        		filledForm.reject("endpoint", "Invalid endpoint URL.");
        }

		public static void save(Form<SetWSForm> filledForm) {
			Setting.set("dp2ws.endpoint", filledForm.field("endpoint").valueOr(""));
        	Setting.set("dp2ws.authid", filledForm.field("authid").valueOr(""));
        	if (Setting.get("dp2ws.secret") == null || !"".equals(filledForm.field("secret").value()))
        		Setting.set("dp2ws.secret", filledForm.field("secret").valueOr(""));
        	String tempDir = filledForm.field("tempDir").valueOr("");
        	String resultDir = filledForm.field("resultDir").valueOr("");
        	if (tempDir.contains("/") && !tempDir.endsWith("/")) tempDir += "/";
        	if (tempDir.contains("\\") && !tempDir.endsWith("\\")) tempDir += "\\";
        	if (resultDir.contains("/") && !resultDir.endsWith("/")) resultDir += "/";
        	if (resultDir.contains("\\") && !resultDir.endsWith("\\")) resultDir += "\\";
        	Setting.set("dp2ws.tempDir", tempDir);
        	Setting.set("dp2ws.resultDir", resultDir);
		}
    }
	
	public final static Form<SetUploadDirForm> setStorageDirsForm = form(SetUploadDirForm.class);
	public static class SetUploadDirForm {
        @Required
        public String uploaddir;
        
        public static void validate(Form<SetUploadDirForm> filledForm) {
        	String uploadPath = filledForm.field("uploaddir").valueOr("");
        	if (!uploadPath.endsWith(System.getProperty("file.separator")))
        		uploadPath += System.getProperty("file.separator");
        	File dir = new File(uploadPath);
        	if (!dir.exists())
        		filledForm.reject("uploaddir", "The directory does not exist.");
        	if (!dir.isDirectory())
        		filledForm.reject("uploaddir", "The path does not point to a directory.");
        }

		public static void save(Form<SetUploadDirForm> filledForm) {
			String uploadPath = filledForm.field("uploaddir").valueOr("");
        	if (!uploadPath.endsWith(System.getProperty("file.separator")))
        		uploadPath += System.getProperty("file.separator");
        	Setting.set("uploads", uploadPath);
		}
    }
	
	public final static Form<ConfigureEmailForm> configureEmailForm = form(ConfigureEmailForm.class);
	public static class ConfigureEmailForm {
		@Required
		public String smtp;
		
		public int port;
		
		public boolean ssl;
		
		public String username;
		
		public String password;
		
		public static void validate(Form<ConfigureEmailForm> filledForm) {
			if (filledForm.field("smtp").valueOr("").equals(""))
	    		filledForm.reject("smtp", "Invalid SMTP IP / domain name.");
			try {
				int port = Integer.parseInt(filledForm.field("port").valueOr(""));
				if (port < 0 || port > 65535)
					filledForm.reject("port", "The port must be a valid number between 0 and 65535.");
			} catch (NumberFormatException e) {
				filledForm.reject("port", "The port must be a valid number between 0 and 65535.");
			}
		}

		public static void save(Form<ConfigureEmailForm> filledForm) {
			Setting.set("mail.username", filledForm.field("username").valueOr(""));
			if (Setting.get("mail.password") == null || !"".equals(filledForm.field("password").value()))
        		Setting.set("mail.password", filledForm.field("password").valueOr(""));
			Setting.set("mail.provider", filledForm.field("emailService").valueOr(""));
			Setting.set("mail.smtp.host", filledForm.field("smtp").valueOr(""));
			Setting.set("mail.smtp.port", filledForm.field("port").valueOr(""));
			Setting.set("mail.smtp.ssl", filledForm.field("ssl").valueOr(""));
			Setting.set("mail.from.name", "Pipeline 2");
			Setting.set("mail.from.email", session("email")); // TODO: make configurable
		}
	}
	
	public final static Form<SetMaintenanceForm> setMaintenanceForm = form(SetMaintenanceForm.class);
	public static class SetMaintenanceForm {
        @Required
        public String maintenance;
        
        public static void validate(Form<SetMaintenanceForm> filledForm) {
        	String maintenanceString = filledForm.field("maintenance").valueOr("0");
        	Long maintenance = null;
        	try {
        		maintenance = Long.parseLong(maintenanceString);
        	} catch (NumberFormatException e) {
        		filledForm.reject("maintenance", "Please enter a number.");
        	}
        	if (maintenance != null && maintenance < 0)
        		filledForm.reject("maintenance", "Please enter a positive number.");
        }

		public static void save(Form<SetMaintenanceForm> filledForm) {
			long maintenanceTime = Long.parseLong(filledForm.field("maintenance").valueOr("0")) * 60000L;
        	Setting.set("jobs.deleteAfterDuration", maintenanceTime+"");
		}
    }
	
	public final static Form<ConfigureAppearanceForm> configureAppearanceForm = form(ConfigureAppearanceForm.class);
	public static class ConfigureAppearanceForm {
        
		public String title;
        public String theme;
        
        public static void validate(Form<ConfigureAppearanceForm> filledForm) {
        	String title = filledForm.field("title").valueOr("");
        	if ("".equals(title)) {
        		filledForm.reject("title", "The website must have a title.");
        	}
        	
        	String theme = filledForm.field("theme").valueOr("");
        	if (!"".equals(theme)) {
	        	File themeDir = new File("themes/"+theme);
	        	if (!themeDir.exists() || !themeDir.isDirectory())
	        		filledForm.reject("theme", "The theme \""+theme+"\" does not exist.");
        	}
        }
        
        public static List<String> themes = new ArrayList<String>();
        public static void refreshList() {
        	synchronized(themes) {
        		File themeDir = new File("themes/");
	        	if (!themeDir.isDirectory())
	        		return;
	        	
	        	themes = new ArrayList<String>();
	        	for (String theme : themeDir.list())
	        		themes.add(theme);
        	}
        }
		public static void save(Form<ConfigureAppearanceForm> filledForm) {
			String theme = filledForm.field("theme").valueOr("");
        	if (theme.length() > 0)
        		theme += "/";
        	Application.themeName = theme;
        	Setting.set("appearance.theme", theme);
        	Setting.set("appearance.title", filledForm.field("title").valueOr(Setting.get("appearance.title")));
		}
    }
	
	final static Form<User> userForm = form(User.class);
	
	final static Form<GuestUser> guestForm = form(GuestUser.class);
	public static class GuestUser {
		@Constraints.MinLength(1)
		@Constraints.Pattern("[^{}\\[\\]();:'\"<>]+") // Avoid breaking JavaScript code in templates
		public String name;
	}
	
	final static Form<GlobalPermissions> globalForm = form(GlobalPermissions.class);
	public static class GlobalPermissions {
		public boolean hideAdvancedOptions;
		
		public boolean allowGuests;
		public boolean automaticLogin;
		
		public boolean shareJobs;
		public boolean showEmailBox;
		public boolean showGuestName;
	}
	
	public static Result getSettings() {
		if (FirstUse.isFirstUse())
			return redirect(routes.FirstUse.getFirstUse());

		User user = User.authenticate(request(), session());
		if (user == null || !user.admin)
			return redirect(routes.Login.login());

		List<User> users = User.find.orderBy("admin, name, email").findList();
		AdminForms forms = new AdminForms();
		ConfigureAppearanceForm.refreshList();
		
		return ok(views.html.Administrator.settings.render(forms, users));
	}
	
	public static Result postSettings() {
		if (FirstUse.isFirstUse())
			return redirect(routes.FirstUse.getFirstUse());
		
		User user = User.authenticate(request(), session());
		if (user == null || !user.admin)
			return redirect(routes.Login.login());
		
		ConfigureAppearanceForm.refreshList();
		
		AdminForms forms = new AdminForms();
		
		String formName = request().queryString().containsKey("formName") ? request().queryString().get("formName")[0] :
							request().body().asFormUrlEncoded().containsKey("formName") ? request().body().asFormUrlEncoded().get("formName")[0] :
							null;
		if (formName == null) {
			flash("error", "Form not found. Submitted information ignored :(");
			redirect(routes.Administrator.getSettings());
		}
		
		flash("settings.formName", formName);
		
		if ("updateGlobalPermissions".equals(formName)) {
			Form<GlobalPermissions> filledForm = globalForm.bindFromRequest();
//			GlobalPermissions.validate(filledForm);
			
			flash("settings.usertab", "global");
			
			if (filledForm.hasErrors()) {
				forms.globalForm = filledForm;
				List<User> users = User.find.orderBy("admin, name, email").findList();
				return badRequest(views.html.Administrator.settings.render(forms, users));
				
			} else {
				String login = filledForm.field("login").valueOr("deny");
				if ("automatic".equals(login)) {
					Setting.set("users.guest.allowGuests", "true");
					Setting.set("users.guest.automaticLogin", "true");
					Setting.set("users.guest.showGuestName", "false");
					Setting.set("users.guest.shareJobs", "true");
					Setting.set("users.guest.showEmailBox", "false");
					
				} else if ("allow".equals(login)) {
					Setting.set("users.guest.allowGuests", "true");
					Setting.set("users.guest.automaticLogin", "false");
					Setting.set("users.guest.showGuestName", "true");
					Setting.set("users.guest.shareJobs", "false");
					Setting.set("users.guest.showEmailBox", "true");
					
				} else {
					Setting.set("users.guest.allowGuests", "false");
					Setting.set("users.guest.automaticLogin", "false");
					Setting.set("users.guest.showGuestName", "true");
					Setting.set("users.guest.shareJobs", "false");
					Setting.set("users.guest.showEmailBox", "false");
				}
				
				Setting.set("jobs.hideAdvancedOptions", filledForm.field("hideAdvancedOptions").valueOr("false"));
				flash("success", "Global permissions was updated successfully!");
				return redirect(routes.Administrator.getSettings());
			}
		}
		
		if ("updateGuest".equals(formName)) {
			Form<GuestUser> filledForm = guestForm.bindFromRequest();
//			GuestUser.validate(filledForm);
			
			flash("settings.usertab", "guest");
			
			if (filledForm.hasErrors()) {
				forms.guestForm = filledForm;
				List<User> users = User.find.orderBy("admin, name, email").findList();
				return badRequest(views.html.Administrator.settings.render(forms, users));
				
			} else {
				Setting.set("users.guest.name", filledForm.field("name").valueOr("Guest"));
				flash("success", "Guest was updated successfully!");
				return redirect(routes.Administrator.getSettings());
			}
		}
		
		if ("updateUser".equals(formName)) {
			Form<User> filledForm = userForm.bindFromRequest();
			Long userId = request().queryString().containsKey("userid") ? Long.parseLong(request().queryString().get("userid")[0])
					: request().body().asFormUrlEncoded().containsKey("userid") ? Long.parseLong(request().body().asFormUrlEncoded().get("userid")[0])
					: 0L;
			flash("settings.usertab", userId + "");
			
			User updateUser = User.findById(userId);
			if (updateUser == null) {
				flash("success", "Hmm, that's weird; the user was not found. Nothing was changed...");
				return redirect(routes.Administrator.getSettings());
			}
			
			updateUser.validateChange(filledForm, user);
			
			if (!updateUser.hasChanges(filledForm)) {
				flash("success", "You did not submit any changes. No changes were made.");
				return redirect(routes.Administrator.getSettings());
				
			} else if (filledForm.hasErrors()) {
				List<User> users = User.find.orderBy("admin, name, email").findList();
				forms.userForm = filledForm;
				flash("error", "Could not edit user, please review the form and make sure it is filled out properly.");
				return badRequest(views.html.Administrator.settings.render(forms, users));
				
			} else {
				updateUser.name = filledForm.field("name").valueOr("");
				updateUser.admin = filledForm.field("admin").valueOr("").equals("true");
				
				String oldEmail = updateUser.email;
				updateUser.email = filledForm.field("email").valueOr("");
				
				if ("true".equals(Setting.get("mail.enable"))) {
					if (!updateUser.email.equals(oldEmail)) {
						updateUser.active = false;
						updateUser.makeNewActivationUid();

						String activateUrl = routes.Account.showActivateForm(updateUser.email, updateUser.getActivationUid()).absoluteURL(request());
						String html = views.html.Account.emailActivate.render(activateUrl).body();
						String text = "Go to this link to activate your account: " + activateUrl;
						if (!Account.sendEmail("Activate your account", html, text, updateUser.name, updateUser.email))
							flash("error", "Was unable to send the e-mail.");
					}
					
				} else {
					String newPassword = filledForm.field("password").valueOr("");
					if (newPassword.length() > 0)
						updateUser.setPassword(newPassword);
				}
				
				updateUser.save(Application.datasource);
				if (updateUser.id.equals(user.id)) {
					session("name", user.name);
					session("email", user.email);
				}
				
				flash("success", "Your changes were saved successfully!");
				return redirect(routes.Administrator.getSettings());
			}
		}
		
		if ("resetPassword".equals(formName)) {
			Long userId = request().queryString().containsKey("userid") ? Long.parseLong(request().queryString().get("userid")[0])
					: request().body().asFormUrlEncoded().containsKey("userid") ? Long.parseLong(request().body().asFormUrlEncoded().get("userid")[0])
					: 0L;
			flash("settings.usertab", userId + "");

			User resetUser = User.findById(userId);

			if (resetUser.active) {
				resetUser.makeNewActivationUid();
				resetUser.save(Application.datasource);
				String resetUrl = routes.Account.showResetPasswordForm(resetUser.email, resetUser.getActivationUid()).absoluteURL(request());
				String html = views.html.Account.emailResetPassword.render(resetUrl).body();
				String text = "Go to this link to change your password: " + resetUrl;
				if (Account.sendEmail("Reset your password", html, text, resetUser.name, resetUser.email))
					flash("success", "A password reset link was sent to " + resetUser.name);
				else
					flash("error", "Was unable to send the e-mail. Please notify the owners of this website so they can fix their e-mail settings.");
				
			} else {
				resetUser.makeNewActivationUid();
				resetUser.save(Application.datasource);
				String activateUrl = routes.Account.showActivateForm(resetUser.email, resetUser.getActivationUid()).absoluteURL(request());
				String html = views.html.Account.emailActivate.render(activateUrl).body();
				String text = "Go to this link to activate your account: " + activateUrl;

				if (Account.sendEmail("Activate your account", html, text, resetUser.name, resetUser.email))
					flash("success", "An account activation link was sent to " + resetUser.name);
				else
					flash("error", "Was unable to send the e-mail. Please notify the owners of this website so they can fix their e-mail settings.");
			}

			return redirect(routes.Administrator.getSettings());
		}
		
		if ("deleteUser".equals(formName)) {
			Long userId = request().queryString().containsKey("userid") ? Long.parseLong(request().queryString().get("userid")[0])
					: request().body().asFormUrlEncoded().containsKey("userid") ? Long.parseLong(request().body().asFormUrlEncoded().get("userid")[0])
					: 0L;
			
			User deleteUser = User.findById(userId);
			
			if (deleteUser == null) {
				flash("success", "Hmm, that's weird. The user was not found; nothing was changed...");
				return redirect(routes.Administrator.getSettings());
			}
			
			flash("settings.usertab", deleteUser.id + "");
			
			if (deleteUser.id.equals(user.id)) {
				flash("error", "Only other admins can delete you, you cannot do it yourself");
				return redirect(routes.Administrator.getSettings());
			}
			
			deleteUser.delete(Application.datasource);
			flash("settings.usertab", "global");
			flash("success", deleteUser.name + " was deleted");
			return redirect(routes.Administrator.getSettings());
		}
		
		if ("createUser".equals(formName)) {
			Form<User> filledForm = userForm.bindFromRequest();
			User.validateNew(filledForm);
			
			if (filledForm.hasErrors()) {
				flash("settings.usertab", "adduser");
				List<User> users = User.find.orderBy("admin, name, email").findList();
				forms.userForm = filledForm;
				return badRequest(views.html.Administrator.settings.render(forms, users));
				
			} else {
				User newUser = new User(filledForm.field("email").valueOr(""),
										filledForm.field("name").valueOr(""),
										"true".equals(Setting.get("mail.enable")) ? "" : filledForm.field("password").valueOr(""),
										filledForm.field("admin").valueOr("").equals("true"));
				newUser.makeNewActivationUid();
				newUser.save(Application.datasource);
				
				if ("true".equals(Setting.get("mail.enable"))) {
					String activateUrl = routes.Account.showActivateForm(newUser.email, newUser.getActivationUid()).absoluteURL(request());
					String html = views.html.Account.emailActivate.render(activateUrl).body();
					String text = "Go to this link to activate your account: " + activateUrl;
		
					if (!Account.sendEmail("Activate your account", html, text, newUser.name, newUser.email))
						flash("error", "Was unable to send the e-mail. :(");
					
				}
				
				flash("settings.usertab", newUser.id + "");
				flash("success", "User " + newUser.name + " created successfully!");
				
				return redirect(routes.Administrator.getSettings());
			}
		}
		
		if ("setWS".equals(formName)) {
			Form<Administrator.SetWSForm> filledForm = setWSForm.bindFromRequest();
			Administrator.SetWSForm.validate(filledForm);
			
			if (filledForm.hasErrors()) {
				List<User> users = User.find.orderBy("admin, name, email").findList();
				forms.setWSForm = filledForm;
				return badRequest(views.html.Administrator.settings.render(forms, users));
	        	
	        } else {
	        	Administrator.SetWSForm.save(filledForm);
	        	flash("success", "Pipeline 2 Web API endpoint changed successfully!");
	        	return redirect(routes.Administrator.getSettings());
	        }
		}
		
		if ("setStorageDirs".equals(formName)) {
			Form<Administrator.SetUploadDirForm> filledForm = setStorageDirsForm.bindFromRequest();
			Administrator.SetUploadDirForm.validate(filledForm);
			
			if(filledForm.hasErrors()) {
	        	List<User> users = User.find.orderBy("admin, name, email").findList();
				forms.setStorageDirsForm = filledForm;
				return badRequest(views.html.Administrator.settings.render(forms, users));
	        	
	        } else {
	        	Administrator.SetUploadDirForm.save(filledForm);
	        	flash("success", "Uploads directory changed successfully!");
	        	return redirect(routes.Administrator.getSettings());
	        }
		}
		
		if ("configureEmail".equals(formName)) {
			Form<Administrator.ConfigureEmailForm> filledForm = configureEmailForm.bindFromRequest();
			Administrator.ConfigureEmailForm.validate(filledForm);
			
			if (filledForm.field("enable").value() != null) {
				Setting.set("mail.enable", filledForm.field("enable").valueOr(""));
				flash("success", "E-mail is now "+("true".equals(filledForm.field("enable").valueOr("false"))?"enabled":"disabled")+".");
				return redirect(routes.Administrator.getSettings());
				
			} else {
				if(filledForm.hasErrors()) {
					List<User> users = User.find.orderBy("admin, name, email").findList();
					forms.configureEmailForm = filledForm;
					return badRequest(views.html.Administrator.settings.render(forms, users));
	
				} else {
					Administrator.ConfigureEmailForm.save(filledForm);
					flash("success", "Successfully changed e-mail settings!");
		        	return redirect(routes.Administrator.getSettings());
				}
			}
		}
		
		if ("setMaintenance".equals(formName)) {
			Form<Administrator.SetMaintenanceForm> filledForm = setMaintenanceForm.bindFromRequest();
			Administrator.SetMaintenanceForm.validate(filledForm);
			
			if(filledForm.hasErrors()) {
	        	List<User> users = User.find.orderBy("admin, name, email").findList();
				forms.setMaintenanceForm  = filledForm;
				return badRequest(views.html.Administrator.settings.render(forms, users));
	        	
	        } else {
	        	Administrator.SetMaintenanceForm.save(filledForm);
	        	flash("success", "Maintenance time changed successfully!");
	        	return redirect(routes.Administrator.getSettings());
	        }
		}
		
		if ("configureAppearance".equals(formName)) {
			Form<Administrator.ConfigureAppearanceForm> filledForm = configureAppearanceForm.bindFromRequest();
			Administrator.ConfigureAppearanceForm.validate(filledForm);
			
			if(filledForm.hasErrors()) {
				for (String key : filledForm.errors().keySet()) {
					for (ValidationError error : filledForm.errors().get(key)) {
						Logger.debug(key+": "+error.message());
					}
				}
				
	        	List<User> users = User.find.orderBy("admin, name, email").findList();
				forms.configureAppearanceForm  = filledForm;
				return badRequest(views.html.Administrator.settings.render(forms, users));
	        	
	        } else {
	        	String theme = Application.themeName();
	        	String title = Setting.get("appearance.title");
	        	
	        	Administrator.ConfigureAppearanceForm.save(filledForm);
	        	
	        	String successString = "";
	        	if (!theme.equals(Application.themeName()))
	        		successString += "Theme changed to "+("".equals(Application.themeName())?"default":"\""+Application.themeName().substring(0, Application.themeName().length()-1)+"\"")+" !";
	        	if (!title.equals(Setting.get("appearance.title")))
	        		successString += " Title changed to \""+Setting.get("appearance.title")+"\" !";
	        	if ("".equals(successString))
	        		successString = "Nothing changed";
	        	flash("success", successString);
	        	return redirect(routes.Administrator.getSettings());
	        }
		}
		
		flash("error", "Form not found. Submitted information ignored :(");
		return redirect(routes.Administrator.getSettings());
	}
	
	public static Cancellable shutdownProgramatically() {
		// TODO: If running in desktop mode; shutdown after a period of inactivity?
		
		// Shutdown only allowed in desktop mode
		if (!"desktop".equals(Application.deployment()))
			return null;
		
		return Akka.system().scheduler().scheduleOnce(
				Duration.create(3, TimeUnit.SECONDS),
				new Runnable() {
					public void run() {
						try {
							// Pipeline engine
							if (Pipeline2Engine.cwd != null) {
								Logger.info("Attempting to stop the Pipeline engine...");
								Pipeline2Engine.halt();
							}
							
							// Web UI
							System.exit(0);
						} catch (javax.persistence.PersistenceException e) {
							// Ignores this exception that happens on shutdown:
							// javax.persistence.PersistenceException: java.sql.SQLException: Attempting to obtain a connection from a pool that has already been shutdown.
							// Should be safe to ignore I think...
						}
					}
				});
	}
	
	public static Result shutdown() {
		Cancellable cancellable = shutdownProgramatically();
		if (cancellable == null)
			Results.forbidden();
		
		flash("shutdown","true");
		return ok(views.html.Administrator.goodbye.render());
	}
	
	/**
	 * /admin/POST
	 * When POST is not possible (as in javascript redirect), this route lets you treat a GET request as a POST.
	 * @return
	 */
	public static Result getPostSettings() {
		return postSettings();
	}
}
