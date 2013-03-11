package controllers;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.daisy.pipeline.client.Pipeline2WSException;
import org.daisy.pipeline.client.Pipeline2WSResponse;

import models.User;

import play.mvc.*;

public class Alive extends Controller {

	/**
	 * GET /alive
	 * Allows the browser to ping the Web UI, as well as retrieve the local server time.
	 * @return
	 */
	public static Result alive() {
		return ok(play.libs.Jsonp.jsonp("alive", play.libs.Json.toJson(new Date())));
	}
	
	/**
	 * Convenience method for statusMap(Map<String,String[]>)
	 * @param query
	 * @return
	 */
	public static Map<String,Object> statusMap(String name, String value) {
		Map<String,String[]> query = new HashMap<String,String[]>();
    	query.put(name, new String[]{value});
    	return Alive.statusMap(query);
	}
	public static Map<String,Object> statusMap(Map<String,String[]> query) {
		Map<String,Object> status = new HashMap<String,Object>();
		status.put("time", new Date());
		status.put("deployment", Application.deployment());
		status.put("engine.state", Application.getPipeline2EngineState());
		status.put("engine", Application.alive);
		status.put("theme", Application.themeName());
		status.put("version", Application.version);

		User user = User.authenticate(request(), session());
		if (FirstUse.isFirstUse() || user != null && user.admin) {
			status.put("datasource", Application.datasource);
			Map<String,String[]> queryString = query != null ? query : request().queryString();
			for (String key : queryString.keySet()) {
				String value = queryString.get(key)[0];

				if ("probeEngine".equals(key)) {
					status.put("probeEngine.url", value);
					
					URL url = null;
					try {
						url = new URL(value);
						
					} catch (MalformedURLException malformedURLException) {
						// Note that not *all* URLs are caught here, but it should be good enough for our purposes:
						// http://stackoverflow.com/a/5965755/281065
						Map<String,Object> alive = new HashMap<String,Object>();
						alive.put("error", true);
						alive.put("message", "Invalid URL");
						status.put("probeEngine", alive);
					}

					if (url != null) {
						try {
							Pipeline2WSResponse response = org.daisy.pipeline.client.Alive.get(value);
	
							if (response.status == 200) {
								org.daisy.pipeline.client.models.Alive alive = new org.daisy.pipeline.client.models.Alive(response);
								status.put("probeEngine", alive);
	
							} else {
								Map<String,Object> alive = new HashMap<String,Object>();
								alive.put("error", true);
								alive.put("message", response.status+" - "+response.statusName+": "+response.statusDescription);
								status.put("probeEngine", alive);
							}
	
						} catch (Pipeline2WSException e) {
							Map<String,Object> alive = new HashMap<String,Object>();
							alive.put("error", true);
							alive.put("message", "500 - Something unexpected occured while communicating with the Pipeline 2 framework");
							status.put("probeEngine", alive);
						}
					}
				}
			}
		}
		
		return status;
	}
	
	public static Result status() {
		return ok(play.libs.Json.toJson(statusMap(null)));
	}

}
