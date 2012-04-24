package models;

import java.util.List;

import play.db.ebean.Model;

import javax.persistence.*;

import play.data.format.*;
import play.data.validation.*;

@Entity
public class Setting extends Model {
    
	@Id
    @Constraints.Required
    public String name;
    
    public String value;
    
    
    // -- Queries
    
    public static Model.Finder<String,Setting> find = new Model.Finder(String.class, Setting.class);
    
    /** Get the value of a setting */
    public static String get(String name) {
    	Setting setting = find.where().eq("name", name).findUnique();
    	return setting != null ? setting.value : null;
    }
    
    /** Set the value of a setting */
    public static void set(String name, String value) {
    	Setting setting = find.where().eq("name", name).findUnique();
    	if (setting == null) {
    		setting = new Setting();
    		setting.name = name;
    	}
    	setting.value = value;
    	setting.save();
    }
    
}