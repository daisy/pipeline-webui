package models;

import java.util.Arrays;
import java.util.List;
import play.db.ebean.Model;

import javax.persistence.*;

import controllers.Application;

import play.data.validation.*;
import utils.ObfuscatedString;

@Entity
public class Setting extends Model {
	private static final long serialVersionUID = 1L;

	@Id
    @Constraints.Required
    public String name;
    
    public String value;
    
    public static final List<String> obfuscatedSettings = Arrays.asList("dp2ws.secret", "mail.password");
    
    // -- Queries
    
    public static Model.Finder<String,Setting> find = new Model.Finder<String, Setting>(Application.datasource, String.class, Setting.class);
    
    /** Get the value of a setting */
    public static String get(String name) {
    	Setting setting = find.where().eq("name", name).findUnique();
    	if (setting == null)
    		return null;
    	if (obfuscatedSettings.contains(name))
    		return ObfuscatedString.unobfuscate(setting.value);
    	return setting.value;
    }
    
    /** Set the value of a setting. If value is null, the setting is deleted. */
    public static void set(String name, String value) {
    	Setting setting = find.where().eq("name", name).findUnique();
    	if (setting == null) {
    		if (value == null)
    			return;
    		setting = new Setting();
    		setting.name = name;
    	}
    	if (obfuscatedSettings.contains(name))
    		setting.value = ObfuscatedString.obfuscate(value);
    	else
    		setting.value = value;
    	if (value == null)
    		setting.delete(Application.datasource);
    	else
    		setting.save(Application.datasource);
    }
    
}