package controllers;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
import utils.CommandExecutor;

public class Administrator extends Controller {
	
	public static class AdminForms {
		public Form<CreateAdminForm> createAdminForm = Administrator.createAdminForm;
		public Form<SetWSForm> setWSForm = Administrator.setWSForm;
		public Form<SetUploadDirForm> setUploadDirForm = Administrator.setUploadDirForm;
		public Form<ConfigureEmailForm> configureEmailForm = Administrator.configureEmailForm;
		public Form<User> userForm = Administrator.userForm;
		public Form<GuestUser> guestForm = Administrator.guestForm;
		public Form<GlobalPermissions> globalForm = Administrator.globalForm;
		public Form<SetJobCleanupForm> setJobCleanupForm = Administrator.setJobCleanupForm;
		public Form<ConfigureBrandingForm> configureBrandingForm = Administrator.configureBrandingForm;
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
        
        public boolean sameFilesystem;
        
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
        	Setting.set("dp2ws.sameFilesystem", filledForm.field("sameFilesystem").valueOr(""));
		}
    }
	
	public final static Form<SetUploadDirForm> setUploadDirForm = form(SetUploadDirForm.class);
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
	
	public final static Form<SetJobCleanupForm> setJobCleanupForm = form(SetJobCleanupForm.class);
	public static class SetJobCleanupForm {
        @Required
        public String jobcleanup;
        
        public static void validate(Form<SetJobCleanupForm> filledForm) {
        	String jobCleanupString = filledForm.field("jobcleanup").valueOr("0");
        	Long jobCleanup = null;
        	try {
        		jobCleanup = Long.parseLong(jobCleanupString);
        	} catch (NumberFormatException e) {
        		filledForm.reject("jobcleanup", "Please enter a number.");
        	}
        	if (jobCleanup != null && jobCleanup < 0)
        		filledForm.reject("jobcleanup", "Please enter a positive number.");
        }

		public static void save(Form<SetJobCleanupForm> filledForm) {
			long jobCleanupTime = Long.parseLong(filledForm.field("jobcleanup").valueOr("0")) * 60000L;
        	Setting.set("jobs.deleteAfterDuration", jobCleanupTime+"");
		}
    }
	
	public final static Form<ConfigureBrandingForm> configureBrandingForm = form(ConfigureBrandingForm.class);
	public static class ConfigureBrandingForm {
        
		public String title;
        public String theme;
        
        public static void validate(Form<ConfigureBrandingForm> filledForm) {
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
		public static void save(Form<ConfigureBrandingForm> filledForm) {
			String theme = filledForm.field("theme").valueOr("");
        	if (theme.length() > 0)
        		theme += "/";
        	Application.themeName = theme;
        	Setting.set("branding.theme", theme);
        	Setting.set("branding.title", filledForm.field("title").valueOr(Setting.get("branding.title")));
		}
    }
	
	final static Form<User> userForm = form(User.class);
	
	final static Form<GuestUser> guestForm = form(GuestUser.class);
	public static class GuestUser {
		
		public boolean allowGuests;
		public boolean automaticLogin;
		
		@Constraints.MinLength(1)
		@Constraints.Pattern("[^{}\\[\\]();:'\"<>]+") // Avoid breaking JavaScript code in templates
		public String name;
		
		public boolean shareJobs;
		public boolean showEmailBox;
		public boolean showGuestName;
	}
	
	final static Form<GlobalPermissions> globalForm = form(GlobalPermissions.class);
	public static class GlobalPermissions {
		public boolean hideAdvancedOptions;
	}
	
	public static Result getSettings() {
		if (FirstUse.isFirstUse())
			return redirect(routes.FirstUse.getFirstUse());

		User user = User.authenticate(request(), session());
		if (user == null || !user.admin)
			return redirect(routes.Login.login());

		List<User> users = User.find.orderBy("admin, name, email").findList();
		AdminForms forms = new AdminForms();
		ConfigureBrandingForm.refreshList();
		
		return ok(views.html.Administrator.settings.render(forms, users));
	}
	
	public static Result postSettings() {
		if (FirstUse.isFirstUse())
			return redirect(routes.FirstUse.getFirstUse());
		
		User user = User.authenticate(request(), session());
		if (user == null || !user.admin)
			return redirect(routes.Login.login());
		
		AdminForms forms = new AdminForms();
		
		String formName = request().queryString().containsKey("formName") ? request().queryString().get("formName")[0] :
							request().body().asFormUrlEncoded().containsKey("formName") ? request().body().asFormUrlEncoded().get("formName")[0] :
							null;
		if (formName == null) {
			flash("error", "Form not found. Submitted information ignored :(");
			redirect(routes.Administrator.getSettings());
		}
		
		if ("updateGlobalPermissions".equals(formName)) {
			Form<GlobalPermissions> filledForm = globalForm.bindFromRequest();
//			GlobalPermissions.validate(filledForm);
			
			flash("settings.usertab", "global");
			
			if (filledForm.hasErrors()) {
				forms.globalForm = filledForm;
				List<User> users = User.find.orderBy("admin, name, email").findList();
				ConfigureBrandingForm.refreshList();
				return badRequest(views.html.Administrator.settings.render(forms, users));
				
			} else {
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
				ConfigureBrandingForm.refreshList();
				return badRequest(views.html.Administrator.settings.render(forms, users));
				
			} else {
				String login = filledForm.field("login").valueOr("deny");
				if ("automatic".equals(login)) {
					Setting.set("users.guest.allowGuests", "true");
					Setting.set("users.guest.automaticLogin", "true");
					
				} else if ("allow".equals(login)) {
					Setting.set("users.guest.allowGuests", "true");
					Setting.set("users.guest.automaticLogin", "false");
					
				} else {
					Setting.set("users.guest.allowGuests", "false");
					Setting.set("users.guest.automaticLogin", "false");
				}
				
				Setting.set("users.guest.name", filledForm.field("name").valueOr("Guest"));
				Setting.set("users.guest.showGuestName", filledForm.field("showGuestName").valueOr("false"));
				Setting.set("users.guest.shareJobs", filledForm.field("shareJobs").valueOr("false"));
				Setting.set("users.guest.showEmailBox", filledForm.field("showEmailBox").valueOr("true"));
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
				ConfigureBrandingForm.refreshList();
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
				ConfigureBrandingForm.refreshList();
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
				ConfigureBrandingForm.refreshList();
				return badRequest(views.html.Administrator.settings.render(forms, users));
	        	
	        } else {
	        	Administrator.SetWSForm.save(filledForm);
	        	flash("success", "Pipeline 2 Web Service endpoint changed successfully!");
	        	return redirect(routes.Administrator.getSettings());
	        }
		}
		
		if ("setUploadDir".equals(formName)) {
			Form<Administrator.SetUploadDirForm> filledForm = setUploadDirForm.bindFromRequest();
			Administrator.SetUploadDirForm.validate(filledForm);
			
			if(filledForm.hasErrors()) {
	        	List<User> users = User.find.orderBy("admin, name, email").findList();
				forms.setUploadDirForm = filledForm;
				ConfigureBrandingForm.refreshList();
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
				return redirect(routes.Administrator.getSettings());
				
			} else {
				if(filledForm.hasErrors()) {
					List<User> users = User.find.orderBy("admin, name, email").findList();
					forms.configureEmailForm = filledForm;
					ConfigureBrandingForm.refreshList();
					return badRequest(views.html.Administrator.settings.render(forms, users));
	
				} else {
					Administrator.ConfigureEmailForm.save(filledForm);
					flash("success", "Successfully changed e-mail settings!");
		        	return redirect(routes.Administrator.getSettings());
				}
			}
		}
		
		if ("setJobCleanup".equals(formName)) {
			Form<Administrator.SetJobCleanupForm> filledForm = setJobCleanupForm.bindFromRequest();
			Administrator.SetJobCleanupForm.validate(filledForm);
			
			if(filledForm.hasErrors()) {
	        	List<User> users = User.find.orderBy("admin, name, email").findList();
				forms.setJobCleanupForm  = filledForm;
				ConfigureBrandingForm.refreshList();
				return badRequest(views.html.Administrator.settings.render(forms, users));
	        	
	        } else {
	        	Administrator.SetJobCleanupForm.save(filledForm);
	        	flash("success", "Job cleanup time changed successfully!");
	        	return redirect(routes.Administrator.getSettings());
	        }
		}
		
		if ("configureBranding".equals(formName)) {
			Form<Administrator.ConfigureBrandingForm> filledForm = configureBrandingForm.bindFromRequest();
			Administrator.ConfigureBrandingForm.validate(filledForm);
			
			if(filledForm.hasErrors()) {
				for (String key : filledForm.errors().keySet()) {
					for (ValidationError error : filledForm.errors().get(key)) {
						Logger.debug(key+": "+error.message());
					}
				}
				
	        	List<User> users = User.find.orderBy("admin, name, email").findList();
				forms.configureBrandingForm  = filledForm;
				ConfigureBrandingForm.refreshList();
				return badRequest(views.html.Administrator.settings.render(forms, users));
	        	
	        } else {
	        	String theme = Application.themeName();
	        	String title = Setting.get("branding.title");
	        	
	        	Administrator.ConfigureBrandingForm.save(filledForm);
	        	
	        	String successString = "";
	        	if (!theme.equals(Application.themeName()))
	        		successString += "Theme changed to "+("".equals(Application.themeName())?"default":"\""+Application.themeName().substring(0, Application.themeName().length()-1)+"\"")+" !";
	        	if (!title.equals(Setting.get("branding.title")))
	        		successString += " Title changed to \""+Setting.get("branding.title")+"\" !";
	        	flash("success", successString);
	        	return redirect(routes.Administrator.getSettings());
	        }
		}
		
		flash("error", "Form not found. Submitted information ignored :(");
		return redirect(routes.Administrator.getSettings());
	}
	
	public static Result shutdown() {
		// If running in desktop mode; shutdown after a period of inactivity
		if (!"desktop".equals(Application.deployment()))
			return Results.forbidden();
		
		Akka.system().scheduler().scheduleOnce(
				Duration.create(1, TimeUnit.SECONDS),
				new Runnable() {
					public void run() {
						// DP2 framework
						String dp2fwkDir = Setting.get("dp2fwk.dir");
						if (dp2fwkDir != null && !"".equals(dp2fwkDir)) {
							Logger.info("Attempting to stop the DAISY Pipeline 2 framework...");
							Setting.set("dp2fwk.state","STOPPING");
							NotificationConnection.pushAll(new Notification("dp2fwk.state", "STOPPING"));
							int exitValue = CommandExecutor.executeCommandWithWorker(Application.DP2_HALT, new File(dp2fwkDir, "cli"), 20000L);
							if (exitValue != 0) {
								Setting.set("dp2fwk.state","STOPPED");
								NotificationConnection.pushAll(new Notification("dp2fwk.state", "STOPPED"));
								Logger.info("Halted the DAISY Pipeline 2 framework");
							} else {
								Setting.set("dp2fwk.state","RUNNING");
								Logger.info("Failed to halt the DAISY Pipeline 2 framework");
							}
						}
						
						// Web UI
						System.exit(0);
					}
				});
		
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
