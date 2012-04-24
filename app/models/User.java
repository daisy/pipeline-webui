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
    @Constraints.Required
    @Formats.NonEmpty
    @Constraints.Email
    public String email;
    
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
    
    // -- Queries
    
    public static Model.Finder<String,User> find = new Model.Finder(String.class, User.class);
    
 	/** Retrieve all users. */
    public static List<User> findAll() {
        return find.all();
    }
	
    /** Retrieve a User from email. */
    public static User findByEmail(String email) {
        return find.where().eq("email", email).findUnique();
    }
    
    /** Authenticate a user. */
    public static User authenticate(String email, String password) {
    	// TODO: what if running in free-for-all mode?
    	
    	User user;
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
    
    public String toString() {
        return "User(" + email + ")";
    }
    
}