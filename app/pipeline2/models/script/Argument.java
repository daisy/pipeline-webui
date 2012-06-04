package pipeline2.models.script;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

/** A script argument. */
public class Argument {
	/** The name of the argument. This isn't necessarily unique; since inputs and options can have the same name. */ 
	public String name;
	
	/** This is the value from the px:role="name" in the script documentation. */
	public String nicename;
	
	/** A description of the option. */
	public String desc;
	
	/** whether or not this argument is required */
	public boolean required;
	
	/** whether or not multiple selections can be made */
	public boolean sequence;
	
	/** whether or not the ordering matters (only relevant if sequence==true) */
	public boolean ordered;
	
	/** MIME types accepted (only relevant if type=anyDirURI or anyFileURI) */
	public List<String> mediaTypes;
	
	/** Only relevant for file arguments. If mediaTypeBlacklist is defined, then all XML files are supported for this argument, except those listed in this list. */
	public List<String> mediaTypeBlacklist;
	
	/** XSD type */
	public String xsdType = "";
	
	/** Arguments with a output value of "result" or "temp" will only be included when the framework is running in local mode. */
	public String output;
	
	/** Type of underlying argument. Either "input", "parameters", "option" or "output". */
	public String kind;
	
	public Argument() {
		this.mediaTypes = new ArrayList<String>();
	}
	
	public Argument(Argument arg) {
		this.name = arg.name;
		this.nicename = arg.nicename;
		this.desc = arg.desc;
		this.required = arg.required;
		this.sequence = arg.sequence;
		this.ordered = arg.ordered;
		this.mediaTypes = new ArrayList<String>();
		for (String mediaType : arg.mediaTypes) {
			this.mediaTypes.add(mediaType);
		}
		this.xsdType = arg.xsdType;
		this.output = arg.output;
		this.kind = arg.kind;
	}
	
	public Node asDocumentElement(Document document) {
		return null; // Must be overridden!
		
//		for (String inputPort : inputs.keySet()) {
//			element = jobRequestDocument.createElement("input");
//			element.setAttribute("name", inputPort);
//
//			for (String src : inputs.get(inputPort)) {
//				Element fileElement = jobRequestDocument.createElement("file");
//				fileElement.setAttribute("src", src);
//				element.appendChild(fileElement);
//			}
//
//			jobRequest.appendChild(element);
//		}
//
//		for (String option : options.keySet()) {
//			element = jobRequestDocument.createElement("option");
//			element.setAttribute("name", option);
//			element.setTextContent(options.get(option));
//			jobRequest.appendChild(element);
//		}
	}
	
}