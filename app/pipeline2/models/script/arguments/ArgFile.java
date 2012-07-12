package pipeline2.models.script.arguments;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import pipeline2.models.script.Argument;

public class ArgFile extends Argument {
	
	public String href = "";
	
	public ArgFile(Argument arg, String href) {
		super(arg);
		this.href = href;
	}
	
	public Element asDocumentElement(Document document) {
		Element element;
		if ("option".equals(kind)) {
			element = document.createElement("option");
			element.setAttribute("name", name);
			element.setTextContent(href+"");
		} else {
			element = document.createElement("input");
			element.setAttribute("name", name);
			
			Element item = document.createElement("item");
			item.setAttribute("value", href+"");
			element.appendChild(item);
		}
		return element;
	}
	
}
