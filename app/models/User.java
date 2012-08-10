package models;

import play.Logger;
import play.api.libs.Crypto;
import play.db.ebean.Model;

import javax.persistence.*;

import org.codehaus.jackson.JsonNode;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import play.data.Form;
import play.data.format.*;
import play.data.validation.*;
import play.libs.F.Callback;
import play.libs.F.Callback0;
import play.mvc.WebSocket;

@Entity
public class User extends Model {

	// ---------- Static stuff ----------

	public static final Long LINK_TIMEOUT = 24*3600*1000L; // TODO: make as admin setting instead


	// ---------- Instance stuff ----------

	@Id
	@GeneratedValue(strategy=GenerationType.SEQUENCE)
	public Long id;

	@Constraints.Required
	@Formats.NonEmpty
	@Constraints.Email
	public String email;

	@Constraints.MinLength(1)
	@Constraints.Pattern("[^{}\\[\\]();:'\"<>]+") // Avoid breaking JavaScript code in templates
	public String name;

	@Constraints.Required
	@Constraints.MinLength(6)
	public String password;

	@Constraints.Required
	public boolean admin;

	@Constraints.Required
	public boolean active; // Account is deactivated until the user sets a password

	// Crypto.sign(email+passwordLinkSent.getTime()) = uid sent in link
	public Date passwordLinkSent; // Time that the password link was sent

	/**
	 * Constructor
	 */
	public User(String email, String name, String password, boolean admin) {
		this.email = email;
		this.name = name;
		if ("".equals(password)) {
			this.password = "";
			this.active = false;
		} else {
			this.password = Crypto.sign(password);
			this.active = true;
		}
		this.admin = admin;
	}
	
	/**
	 * Try setting the password using the provided activation UID.
	 * 
	 * @param uid
	 * @param password
	 */
	public void activate(String uid, String password) {
		if (getActivationUid().equals(uid)) {
			this.active = true;
			this.password = Crypto.sign(password);
		}
	}

	/**
	 * Generates a new activation UID.
	 */
	public void makeNewActivationUid() {
		this.passwordLinkSent = new Date();
	}

	/**
	 * Gets the activation UID.
	 * @return
	 */
	public String getActivationUid() {
		if (this.passwordLinkSent == null || new Date(new Date().getTime() - LINK_TIMEOUT).after(this.passwordLinkSent)) {
			return null;
		}
		return Crypto.sign(this.email+this.passwordLinkSent.getTime()/1000);
	}

	public String toString() {
		return "User(" + email + ")";
	}

	/**
	 * Encrypts and sets the password.
	 */
	public void setPassword(String password) {
		this.password = Crypto.sign(password);
	}

	// -- Queries

	public static Model.Finder<String,User> find = new Model.Finder<String, User>(String.class, User.class);

	/** Retrieve all users. */
	public static List<User> findAll() {
		return find.all();
	}

	/** Retrieve a User from email. */
	public static User findByEmail(String email) {
		return find.where().eq("email", email).findUnique();
	}

	/** Retrieve a User from id. */
	public static User findById(long id) {
		return find.where().eq("id", id).findUnique();
	}

	/** Authenticate a user. */
	public static User authenticate(String userid, String email, String password) {
		
		Long id = null;
		try {
			id = Long.parseLong(userid);
		} catch (NumberFormatException e) {
			// unparseable id; continue as if nothing happened
		}
		
		if (id == null || id >= 0) {
			try {
				return find.where()
						.eq("email", email)
						.eq("password", password)
						.findUnique();
			} catch (NullPointerException e) {
				// Not found
				return null;
			}
			
		} else {
			if (!"true".equals(models.Setting.get("guest.allowGuests")))
				return null;
			
			User guest = new User("", models.Setting.get("guest.name"), "", false);
			guest.id = id;
			return guest;
		}
	}

	/** Authenticate a user with an unencrypted password */
	public static User authenticateUnencrypted(String email, String password) {
		return authenticate(null, email, Crypto.sign(password));
	}

	/**
	 * Validate a new user.
	 * @param filledForm
	 */
	public static void validateNew(Form<User> filledForm) {
		if (User.findByEmail(filledForm.field("email").valueOr("")) != null)
			filledForm.reject("email", "That e-mail address is already taken");
		
		String adminString = filledForm.field("admin").valueOr("");
		if (!adminString.equals("true") && !adminString.equals("false"))
			filledForm.reject("admin", "The user must either *be* an admin, or *not be* an admin");
		
		// only "name", "email" and "admin" are set during user creation
		filledForm.errors().remove("password");
		filledForm.errors().remove("active");
	}
	
	/**
	 * Validate changes for a user.
	 * @param filledForm
	 * @param user 
	 */
	public void validateChange(Form<User> filledForm, User user) {
		if (!this.email.equals(filledForm.field("email").value()) && User.findByEmail(filledForm.field("email").valueOr("")) != null)
			filledForm.reject("email", "That e-mail address is already taken");
		
		if (!(this.admin + "").equals(filledForm.field("admin").valueOr(""))) {
			String adminString = filledForm.field("admin").valueOr("");
			if (!adminString.equals("true") && !adminString.equals("false"))
				filledForm.reject("admin", "The user must either *be* an admin, or *not be* an admin");
			
			if (this.id.equals(user.id))
				filledForm.reject("admin", "Only other admins can demote you to a normal user, you cannot do it yourself");
		}
	}
	
	public boolean hasChanges(Form<User> filledForm) {
		if (!this.name.equals(filledForm.field("name").valueOr("")))
			return true;
		
		if (!this.email.equals(filledForm.field("email").valueOr("")))
			return true;

		if (!(this.admin + "").equals(filledForm.field("admin").valueOr("")))
			return true;
		
		// "password" and "active" are not changed directly so they are not checked.
		
		return false;
	}
	
	public List<Job> getJobs() {
		return Job.find.where("user = '"+id+"'").findList();
	}
	
	@Override
	public void delete() {
		List<Job> jobs = getJobs();
		for (Job job : jobs)
			job.delete();
		super.delete();
	}
	
}