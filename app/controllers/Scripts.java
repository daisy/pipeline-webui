package controllers;

import java.util.ArrayList;
import java.util.List;

import models.Setting;
import models.User;

import org.w3c.dom.Node;

import pipeline2.Pipeline2WS;
import pipeline2.Pipeline2WSResponse;
import pipeline2.models.Script;
import pipeline2.models.script.*;
import play.Logger;
import play.libs.XPath;
import play.mvc.*;
import utils.XML;

public class Scripts extends Controller {
	
	public static Result getScripts() {
		if (FirstUse.isFirstUse())
    		return redirect(routes.FirstUse.getFirstUse());
		
		User user = User.authenticate(session("userid"), session("email"), session("password"));
		if (user == null)
			return redirect(routes.Login.login());
		
		Pipeline2WSResponse response = pipeline2.Scripts.get(Setting.get("dp2ws.endpoint"), Setting.get("dp2ws.authid"), Setting.get("dp2ws.secret"));
		
		if (response.status != 200) {
			return Application.error(response.status, response.statusName, response.statusDescription, response.asText());
		}
		
		List<Script> scripts = Script.getScripts(response);
		
		return ok(views.html.Scripts.getScripts.render(scripts));
	}
	
	public static Result getScript(String id) {
		if (FirstUse.isFirstUse())
    		return redirect(routes.FirstUse.getFirstUse());
		
		User user = User.authenticate(session("userid"), session("email"), session("password"));
		if (user == null)
			return redirect(routes.Login.login());
		
		Pipeline2WSResponse response = pipeline2.Scripts.get(Setting.get("dp2ws.endpoint"), Setting.get("dp2ws.authid"), Setting.get("dp2ws.secret"), id);
		
		if (response.status != 200) {
			return Application.error(response.status, response.statusName, response.statusDescription, response.asText());
		}
		
		Script script = new Script(response);
		
		boolean uploadFiles = false;
		boolean hideAdvancedOptions = "true".equals(Setting.get("jobs.hideAdvancedOptions"));
		for (Argument arg : script.arguments) {
			if ("input".equals(arg.kind) || "anyFileURI".equals(arg.xsdType)) {
				uploadFiles = true;
			}
			if (hideAdvancedOptions && arg.required == false)
				arg.hide = true;
		}
		if (hideAdvancedOptions) {
			boolean hasHiddenOptions = false;
			for (Argument arg : script.arguments) {
				if (arg.hide) {
					hasHiddenOptions = true;
					break;
				}
			}
			if (!hasHiddenOptions)
				hideAdvancedOptions = false; // don't show "hide advanced options" control, if there are no advanced options
		}
		
		return ok(views.html.Scripts.getScript.render(script, uploadFiles, hideAdvancedOptions));

	}

}