package pipeline2.models.script.arguments;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import pipeline2.models.script.Argument;

public class ArgStrings extends Argument {
	
	public List<String> values = new ArrayList<String>();
	
	public ArgStrings(Argument arg) {
		super(arg);
	}
	
	/** For chaining */
	public ArgStrings add(String value) {
		values.add(value);
		return this;
	}
	
	public Element asDocumentElement(Document document) {
		Element element = document.createElement("option");
		element.setAttribute("name", name);
		
		// new API
		for (String value : values) {
			Element item = document.createElement("item");
			item.setAttribute("value", value);
			element.appendChild(item);
		}
		
		// old API
//		String csv = values.size() > 0 ? values.get(0) : "";
//		for (int i = 1; i < values.size(); i++)
//			csv += ","+values.get(i);
//		element.setTextContent(csv);
		
		return element;
	}
	
}
