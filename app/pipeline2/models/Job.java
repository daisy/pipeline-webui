package pipeline2.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import pipeline2.Pipeline2WS;
import pipeline2.Pipeline2WSResponse;
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
		this(XPath.selectNode("/d:job", jobXml, Pipeline2WS.ns));
	}
	
	public Job(Node jobXml) {
		this();
		
		id = XPath.selectText("@id", jobXml, Pipeline2WS.ns);
		href = XPath.selectText("@href", jobXml, Pipeline2WS.ns);
		String status = XPath.selectText("@status", jobXml, Pipeline2WS.ns);
		for (Status s : Status.values()) {
			if (s.toString().equals(status)) {
				this.status = s;
				break;
			}
		}
		script.id = XPath.selectText("d:script/@id", jobXml, Pipeline2WS.ns);
		script.href = XPath.selectText("d:script/@href", jobXml, Pipeline2WS.ns);
		script.desc = XPath.selectText("d:script/d:description", jobXml, Pipeline2WS.ns);
		logHref = XPath.selectText("d:log/@href", jobXml, Pipeline2WS.ns);
		resultHref = XPath.selectText("d:result/@href", jobXml, Pipeline2WS.ns);
		
		List<Node> messageNodes = XPath.selectNodes("d:messages/d:message", jobXml, Pipeline2WS.ns);
		for (Node messageNode : messageNodes) {
			this.messages.add(new Message(
				XPath.selectText("@level", messageNode, Pipeline2WS.ns),
				XPath.selectText("@sequence", messageNode, Pipeline2WS.ns),
				XPath.selectText(".", messageNode, Pipeline2WS.ns)
			));
		}
		Collections.sort(this.messages);
	}
	
	public static List<Job> getJobs(Pipeline2WSResponse response) {
		List<Job> jobs = new ArrayList<Job>();
		
		List<Node> jobNodes = XPath.selectNodes("/d:jobs/d:job", response.asXml(), Pipeline2WS.ns);
		for (Node jobNode : jobNodes) {
			jobs.add(new Job(jobNode));
		}
		
		return jobs;
	}
}
