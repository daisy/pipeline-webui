package controllers;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.persistence.Transient;

import org.daisy.pipeline.client.Pipeline2Exception;
import org.daisy.pipeline.client.http.WSResponse;
//import org.daisy.pipeline.client.models.Script;

import org.daisy.pipeline.client.models.Script;

import controllers.SystemStatus.EngineAttempt;
import akka.actor.Cancellable;
import models.Setting;
import models.User;
import models.UserSetting;
import play.Logger;
import play.data.Form;
import play.data.format.Formats;
import play.data.validation.Constraints;
import play.data.validation.Constraints.Required;
import play.data.validation.ValidationError;
import play.libs.Akka;
import play.libs.Json;
import play.mvc.*;
import scala.concurrent.duration.Duration;
import utils.FormHelper;

public class Administrator extends Controller {

	public static class AdminForms {
		public Form<CreateAdminForm> createAdminForm = Administrator.createAdminForm;
		public Form<SetWSForm> setWSForm = Administrator.setWSForm;
		public Form<SetStorageDirsForm> setStorageDirsForm = Administrator.setStorageDirsForm;
		public Form<ConfigureEmailForm> configureEmailForm = Administrator.configureEmailForm;
		public Form<User> userForm = Administrator.userForm;
		public Form<GuestUser> guestForm = Administrator.guestForm;
		public Form<GlobalPermissions> globalForm = Administrator.globalForm;
		public Form<SetMaintenanceForm> setMaintenanceForm = Administrator.setMaintenanceForm;
		public Form<ConfigureAppearanceForm> configureAppearanceForm = Administrator.configureAppearanceForm;
		public Form<ConfigureScripts> scriptsForm = Administrator.scriptsForm;
	}

	public final static Form<CreateAdminForm> createAdminForm = play.data.Form.form(CreateAdminForm.class);
	public static class CreateAdminForm {
		@Constraints.Required
		@Formats.NonEmpty
		@Constraints.Email
		public String email;

		@Constraints.Required
		@Constraints.MinLength(6)
		public String password;

		@Constraints.Required
		@Constraints.MinLength(6)
		public String repeatPassword;

		public static void validate(Form<CreateAdminForm> filledForm) {
			if (User.findByEmail(filledForm.field("email").valueOr("")) != null)
				filledForm.reject("email", "That e-mail address is already taken");

			if (!filledForm.field("password").valueOr("").equals("") && !filledForm.field("password").valueOr("").equals(filledForm.field("repeatPassword").value()))
				filledForm.reject("repeatPassword", "Password doesn't match.");
		}
	}

	public final static Form<SetWSForm> setWSForm = play.data.Form.form(SetWSForm.class);
	public static class SetWSForm {
		@Required
		public String endpoint;

		public String authid;

		public String secret;

		public String engine; // only used in the response
		
		@Transient
		public static Map<String,Map<String,Object>> aliveAttempts = new HashMap<String,Map<String,Object>>();
		@Transient
		public static Map<String,Map<String,Object>> authAttempts = new HashMap<String,Map<String,Object>>();

		public static void validate(Form<SetWSForm> filledForm) {
			String url = filledForm.field("endpoint").valueOr("");
			String authid = filledForm.field("authid").valueOr("");
			String secret = filledForm.field("secret").valueOr("");
			
			EngineAttempt engineAttempt = SystemStatus.engineAttempt(url, authid, secret);
			
			if (engineAttempt.aliveError != null) {
				filledForm.reject("endpoint", engineAttempt.aliveError);
			}
			
			if (!filledForm.hasErrors() && engineAttempt.authError != null) {
				filledForm.reject("authid", engineAttempt.authError);
				filledForm.reject("secret", engineAttempt.authError);
			}
			
			if (engineAttempt.alive != null)
				filledForm.data().put("engine", Json.toJson(engineAttempt.alive).toString());
			
		}

		public static void save(Form<SetWSForm> filledForm) {
			Setting.set("dp2ws.endpoint", filledForm.field("endpoint").valueOr(""));
			Setting.set("dp2ws.authid", filledForm.field("authid").valueOr(""));
			if (Setting.get("dp2ws.secret") == null || !"".equals(filledForm.field("secret").value()))
				Setting.set("dp2ws.secret", filledForm.field("secret").valueOr(""));
		}
	}

	public final static Form<SetStorageDirsForm> setStorageDirsForm = play.data.Form.form(SetStorageDirsForm.class);
	public static class SetStorageDirsForm {
		@Required
		public String uploaddir;
		
		@Required
		public String jobsdir;
		
		@Required
		public String templatesdir;

		public static void validate(Form<SetStorageDirsForm> filledForm) {
			String uploadPath = filledForm.field("uploaddir").valueOr("");
			if (!uploadPath.endsWith(System.getProperty("file.separator")))
				uploadPath += System.getProperty("file.separator");
			
			String jobsPath = filledForm.field("jobsdir").valueOr("");
			if (!jobsPath.endsWith(System.getProperty("file.separator")))
				jobsPath += System.getProperty("file.separator");
			
			String templatesPath = filledForm.field("templatesdir").valueOr("");
			if (!templatesPath.endsWith(System.getProperty("file.separator")))
				templatesPath += System.getProperty("file.separator");
			
			File uploadDir = new File(uploadPath);
			if (!uploadDir.exists())
				filledForm.reject("uploaddir", "The directory does not exist.");
			else if (!uploadDir.isDirectory())
				filledForm.reject("uploaddir", "The path does not point to a directory.");
			
			File jobsDir = new File(jobsPath);
			if (!jobsDir.exists())
				filledForm.reject("jobsdir", "The directory does not exist.");
			else if (!jobsDir.isDirectory())
				filledForm.reject("jobsdir", "The path does not point to a directory.");
			
			File templatesDir = new File(templatesPath);
			if (!templatesDir.exists())
				filledForm.reject("templatesdir", "The directory does not exist.");
			else if (!templatesDir.isDirectory())
				filledForm.reject("templatesdir", "The path does not point to a directory.");
		}

		public static void save(Form<SetStorageDirsForm> filledForm) {
			String uploadPath = filledForm.field("uploaddir").valueOr("");
			if (!uploadPath.endsWith(System.getProperty("file.separator")))
				uploadPath += System.getProperty("file.separator");
			
			String jobsPath = filledForm.field("jobsdir").valueOr("");
			if (!jobsPath.endsWith(System.getProperty("file.separator")))
				jobsPath += System.getProperty("file.separator");
			
			String templatesPath = filledForm.field("templatesdir").valueOr("");
			if (!templatesPath.endsWith(System.getProperty("file.separator")))
				templatesPath += System.getProperty("file.separator");
			
			Setting.set("uploads", uploadPath);
			Setting.set("jobs", uploadPath);
			Setting.set("templates", uploadPath);
		}
	}

	public final static Form<ConfigureEmailForm> configureEmailForm = play.data.Form.form(ConfigureEmailForm.class);
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
			Setting.set("mail.from.email", session("email"));
		}
	}

	public final static Form<SetMaintenanceForm> setMaintenanceForm = play.data.Form.form(SetMaintenanceForm.class);
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

	public final static Form<ConfigureAppearanceForm> configureAppearanceForm = play.data.Form.form(ConfigureAppearanceForm.class);
	public static class ConfigureAppearanceForm {
		
		@Formats.NonEmpty
		public String title;
		
		public String titleLink;
		
		public String titleLinkNewWindow;
		
		public String landingPage;
		
		public String theme;

		public static void validate(Form<ConfigureAppearanceForm> filledForm) {
			String title = filledForm.field("title").valueOr("");
			if ("".equals(title)) {
				filledForm.reject("title", "The website must have a title.");
			}
			
			// any string is valid for titleLink - it is up to the administrator to set a proper one
			
			String landingPage = filledForm.field("landingPage").valueOr("");
			if (!"welcome".equals(landingPage) && !"scripts".equals(landingPage) && !"jobs".equals(landingPage) &&
											!"about".equals(landingPage) && !"admin".equals(landingPage) && !"account".equals(landingPage)) {
				filledForm.reject("landingPage", "Please select a valid landing page.");
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
			Setting.set("appearance.titleLink", filledForm.field("titleLink").valueOr(Setting.get("appearance.titleLink")));
			Setting.set("appearance.titleLink.newWindow", ("on".equals(filledForm.field("titleLinkNewWindow").value())?"true":"false"));
			Setting.set("appearance.landingPage", filledForm.field("landingPage").valueOr(Setting.get("appearance.landingPage")));
			Setting.set("appearance.title", filledForm.field("title").valueOr(Setting.get("appearance.title")));
		}
	}

	final static Form<User> userForm = play.data.Form.form(User.class);

	final static Form<GuestUser> guestForm = play.data.Form.form(GuestUser.class);
	public static class GuestUser {
		@Constraints.MinLength(1)
		@Constraints.Pattern("[^{}\\[\\]();:'\"<>]+") // Avoid breaking JavaScript code in templates
		public String name;
	}
	
	final static Form<ConfigureScripts> scriptsForm = play.data.Form.form(ConfigureScripts.class);
	public static class ConfigureScripts {
		/*public static Object scriptPermissions() {
			Map<String,Boolean> scriptPermissions = new HashMap<String,Boolean>();
			
			List<Script> scripts = Scripts.get();
			if (scripts == null) {
				Logger.error("Something bad happened, try refreshing the page or ask a technician for help");
				return "Something bad happened, try refreshing the page or ask a technician for help";
			}
			
			for (Script script : scripts) {
				String enabled = UserSetting.get(-2L, "scriptEnabled-"+script.getId());
				if ("false".equals(enabled)) {
					scriptPermissions.put(script.getId(), false);
				} else {
					scriptPermissions.put(script.getId(), true);
				}
			}
			
			return scriptPermissions;
		}*/
	}

	final static Form<GlobalPermissions> globalForm = play.data.Form.form(GlobalPermissions.class);
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
		
		User.flashBrowserId(user);
		return ok(views.html.Administrator.settings.render(forms, users));
	}

	public static Result postSettings() {
		if (FirstUse.isFirstUse())
			return redirect(routes.FirstUse.getFirstUse());

		User user = User.authenticate(request(), session());
		if (user == null || !user.admin)
			return redirect(routes.Login.login());
		
		Map<String, String[]> query = request().queryString();
//		Map<String, String[]> form = request().body().asFormUrlEncoded();

		ConfigureAppearanceForm.refreshList();

		AdminForms forms = new AdminForms();

		String formName = request().queryString().containsKey("formName") ? request().queryString().get("formName")[0] :
			request().body().asFormUrlEncoded().containsKey("formName") ? request().body().asFormUrlEncoded().get("formName")[0] :
				null;
		if (formName == null) {
			flash("error", "Form not found. Submitted information ignored :(");
			redirect(routes.Administrator.getSettings());
		}
		
		if (!query.containsKey("validate")) {
			flash("settings.formName", formName);
		}

		if ("updateGlobalPermissions".equals(formName)) {
			Form<GlobalPermissions> filledForm = globalForm.bindFromRequest();
			//			GlobalPermissions.validate(filledForm);

			flash("settings.usertab", "global");
			
			if (query.containsKey("validate")) {
				User.flashBrowserId(user);
				return ok(FormHelper.asJson(filledForm));
			
			} else if (filledForm.hasErrors()) {
				forms.globalForm = filledForm;
				List<User> users = User.find.orderBy("admin, name, email").findList();
				User.flashBrowserId(user);
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
			
			if (query.containsKey("validate")) {
				User.flashBrowserId(user);
				return ok(FormHelper.asJson(filledForm));
			
			} else if (filledForm.hasErrors()) {
				forms.guestForm = filledForm;
				List<User> users = User.find.orderBy("admin, name, email").findList();
				User.flashBrowserId(user);
				return badRequest(views.html.Administrator.settings.render(forms, users));

			} else {
				Setting.set("users.guest.name", filledForm.field("name").valueOr("Guest"));
				
				flash("success", "Guest was updated successfully!");
				return redirect(routes.Administrator.getSettings());
			}
		}
		
		if ("configureScripts".equals(formName)) {
			Form<ConfigureScripts> filledForm = scriptsForm.bindFromRequest();

			if (query.containsKey("validate")) {
				User.flashBrowserId(user);
				return ok(FormHelper.asJson(filledForm));
			
			} else if (filledForm.hasErrors()) {
				forms.scriptsForm = filledForm;
				List<User> users = User.find.orderBy("admin, name, email").findList();
				User.flashBrowserId(user);
				return badRequest(views.html.Administrator.settings.render(forms, users));

			} else {
				Map<String, String[]> form = request().body().asFormUrlEncoded();
				for (String fieldName : form.keySet()) {
					if (fieldName.startsWith("configureScripts-script-")) {
						String scriptId = fieldName.substring("configureScripts-script-".length());
						String scriptEnabled = form.get(fieldName)[0];
						UserSetting.set(-2L, "scriptEnabled-"+scriptId, scriptEnabled);
						Logger.debug(scriptId+" - "+scriptEnabled);
					}
				}
				
				flash("success", "Scripts were updated successfully!");
				return redirect(routes.Administrator.getSettings());
			}
		}

		if ("updateUser".equals(formName)) {
			Form<User> filledForm = userForm.bindFromRequest();
			Long userId = request().queryString().containsKey("userid") ? Long.parseLong(request().queryString().get("userid")[0])
					: request().body().asFormUrlEncoded().containsKey("userid") ? Long.parseLong(request().body().asFormUrlEncoded().get("userid")[0])
							: 0L;
			flash("settings.usertab", ""+userId);

			User updateUser = User.findById(userId);
			if (updateUser == null) {
				flash("success", "Hmm, that's weird; the user was not found. Nothing was changed...");
				return redirect(routes.Administrator.getSettings());
			}
			
			if (query.containsKey("validate")) {
				User.flashBrowserId(user);
				return ok(FormHelper.asJson(filledForm,new String[]{"password"}));
			
			} else if (filledForm.hasErrors()) {
				List<User> users = User.find.orderBy("admin, name, email").findList();
				forms.userForm = filledForm;
				flash("error", "Could not edit user, please review the form and make sure it is filled out properly.");
				User.flashBrowserId(user);
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

						String activateUrl = Application.absoluteURL(routes.Account.showActivateForm(updateUser.email, updateUser.getActivationUid()).absoluteURL(request()));
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

				updateUser.save();
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
				resetUser.save();
				String resetUrl = Application.absoluteURL(routes.Account.showResetPasswordForm(resetUser.email, resetUser.getActivationUid()).absoluteURL(request()));
				String html = views.html.Account.emailResetPassword.render(resetUrl).body();
				String text = "Go to this link to change your password: " + resetUrl;
				if (Account.sendEmail("Reset your password", html, text, resetUser.name, resetUser.email))
					flash("success", "A password reset link was sent to " + resetUser.name);
				else
					flash("error", "Was unable to send the e-mail. Please notify the owners of this website so they can fix their e-mail settings.");

			} else {
				resetUser.makeNewActivationUid();
				resetUser.save();
				String activateUrl = Application.absoluteURL(routes.Account.showActivateForm(resetUser.email, resetUser.getActivationUid()).absoluteURL(request()));
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

			deleteUser.delete();
			flash("settings.usertab", "global");
			flash("success", deleteUser.name + " was deleted");
			return redirect(routes.Administrator.getSettings());
		}

		if ("createUser".equals(formName)) {
			Form<User> filledForm = userForm.bindFromRequest();
			User.validateNew(filledForm);
			
			if (query.containsKey("validate")) {
				User.flashBrowserId(user);
				return ok(FormHelper.asJson(filledForm,new String[]{"password"}));
			
			} else if (filledForm.hasErrors()) {
				flash("settings.usertab", "adduser");
				List<User> users = User.find.orderBy("admin, name, email").findList();
				forms.userForm = filledForm;
				User.flashBrowserId(user);
				return badRequest(views.html.Administrator.settings.render(forms, users));

			} else {
				User newUser = new User(filledForm.field("email").valueOr(""),
						filledForm.field("name").valueOr(""),
						"true".equals(Setting.get("mail.enable")) ? "" : filledForm.field("password").valueOr(""),
								filledForm.field("admin").valueOr("").equals("true"));
				newUser.makeNewActivationUid();
				newUser.save();

				if ("true".equals(Setting.get("mail.enable"))) {
					String activateUrl = Application.absoluteURL(routes.Account.showActivateForm(newUser.email, newUser.getActivationUid()).absoluteURL(request()));
					String html = views.html.Account.emailActivate.render(activateUrl).body();
					String text = "Go to this link to activate your account: " + activateUrl;

					if (!Account.sendEmail("Activate your account", html, text, newUser.name, newUser.email))
						flash("error", "Was unable to send the e-mail. :(");

				}

				flash("settings.usertab", newUser.id  + "");
				flash("success", "User " + newUser.name + " created successfully!");

				return redirect(routes.Administrator.getSettings());
			}
		}

		if ("setWS".equals(formName)) {
			Form<Administrator.SetWSForm> filledForm = setWSForm.bindFromRequest();
			Administrator.SetWSForm.validate(filledForm);
			
			if (query.containsKey("validate")) {
				User.flashBrowserId(user);
				try {
					return ok(FormHelper.asJson(filledForm));
				} catch (RuntimeException e) {
					Logger.error("validation failed", e);
					return internalServerError("validation failed");
				}
			
			} else if (filledForm.hasErrors()) {
				List<User> users = User.find.orderBy("admin, name, email").findList();
				forms.setWSForm = filledForm;
				User.flashBrowserId(user);
				return badRequest(views.html.Administrator.settings.render(forms, users));

			} else {
				Administrator.SetWSForm.save(filledForm);
				flash("success", "Pipeline 2 Web API endpoint changed successfully!");
				return redirect(routes.Administrator.getSettings());
			}
		}

		if ("setStorageDirs".equals(formName)) {
			Form<Administrator.SetStorageDirsForm> filledForm = setStorageDirsForm.bindFromRequest();
			Administrator.SetStorageDirsForm.validate(filledForm);
			
			if (query.containsKey("validate")) {
				User.flashBrowserId(user);
				return ok(FormHelper.asJson(filledForm));
			
			} else if (filledForm.hasErrors()) {
				List<User> users = User.find.orderBy("admin, name, email").findList();
				forms.setStorageDirsForm = filledForm;
				User.flashBrowserId(user);
				return badRequest(views.html.Administrator.settings.render(forms, users));

			} else {
				Administrator.SetStorageDirsForm.save(filledForm);
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

			} else if (filledForm.field("absoluteURL").value() != null) {
				Setting.set("absoluteURL", filledForm.field("absoluteURL").valueOr(""));
				flash("success", "Absolute URL was successfully changed to "+filledForm.field("absoluteURL").valueOr("")+".");
				return redirect(routes.Administrator.getSettings());

			} else {
				
				if (query.containsKey("validate")) {
					User.flashBrowserId(user);
					return ok(FormHelper.asJson(filledForm));
					
				} else if (filledForm.hasErrors()) {
					List<User> users = User.find.orderBy("admin, name, email").findList();
					forms.configureEmailForm = filledForm;
					User.flashBrowserId(user);
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
			
			if (query.containsKey("validate")) {
				User.flashBrowserId(user);
				return ok(FormHelper.asJson(filledForm));
			
			} else if (filledForm.hasErrors()) {
				List<User> users = User.find.orderBy("admin, name, email").findList();
				forms.setMaintenanceForm  = filledForm;
				User.flashBrowserId(user);
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
			
			if (query.containsKey("validate")) {
				User.flashBrowserId(user);
				return ok(FormHelper.asJson(filledForm));
			
			} else if (filledForm.hasErrors()) {
				for (String key : filledForm.errors().keySet()) {
					for (ValidationError error : filledForm.errors().get(key)) {
						Logger.debug(key+": "+error.message());
					}
				}

				List<User> users = User.find.orderBy("admin, name, email").findList();
				forms.configureAppearanceForm  = filledForm;
				User.flashBrowserId(user);
				return badRequest(views.html.Administrator.settings.render(forms, users));

			} else {
				String title = Setting.get("appearance.title")+"";
				String titleLink = Setting.get("appearance.titleLink")+"";
				String titleLinkNewWindow = Setting.get("appearance.titleLink.newWindow")+"";
				String landingPage = Setting.get("appearance.landingPage")+"";
				String theme = Application.themeName()+"";

				Administrator.ConfigureAppearanceForm.save(filledForm);

				String successString = "";
				if (!title.equals(Setting.get("appearance.title")))
					successString += " Title changed to \""+Setting.get("appearance.title")+"\" !";
				if (!titleLink.equals(Setting.get("appearance.titleLink")))
					successString += " Title link changed to \""+Setting.get("appearance.titleLink")+"\" !";
				if (!titleLinkNewWindow.equals(Setting.get("appearance.titleLink.newWindow")))
					successString += " Title link will "+ ("true".equals(titleLinkNewWindow)? "not open in a new window anymore" : "now open in a new window") +" !";
				if (!landingPage.equals(Setting.get("appearance.landingPage")))
					successString += " Landing page changed to \""+Setting.get("appearance.landingPage")+"\" !";
				if (!theme.equals(Application.themeName()))
					successString += " Theme changed to "+("".equals(Application.themeName())?"default":"\""+Application.themeName().substring(0, Application.themeName().length()-1)+"\"")+" !";
				if ("".equals(successString))
					successString = "Nothing changed";
				flash("success", successString);
				return redirect(routes.Administrator.getSettings());
			}
		}

		flash("error", "Form not found. Submitted information ignored :(");
		return redirect(routes.Administrator.getSettings());
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
