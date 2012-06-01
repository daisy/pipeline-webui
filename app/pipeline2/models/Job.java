package pipeline2.models;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import pipeline2.Pipeline2WS;
import pipeline2.models.job.Message;
import play.libs.XPath;

public class Job {
	
	public enum Status { IDLE, RUNNING, DONE, ERROR };
	
	public String id;
	public String href; // xs:anyURI
	public Status status;
	
	public Script script;
	public List<Message> messages;
	public String logHref;
	public String resultHref;
	
	public Job() {
		script = new Script();
		messages = new ArrayList<Message>();
	}
	
	public Job(Document jobXml) {
		this();
		
		id = XPath.selectText("/d:job/@id", jobXml, Pipeline2WS.ns);
		href = XPath.selectText("/d:job/@href", jobXml, Pipeline2WS.ns);
		String status = XPath.selectText("/d:job/@status", jobXml, Pipeline2WS.ns);
		for (Status s : Status.values()) {
			if (s.toString().equals(status)) {
				this.status = s;
				break;
			}
		}
		script.id = XPath.selectText("/d:job/d:script/@id", jobXml, Pipeline2WS.ns);
		script.href = XPath.selectText("/d:job/d:script/@href", jobXml, Pipeline2WS.ns);
		script.desc = XPath.selectText("/d:job/d:script/d:description", jobXml, Pipeline2WS.ns);
		logHref = XPath.selectText("/d:job/d:log/@href", jobXml, Pipeline2WS.ns);
		resultHref = XPath.selectText("/d:job/d:result/@href", jobXml, Pipeline2WS.ns);
		
		List<Node> messageNodes = XPath.selectNodes("/d:job/d:messages/d:message", jobXml, Pipeline2WS.ns);
		for (Node messageNode : messageNodes) {
			messages.add(new Message(
				XPath.selectText("@level", messageNode, Pipeline2WS.ns),
				XPath.selectText("@sequence", messageNode, Pipeline2WS.ns),
				XPath.selectText(".", messageNode, Pipeline2WS.ns)
			));
		}
	}
	
}
