package pipeline2;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.management.RuntimeErrorException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.io.FileUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import play.Logger;


public class Jobs {

//	public final static String JOB_REQUEST_NAMESPACE = "http://www.daisy.org/ns/pipeline/data";

	/**
	 * Get all jobs
	 * 
	 * HTTP 200 OK: Response body contains XML data
	 * HTTP 401 Unauthorized: Client was not authorized to perform request.
	 */
	public static Pipeline2WSResponse get(String endpoint, String username, String secret) {
		return Pipeline2WS.get(endpoint, "/jobs", username, secret);
	}

	/**
	 * Helper method for the two post(...) methods that creates the job request document
	 * 
	 * @param href
	 * @param options
	 * @param inputs
	 * @return
	 */
	private static Document createJobRequestDocument(String href, Map<String, String> options, Map<String, List<String>> inputs) {
		Document jobRequestDocument = utils.XML.getXml("<jobRequest xmlns='http://www.daisy.org/ns/pipeline/data'/>");
		Element jobRequest = jobRequestDocument.getDocumentElement();

		Element element = jobRequestDocument.createElement("script");
		element.setAttribute("href", href);
		jobRequest.appendChild(element);

		for (String inputPort : inputs.keySet()) {
			element = jobRequestDocument.createElement("input");
			element.setAttribute("name", inputPort);

			for (String src : inputs.get(inputPort)) {
				Element fileElement = jobRequestDocument.createElement("file");
				fileElement.setAttribute("src", src);
				element.appendChild(fileElement);
			}

			jobRequest.appendChild(element);
		}

		for (String option : options.keySet()) {
			element = jobRequestDocument.createElement("option");
			element.setAttribute("name", option);
			element.setTextContent(options.get(option));
			jobRequest.appendChild(element);
		}
		
		return jobRequestDocument;
	}

	/**
	 * Create a job without files
	 * 
	 * HTTP 201 Created: The URI of the new job is found in the HTTP location header
	 * HTTP 400 Bad Request: Errors in the parameters such as invalid script name
	 * HTTP 401 Unauthorized: Client was not authorized to perform request.
	 * @return 
	 */
	public static Pipeline2WSResponse post(String endpoint, String username, String secret, final String href, final Map<String,String> options, final Map<String,List<String>> inputs) {
		Document jobRequestDocument = createJobRequestDocument(href, options, inputs);
		return Pipeline2WS.postXml(endpoint, "/jobs", username, secret, jobRequestDocument);
	}

	/**
	 * Create a job with files
	 * 
	 * HTTP 201 Created: The URI of the new job is found in the HTTP location header
	 * HTTP 400 Bad Request: Errors in the parameters such as invalid script name
	 * HTTP 401 Unauthorized: Client was not authorized to perform request.
	 * @return 
	 */
	public static Pipeline2WSResponse post(String endpoint, String username, String secret, final String href, final Map<String,String> options, final Map<String,List<String>> inputs, final File contextZipFile) {
		
		Document jobRequestDocument = createJobRequestDocument(href, options, inputs);
		File jobRequestFile = null;
		try {
			jobRequestFile = File.createTempFile("jobRequest", ".xml");

			StringWriter writer = new StringWriter();
			Transformer transformer = TransformerFactory.newInstance().newTransformer();
			transformer.transform(new DOMSource(jobRequestDocument), new StreamResult(writer));
			FileUtils.writeStringToFile(jobRequestFile, writer.toString());
			
		} catch (IOException e) {
			Logger.error("Could not create and/or write to temporary jobRequest file", e);
			throw new RuntimeErrorException(new Error(e), "Could not create and/or write to temporary jobRequest file");
		} catch (TransformerConfigurationException e) {
			Logger.error("Could not serialize jobRequest XML", e);
			throw new RuntimeErrorException(new Error(e), "Could not serialize jobRequest XML");
		} catch (TransformerFactoryConfigurationError e) {
			Logger.error("Could not serialize jobRequest XML", e);
			throw new RuntimeErrorException(new Error(e), "Could not serialize jobRequest XML");
		} catch (TransformerException e) {
			Logger.error("Could not serialize jobRequest XML", e);
			throw new RuntimeErrorException(new Error(e), "Could not serialize jobRequest XML");
		}
		
		Map<String,File> parts = new HashMap<String,File>();
		parts.put("job-request", jobRequestFile);
		parts.put("job-data", contextZipFile);
		
		return Pipeline2WS.postMultipart(endpoint, "/jobs", username, secret, parts);
		
	}

	/**
	 * Get a single job
	 * 
	 * HTTP 200 OK: Response body contains XML data
	 * HTTP 401 Unauthorized: Client was not authorized to perform request.
	 * HTTP 404 Not Found: Resource not found
	 */
	public static Pipeline2WSResponse get(String endpoint, String username, String secret, String id) {
		return Pipeline2WS.get(endpoint, "/jobs/"+id, username, secret);
	}

	/**
	 * Delete a single job
	 * 
	 * HTTP 204 No Content: Successfully processed the request, no content being returned
	 * HTTP 401 Unauthorized: Client was not authorized to perform request.
	 * HTTP 404 Not Found: Resource not found
	 */
	public static Pipeline2WSResponse delete(String endpoint, String username, String secret, String id) {
		return null; //TODO Pipeline2WS.delete(endpoint, "/jobs/"+id, username, secret);
	}

	/**
	 * Get the result for a job
	 * 
	 * HTTP 200 OK: Response body contains Zip data
	 * HTTP 401 Unauthorized: Client was not authorized to perform request.
	 * HTTP 404 Not Found: Resource not found
	 * @return 
	 */
	public static Pipeline2WSResponse getResult(String endpoint, String username, String secret, String id) {
		return Pipeline2WS.get(endpoint, "/jobs/"+id+"/result", username, secret);
	}

	/**
	 * Get the log file for a job
	 * 
	 * HTTP 200 OK: Response body contains plain text data
	 * HTTP 401 Unauthorized: Client was not authorized to perform request.
	 * HTTP 404 Not Found: Resource not found
	 */
	public static Pipeline2WSResponse getLog(String endpoint, String username, String secret, String id) {
		return Pipeline2WS.get(endpoint, "/jobs/"+id+"/log", username, secret);
	}

}
