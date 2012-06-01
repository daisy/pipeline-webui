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
		
		// new API
//		for (String href : hrefs) {
//			Element item = document.createElement("item");
//			item.setTextContent(href);
//			element.appendChild(item);
//		}
		
		// old API
		if ("option".equals(kind)) {
			String csv = hrefs.size() > 0 ? hrefs.get(0) : "";
			for (int i = 1; i < hrefs.size(); i++)
				csv += ","+hrefs.get(i);
			element.setTextContent(csv);
		} else {
			for (String href : hrefs) {
				Element file = document.createElement("file");
				file.setAttribute("src", href);
				element.appendChild(file);
			}
		}
		
		return element;
	}
	
}
