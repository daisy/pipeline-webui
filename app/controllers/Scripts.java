package controllers;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;

import org.daisy.pipeline.client.models.Argument;
import org.daisy.pipeline.client.models.Script;

import models.User;
import models.UserSetting;
import play.Logger;
import play.mvc.*;
import utils.Pair;

public class Scripts extends Controller {

	public static Result getScriptsJson() {
		if (FirstUse.isFirstUse())
			return unauthorized("unauthorized");

		User user = User.authenticate(request(), session());
		if (user == null)
			return unauthorized("unauthorized");
		
		List<Script> scripts = get();
		if (scripts == null) {
			scripts = new ArrayList<Script>();
		}
		for (int i = scripts.size()-1; i >= 0; i--) {
			if ("false".equals(UserSetting.get(-2L, "scriptEnabled-"+scripts.get(i).getId()))) {
				scripts.remove(i);
			}
		}
		
		JsonNode scriptsJson = play.libs.Json.toJson(scripts);
		return ok(scriptsJson);
	}
	
	public static Result getScriptJson(String id) {
		if (FirstUse.isFirstUse())
			return unauthorized("unauthorized");

		User user = User.authenticate(request(), session());
		if (user == null)
			return unauthorized("unauthorized");
		
		if ("false".equals(UserSetting.get(-2L, "scriptEnabled-"+id))) {
			return forbidden();
		}
		
		Script script = get(id);
		
		if (script != null) {
			JsonNode scriptJson = play.libs.Json.toJson(script);
			return ok(scriptJson);
		} else {
			return internalServerError("An error occured while trying to retrieve the script '"+id+"'.");
		}
	}

	public static class ScriptForm {

		//public Script script;
		public Map<String,List<String>> errors;

		public String guestEmail;

		//                                                          kind    position  part      name
		private static final Pattern PARAM_NAME = Pattern.compile("^([A-Za-z]+)(\\d*)([A-Za-z]*?)-(.*)$");

		public ScriptForm(Long userId, Script script, Map<String, String[]> params) {
			//this.script = script;

			// Parse all arguments
			for (String param : params.keySet()) {
				Matcher matcher = PARAM_NAME.matcher(param);
				if (!matcher.find()) {
					Logger.debug("Unable to parse argument parameter: "+param);
				} else {
					String kind = matcher.group(1);
					String name = matcher.group(4);
					Logger.debug("script form: "+kind+": "+name);

					Argument argument = script.getArgument(name);
					if (argument == null) {
						Logger.debug("'"+name+"' is not an argument for the script '"+script.getId()+"'; ignoring it");
						continue;
					}

					for (int i = 0; i < params.get(param).length; i++) {
						argument.add(params.get(param)[i]);
					}
				}
			}

			if (userId < 0 && params.containsKey("guest-email"))
				this.guestEmail = params.get("guest-email")[0];

			this.errors = new HashMap<String, List<String>>();
		}

		public void validate() {
			if (guestEmail != null && !"".equals(guestEmail) && !guestEmail.matches("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,4}$")) {
				addError("guest-email", "Please enter a valid e-mail address.");
			}

			// TODO: validate arguments
		}

		public boolean hasErrors() {
			return errors.size() > 0;
		}

		public void addError(String field, String error) {
			if (!errors.containsKey(field))
				errors.put(field, new ArrayList<String>());
			errors.get(field).add(error);
		}
	}
	
	private static Map<String, Pair<Script, Date>> scriptCache = new HashMap<String, Pair<Script, Date>>();
	private static List<Script> scriptList = new ArrayList<Script>();
	private static Date scriptListCacheLastUpdate = new Date();
	public static Script get(String scriptId) { return get(false, scriptId); }
	public static Script get(boolean forceUpdate, String scriptId) {
		Pair<Script, Date> scriptAndDate = scriptCache.get(scriptId);
		if (forceUpdate || scriptAndDate == null || scriptAndDate.b.before(new Date(new Date().getTime() - 1000*60*5))) {
			// not in cache or cache more than 5 minutes old
			Script script = Application.ws.getScript(scriptId);
			if (script == null) {
				scriptCache.remove(script);
			} else {
				scriptCache.put(scriptId, new Pair<Script, Date>(script, new Date()));
			}
			return script;
			
		} else {
			return scriptAndDate.a;
		}
	}
	public static List<Script> get() { return get(false); }
	public static List<Script> get(boolean forceUpdate) {
		if (forceUpdate || scriptList == null || scriptList.isEmpty() || scriptListCacheLastUpdate.before(new Date(new Date().getTime() - 1000*60*5))) {
			// no scripts in cache or cache more than 5 minutes old
			scriptList = Application.ws.getScripts();
			scriptListCacheLastUpdate = new Date();
		}
		return scriptList;
	}

}
