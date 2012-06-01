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

public class Scripts extends Controller {
	
	public static Result getScripts() {
		if (FirstUse.isFirstUse())
    		return redirect(routes.FirstUse.getFirstUse());
		
		User user = User.authenticate(session("email"), session("password"));
		if (user == null)
			return redirect(routes.Login.login());
		
		Pipeline2WSResponse scripts = pipeline2.Scripts.get(Setting.get("dp2ws.endpoint"), Setting.get("dp2ws.authid"), Setting.get("dp2ws.secret"));
		
		if (scripts.status != 200) {
			return Application.error(scripts.status, scripts.statusName, scripts.statusDescription, "");
		}
		
		List<List<String>> scriptList = new ArrayList<List<String>>();

		List<Node> scriptNodes = XPath.selectNodes("//d:script", scripts.asXml(), Pipeline2WS.ns);
		for (Node scriptNode : scriptNodes) {
			List<String> row = new ArrayList<String>();
			row.add(XPath.selectText("@href", scriptNode, Pipeline2WS.ns));
			row.add(XPath.selectText("d:nicename", scriptNode, Pipeline2WS.ns));
			row.add(XPath.selectText("d:description", scriptNode, Pipeline2WS.ns));
			scriptList.add(row);
		}

		return ok(views.html.Scripts.getScripts.render(scriptList));
	}
	
	public static Result getScript(String id) {
		if (FirstUse.isFirstUse())
    		return redirect(routes.FirstUse.getFirstUse());
		
		User user = User.authenticate(session("email"), session("password"));
		if (user == null)
			return redirect(routes.Login.login());
		
		Pipeline2WSResponse wsScript = pipeline2.Scripts.get(Setting.get("dp2ws.endpoint"), Setting.get("dp2ws.authid"), Setting.get("dp2ws.secret"), id);

		if (wsScript.status != 200) {
			return Application.error(wsScript.status, wsScript.statusName, wsScript.statusDescription, "");
		}
		
		Script script = new Script(wsScript.asXml());
		
		boolean uploadFiles = false;
		for (Argument arg : script.arguments) {
			if ("input".equals(arg.kind) || "anyFileURI".equals(arg.xsdType)) {
				uploadFiles = true;
				break;
			}
		}
		
		return ok(views.html.Scripts.getScript.render(script, uploadFiles));

	}

}