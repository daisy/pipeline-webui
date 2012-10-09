package controllers;

import java.util.Date;

import play.mvc.*;

public class Alive extends Controller {
	
	/**
	 * GET /account
	 * Show information about the user, and a form letting the user change their details.
	 * @return
	 */
	public static Result alive() {
		return ok(play.libs.Jsonp.jsonp("alive", play.libs.Json.toJson(new Date())));
	}
	
}
