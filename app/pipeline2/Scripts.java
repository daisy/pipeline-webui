package pipeline2;

public class Scripts {

	/**
	 * Get all scripts
	 * 
	 * HTTP 200 OK: Response body contains XML data
	 * HTTP 401 Unauthorized: Client was not authorized to perform request.
	 */
	public static Pipeline2WSResponse get(String endpoint, String username, String secret) {
		return Pipeline2WS.get(endpoint, "/scripts", username, secret, null);
	}

	/**
	 * Get a single script
	 * 
	 * HTTP 200 OK: Response body contains XML data
	 * HTTP 401 Unauthorized: Client was not authorized to perform request.
	 * HTTP 404 Not Found: Resource not found
	 */
	public static Pipeline2WSResponse get(String endpoint, String username, String secret, String id) {
		return Pipeline2WS.get(endpoint, "/scripts/"+id, username, secret, null);
	}

}
