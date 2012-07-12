package pipeline2.models.script.arguments;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import pipeline2.models.script.Argument;

public class ArgFiles extends Argument {
	
	public List<String> hrefs = new ArrayList<String>();
	
	public ArgFiles(Argument arg) {
		super(arg);
	}
	
	/** For chaining 
	 * @return */
	public ArgFiles add(String href) {
		hrefs.add(href);
		return this;
	}
	
	public Element asDocumentElement(Document document) {
		Element element;
		if ("option".equals(kind))
			element = document.createElement("option");
		else
			element = document.createElement("input");
		
		element.setAttribute("name", name);
		
		for (String href : hrefs) {
			Element item = document.createElement("item");
			item.setAttribute("value", href+"");
			element.appendChild(item);
		}
		
		return element;
	}
	
}
