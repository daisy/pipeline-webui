package models;

import play.api.libs.Crypto;
import play.db.ebean.Model;

import javax.persistence.*;

import java.util.*;
import play.data.format.*;
import play.data.validation.*;

@Entity
public class User extends Model {
    
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
    
    /**
     * Constructor
     */
    public User(String email, String name, String password, boolean admin) {
    	this.email = email;
    	this.name = name;
    	this.password = Crypto.sign(password);
    	this.admin = admin;
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
    public static User authenticate(String email, String password) {
    	// TODO: what if running in free-for-all mode?
    	
    	try {
    		return find.where()
			            .eq("email", email)
			            .eq("password", password)
			            .findUnique();
    	} catch (NullPointerException e) {
    		// Not found
    		return null;
    	}
    }
    
    /** Authenticate a user with an unencrypted password */
    public static User authenticateUnencrypted(String email, String password) {
    	return authenticate(email, Crypto.sign(password));
    }
    
}