package pipeline2;

import java.io.InputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import play.Logger;

public class Pipeline2WSResponse {
	
	public int status;
	public String statusName;
	public String statusDescription;
	private InputStream body;
	private Document xml;
	
	public Pipeline2WSResponse(int status, String statusName, String statusDescription, InputStream body) {
		this.status = status;
		this.statusName = statusName;
		this.statusDescription = statusDescription;
		this.body = body;
		this.xml = null;
	}
	
	public InputStream asStream() {
		return body;
	}
	
	public Document asXml() {
		if (xml != null)
			return xml;
		
		if (body == null)
			return null;
		
		DocumentBuilderFactory factory = null;
		DocumentBuilder builder = null;
		
		try {
			factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			builder = factory.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			throw new RuntimeException(e);
		}
		
		try {
			InputSource is = new InputSource(body);
			is.setEncoding("utf-8");
			xml = builder.parse(is);
		} catch (Exception e) {
			Logger.warn(e.getMessage());
			xml = null;
			// Should be safe to silently ignore this one, I think this happens when there is no content in the HTTP response body, or the body does not contain valid XML.
		}
		
		return xml;
	}
	
}
