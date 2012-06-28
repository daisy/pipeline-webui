package models;

import java.util.Arrays;
import java.util.List;
import play.db.ebean.Model;

import javax.persistence.*;

import play.data.validation.*;
import utils.ObfuscatedString;

@Entity
public class Setting extends Model {
    
	@Id
    @Constraints.Required
    public String name;
    
    public String value;
    
    public static final List<String> obfuscatedSettings = Arrays.asList("dp2ws.secret", "mail.password");
    
    // -- Queries
    
    public static Model.Finder<String,Setting> find = new Model.Finder(String.class, Setting.class);
    
    /** Get the value of a setting */
    public static String get(String name) {
    	Setting setting = find.where().eq("name", name).findUnique();
    	if (setting == null)
    		return null;
    	if (obfuscatedSettings.contains(name))
    		return ObfuscatedString.unobfuscate(setting.value);
    	return setting.value;
    }
    
    /** Set the value of a setting */
    public static void set(String name, String value) {
    	Setting setting = find.where().eq("name", name).findUnique();
    	if (setting == null) {
    		setting = new Setting();
    		setting.name = name;
    	}
    	if (obfuscatedSettings.contains(name))
    		setting.value = ObfuscatedString.obfuscate(value);
    	else
    		setting.value = value;
    	setting.save();
    }
    
}