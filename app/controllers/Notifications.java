package controllers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import models.Notification;
import models.User;

import org.codehaus.jackson.JsonNode;

import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.WebSocket;

public class Notifications extends Controller {

	/**
	 * Handle WebSocket pushing.
	 */
	public static WebSocket<JsonNode> websocket(Long browserId) {
		if (FirstUse.isFirstUse())
    		return null; // forbidden
		
		User user = User.authenticate(session("userid"), session("email"), session("password"));
		if (user == null)
			return null; // forbidden
		
		synchronized (User.notificationQueues) {
			User.notificationQueues.putIfAbsent(user.id, new HashMap<Long,List<Notification>>());
			if (!User.notificationQueues.get(user.id).containsKey(browserId))
				User.notificationQueues.get(user.id).put(browserId, new ArrayList<Notification>());
		}
		
		return user.addWebSocket(browserId);
	}
	
	/**
	 * Handle XHR polling.
	 * @return
	 */
	public static Result xhr(Long browserId) {
		if (FirstUse.isFirstUse())
			return forbidden();
		
		User user = User.authenticate(session("userid"), session("email"), session("password"));
		if (user == null)
			return forbidden();
		
		List<JsonNode> result = new ArrayList<JsonNode>();
		synchronized (User.notificationQueues) {
			User.notificationQueues.putIfAbsent(user.id, new HashMap<Long,List<Notification>>());
			if (!User.notificationQueues.get(user.id).containsKey(browserId))
				User.notificationQueues.get(user.id).put(browserId, new ArrayList<Notification>());
			
			for (Notification n : User.notificationQueues.get(user.id).get(browserId)) {
				result.add(n.toJson());
			}
			User.notificationQueues.get(user.id).get(browserId).clear();
		}
		
		return ok(play.libs.Json.toJson(result));
	}

}
