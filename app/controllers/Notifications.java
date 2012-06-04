package controllers;

import java.util.ArrayList;
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
	public static WebSocket<JsonNode> websocket() {
		if (FirstUse.isFirstUse())
    		return null; // forbidden
		
		User user = User.authenticate(session("email"), session("password"));
		if (user == null)
			return null; // forbidden
		
		User.notificationQueues.putIfAbsent(user.id, new ArrayList<Notification>());
		
		return user.addWebSocket();
		
	}

	/**
	 * Handle XHR polling.
	 * @return
	 */
	public static Result xhr() {
		if (FirstUse.isFirstUse())
    		return null; // forbidden
		
		User user = User.authenticate(session("email"), session("password"));
		if (user == null)
			return null; // forbidden
		
		User.notificationQueues.putIfAbsent(user.id, new ArrayList<Notification>());
		
		List<JsonNode> result = new ArrayList<JsonNode>();
		synchronized (User.notificationQueues) {
			for (Notification n : User.notificationQueues.get(user.id)) {
				result.add(n.toJson());
			}
			User.notificationQueues.get(user.id).clear();
		}
		
		return ok(play.libs.Json.toJson(result));
	}

}
