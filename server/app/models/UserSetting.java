package models;

import java.util.HashMap;
import java.util.Map;
import play.db.ebean.*;

import javax.persistence.*;

import controllers.Application;

import play.data.validation.*;

@Entity(name="usersetting")
@Table(name="usersetting")
public class UserSetting extends Model {
	private static final long serialVersionUID = 1L;
	
	@Id
	@Constraints.Required
	public String id;
	
	@Constraints.Required
	@Column(name="user_id") public Long user;
	
	@Constraints.Required
	public String name;
	
	public String value;
	
	// -- Queries
    public static Model.Finder<String,UserSetting> find = new Model.Finder<String, UserSetting>(Application.datasource, String.class, UserSetting.class);
    
    @Transient
    private static Map<String,String> cache = new HashMap<String,String>();
    
    /** Get the value of a setting */
    public static String get(Long user, String name) {
    	if (user < -2) user = -2L;
    	synchronized (cache) {
    		if (cache.containsKey(name))
        		return cache.get(name);
		}
    	
    	UserSetting setting = find.where().eq("user", user).eq("name", name).findUnique();
    	if (setting == null)
    		return null;
    	return setting.value;
    }
    
    /** Set the value of a setting. If value is null, the setting is deleted. */
    public static void set(Long user, String name, String value) {
    	if (user < -2) user = -2L;
    	UserSetting setting = find.where().eq("user", user).eq("name", name).findUnique();
    	if (setting == null) {
    		if (value == null)
    			return;
    		setting = new UserSetting();
    		setting.user = user;
    		setting.name = name;
    	}
		setting.value = value;
    	if (value == null)
    		setting.delete(Application.datasource);
    	else
    		setting.save(Application.datasource);
    	
    	// Cache settings
    	synchronized (cache) {
    		cache.put(name, value);
    	}
   }

}