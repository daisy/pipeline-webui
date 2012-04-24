package controllers;

import java.util.ArrayList;
import java.util.List;

import models.Setting;
import models.User;

import org.w3c.dom.Node;

import pipeline2.Pipeline2WS;
import pipeline2.Pipeline2WSResponse;
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
		
		Pipeline2WSResponse script = pipeline2.Scripts.get(Setting.get("dp2ws.endpoint"), Setting.get("dp2ws.authid"), Setting.get("dp2ws.secret"), id);

		if (script.status != 200) {
			return Application.error(script.status, script.statusName, script.statusDescription, "");
		}
		
		String href = XPath.selectText("/d:script/@href", script.asXml(), Pipeline2WS.ns);
		String scriptId = XPath.selectText("/d:script/@id", script.asXml(), Pipeline2WS.ns);
		String nicename = XPath.selectText("/d:script/d:nicename", script.asXml(), Pipeline2WS.ns);
		String description = XPath.selectText("/d:script/d:description", script.asXml(), Pipeline2WS.ns);
		String homepage = XPath.selectText("/d:script/d:homepage", script.asXml(), Pipeline2WS.ns);

		List<List<String>> inputList = new ArrayList<List<String>>();
		List<List<String>> optionList = new ArrayList<List<String>>();
		List<List<String>> outputList = new ArrayList<List<String>>();

		List<Node> inputNodes = XPath.selectNodes("/d:script/d:input", script.asXml(), Pipeline2WS.ns);
		List<Node> optionNodes = XPath.selectNodes("/d:script/d:option", script.asXml(), Pipeline2WS.ns);
		List<Node> outputNodes = XPath.selectNodes("/d:script/d:output", script.asXml(), Pipeline2WS.ns);

		for (Node inputNode : inputNodes) {
			List<String> row = new ArrayList<String>();
			row.add(XPath.selectText("@name", inputNode, Pipeline2WS.ns).replaceAll("\"", "'").replaceAll("\\n", " "));
			row.add(XPath.selectText("@desc", inputNode, Pipeline2WS.ns).replaceAll("\"", "'").replaceAll("\\n", " "));
			row.add(XPath.selectText("@sequence", inputNode, Pipeline2WS.ns).replaceAll("\"", "'").replaceAll("\\n", " "));
			row.add(XPath.selectText("@mediaType", inputNode, Pipeline2WS.ns).replaceAll("\"", "'").replaceAll("\\n", " "));
			inputList.add(row);
		}

		for (Node optionNode : optionNodes) {
			List<String> row = new ArrayList<String>();
			row.add(XPath.selectText("@name", optionNode, Pipeline2WS.ns).replaceAll("\"", "'").replaceAll("\\n", " "));
			row.add(XPath.selectText("@desc", optionNode, Pipeline2WS.ns).replaceAll("\"", "'").replaceAll("\\n", " "));
			row.add(XPath.selectText("@required", optionNode, Pipeline2WS.ns).replaceAll("\"", "'").replaceAll("\\n", " "));
			row.add(XPath.selectText("@type", optionNode, Pipeline2WS.ns).replaceAll("\"", "'").replaceAll("\\n", " "));
			row.add(XPath.selectText("@mediaType", optionNode, Pipeline2WS.ns).replaceAll("\"", "'").replaceAll("\\n", " "));
			optionList.add(row);
		}

		for (Node outputNode : outputNodes) {
			List<String> row = new ArrayList<String>();
			row.add(XPath.selectText("@name", outputNode, Pipeline2WS.ns).replaceAll("\"", "'").replaceAll("\\n", " "));
			row.add(XPath.selectText("@desc", outputNode, Pipeline2WS.ns).replaceAll("\"", "'").replaceAll("\\n", " "));
			row.add(XPath.selectText("@sequence", outputNode, Pipeline2WS.ns).replaceAll("\"", "'").replaceAll("\\n", " "));
			row.add(XPath.selectText("@mediaType", outputNode, Pipeline2WS.ns).replaceAll("\"", "'").replaceAll("\\n", " "));
			outputList.add(row);
		}
		
		return ok(views.html.Scripts.getScript.render(href, scriptId, nicename, description, homepage, inputList, optionList, outputList));

	}

}