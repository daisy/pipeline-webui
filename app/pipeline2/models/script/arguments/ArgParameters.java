package pipeline2.models.script.arguments;

import java.util.HashMap;
import java.util.Map;

import org.w3c.dom.Node;

import pipeline2.models.script.Argument;

public class ArgParameters extends Argument {
	
	public Map<String,String> parameters = new HashMap<String,String>();
	
	public ArgParameters(Argument arg) {
		super(arg);
	}
	
	/** For chaining */
	public Argument put(String name, String value) {
		this.parameters.put(name, value);
		return this;
	}
	
	public Node asDocumentElement() {
		return null; // TODO: parameter ports are not implemented yet
	}
	
}
