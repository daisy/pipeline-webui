package controllers;

import models.NotificationConnection;
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
		
		return NotificationConnection.createWebSocket(user.id, browserId);
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
		
		return ok(NotificationConnection.pullJson(user.id, browserId));
	}
	
}
