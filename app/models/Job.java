package models;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Transient;

import org.daisy.pipeline.client.Pipeline2Exception;
import org.daisy.pipeline.client.Pipeline2Logger;
import org.daisy.pipeline.client.filestorage.JobStorage;
import org.daisy.pipeline.client.models.Job.Priority;
import org.daisy.pipeline.client.models.Job.Status;
import org.daisy.pipeline.client.models.Callback;
import org.daisy.pipeline.client.models.Message;
import org.daisy.pipeline.client.models.Result;
import org.daisy.pipeline.client.models.Script;

import play.Logger;
import play.libs.Akka;
import scala.concurrent.duration.Duration;
import utils.XML;
import akka.actor.Cancellable;

import com.avaje.ebean.Model;

import controllers.Application;

@Entity
public class Job extends Model implements Comparable<Job> {
	/** Key is the job ID; value is the sequence number of the last message read from the Pipeline 2 Web API. */ 
	public static Map<String,Integer> lastMessageSequence = Collections.synchronizedMap(new HashMap<String,Integer>());
	public static Map<String,String> lastStatus = Collections.synchronizedMap(new HashMap<String,String>());
	
	@Id
	public Long id;

	// General information
	public String engineId;
	public String nicename;
	public Date created;
	public Date started;
	public Date finished;
	@Column(name="user_id") public Long user;
	public String guestEmail; // Guest users may enter an e-mail address to receive notifications
	public String localDirName;
	public String scriptId;
	public String scriptName;
	public String status;
	
	// Notification flags
	public boolean notifiedCreated;
	public boolean notifiedComplete;

	// Not stored in the job table; retrieved dynamically
	@Transient
	public String href;
	@Transient
	private String userNicename;
	@Transient
	org.daisy.pipeline.client.models.Job clientlibJob;

	@Transient
	private Cancellable pushNotifier;
	
	/** Make job belonging to user */
	public Job(User user) {
		super();
		this.user = user.id;
		this.status = "NEW";
		this.created = new Date();
		this.notifiedCreated = false;
		this.notifiedComplete = false;
		if (user.id < 0)
			this.userNicename = Setting.get("users.guest.name");
		else
			this.userNicename = User.findById(user.id).name;
	}
	
	/** Make job from engine job */
	public Job(org.daisy.pipeline.client.models.Job job, User user) {
		super();
		this.engineId = job.getId();
		this.user = user.id;
		this.nicename = job.getNicename();
		this.status = job.getStatus()+"";
		this.created = new Date();
		this.notifiedCreated = false;
		this.notifiedComplete = false;
		if (user.id < 0)
			this.userNicename = Setting.get("users.guest.name");
		else
			this.userNicename = User.findById(user.id).name;
		
		if (!org.daisy.pipeline.client.models.Job.Status.IDLE.equals(job.getStatus())) {
			this.started = this.created;
			if (!org.daisy.pipeline.client.models.Job.Status.RUNNING.equals(job.getStatus())) {
				this.finished = this.started;
			}
		}
		
		this.scriptId = job.getScript().getId();
		this.scriptName = job.getScript().getNicename();
		
		File jobDir = new File(new File(Setting.get("jobs")), job.getId());
		try {
			this.localDirName = jobDir.getCanonicalPath();
		} catch (IOException e) {
			this.localDirName = jobDir.getPath();
		}
	}

	public int compareTo(Job other) {
		return created.compareTo(other.created);
	}

	// -- Queries

	public static Model.Finder<Long,Job> find = new Model.Finder<Long, Job>(Job.class);

	/** Retrieve a Job by its id. */
	public static Job findById(Long id) {
		Job job = find.where().eq("id", id).findUnique();
		if (job != null) {
			User user = User.findById(job.user);
			if (user != null)
				job.userNicename = user.name;
			else if (job.user < 0)
				job.userNicename = Setting.get("users.guest.name");
			else
				job.userNicename = "User";
		}
		return job;
	}

	/** Retrieve a Job by its engine id. */
	public static Job findByEngineId(String id) {
		Job job = find.where().eq("engine_id", id).findUnique();
		if (job != null) {
			User user = User.findById(job.user);
			if (user != null)
				job.userNicename = user.name;
			else if (job.user < 0)
				job.userNicename = Setting.get("users.guest.name");
			else
				job.userNicename = "User";
		}
		return job;
	}
	
	/** The nice name of the user that owns this job */
	public String getUserNicename() {
		if (userNicename == null) {
			if (user == null || user < 0)
				userNicename = Setting.get("users.guest.name");
			else
				userNicename = User.findById(user).name;
		}
		return userNicename;
	}
	
	public void cancelPushNotifications() {
		Logger.debug("Cancelling push notifications for job #"+id+" with engineId="+engineId);
		if (pushNotifier != null) {
			pushNotifier.cancel();
			pushNotifier = null;
		}
	}
	
	public void pushNotifications() {
		if (pushNotifier != null) {
			return;
		}
		
		Logger.debug("Starting new push notifications for job #"+id+" with engineId="+engineId);
		pushNotifier = Akka.system().scheduler().schedule(
				Duration.create(0, TimeUnit.SECONDS),
				Duration.create(500, TimeUnit.MILLISECONDS),
				new Runnable() {
					public void run() {
						try {
							int fromSequence = Job.lastMessageSequence.containsKey(id) ? Job.lastMessageSequence.get(id) : 0;
//							Logger.debug("checking job #"+id+" for updates from message #"+fromSequence);
							
							org.daisy.pipeline.client.models.Job job = getJobFromEngine(fromSequence);
							
							if (job == null) {
								Logger.debug("Could not find job in engine ("+engineId+")");
								return;
							}
							
							Job webUiJob = Job.findByEngineId(job.getId());
							
							if (webUiJob == null) {
								Logger.debug("Job has been deleted; stop updates (engine id: "+job.getId()+")");
								pushNotifier.cancel();
								return;
							}
							
							if (job.getStatus() != Status.RUNNING && job.getStatus() != Status.IDLE) {
								pushNotifier.cancel();
								if (webUiJob.finished == null) {
									// pushNotifier tends to fire multiple times after canceling it, so this if{} is just to fire the "finished" event exactly once
									webUiJob.finished = new Date();
									Map<String,String> finishedMap = new HashMap<String,String>();
									finishedMap.put("text", webUiJob.finished.toString());
									finishedMap.put("number", webUiJob.finished.getTime()+"");
									NotificationConnection.pushJobNotification(webUiJob.user, new Notification("job-finished-"+webUiJob.id, finishedMap));
									NotificationConnection.pushJobNotification(webUiJob.user, new Notification("job-results-"+webUiJob.id, jsonifiableResults(job)));
								}
							}
							
							if (job.getStatus() != null && !job.getStatus().toString().equals(lastStatus.get(job.getId()))) {
								Logger.debug("    status has changed to "+job.getStatus());
								lastStatus.put(job.getId(), job.getStatus().toString());
								Logger.debug("    notifying job-status-"+webUiJob.id);
								NotificationConnection.pushJobNotification(webUiJob.user, new Notification("job-status-"+webUiJob.id, job.getStatus()));
								
								webUiJob.status = job.getStatus().toString();
								
								if (job.getStatus() == Status.RUNNING) {
									// job status changed from IDLE to RUNNING
									Logger.debug("    job status changed from IDLE to RUNNING");
									webUiJob.started = new Date();
									Map<String,String> startedMap = new HashMap<String,String>();
									startedMap.put("text", webUiJob.started.toString());
									startedMap.put("number", webUiJob.started.getTime()+"");
									Logger.debug("    notifying job-started-"+webUiJob.id);
									NotificationConnection.pushJobNotification(webUiJob.user, new Notification("job-started-"+webUiJob.id, startedMap));
								}
								
								Logger.debug("    saving");
								webUiJob.save();
							}
							
							List<org.daisy.pipeline.client.models.Message> messages = job.getMessages();
							if (messages != null) {
								for (org.daisy.pipeline.client.models.Message message : messages) {
									Notification notification = new Notification("job-message-"+webUiJob.id, message);
									NotificationConnection.pushJobNotification(webUiJob.user, notification);
								}
								
								if (messages.size() > 0) {
									Job.lastMessageSequence.put(job.getId(), messages.get(messages.size()-1).sequence);
								}
							}
							
						} catch (javax.persistence.PersistenceException e) {
							// Ignores this exception that happens on shutdown:
							// javax.persistence.PersistenceException: java.sql.SQLException: Attempting to obtain a connection from a pool that has already been shutdown.
							// Should be safe to ignore I think...
						}
					}
				},
				Akka.system().dispatcher()
				);
	}
	
	public static Object jsonifiableResults(org.daisy.pipeline.client.models.Job job) {
		Result allResults = job.getResult();
		SortedMap<Result, List<Result>> individualResults = job.getResults();
		
		SortedMap<String,Object> jsonResults = new TreeMap<String,Object>();
		if (allResults != null) {
			jsonResults.put("filename", allResults.filename);
			jsonResults.put("from", allResults.from);
			jsonResults.put("mimeType", allResults.mimeType);
			jsonResults.put("name", allResults.name);
			jsonResults.put("nicename", allResults.nicename);
			jsonResults.put("relativeHref", allResults.relativeHref);
			jsonResults.put("prettyRelativeHref", allResults.prettyRelativeHref);
			jsonResults.put("size", allResults.size);
		}
		List<Object> jsonResultResults = new ArrayList<Object>();
		if (individualResults != null) {
			for (Result individualResult : individualResults.keySet()) {
				SortedMap<String,Object> jsonSubResults = new TreeMap<String,Object>();
				jsonSubResults.put("filename", individualResult.filename);
				jsonSubResults.put("from", individualResult.from);
				jsonSubResults.put("mimeType", individualResult.mimeType);
				jsonSubResults.put("name", individualResult.name);
				jsonSubResults.put("nicename", individualResult.nicename);
				jsonSubResults.put("relativeHref", individualResult.relativeHref);
				jsonSubResults.put("prettyRelativeHref", individualResult.prettyRelativeHref);
				jsonSubResults.put("size", individualResult.size);
				List<Object> jsonSubResultResults = new ArrayList<Object>();
				for (Result fileResult : individualResults.get(individualResult)) {
					SortedMap<String,Object> jsonSubSubResults = new TreeMap<String,Object>();
					jsonSubSubResults.put("filename", fileResult.filename);
					jsonSubSubResults.put("from", fileResult.from);
					jsonSubSubResults.put("mimeType", fileResult.mimeType);
					jsonSubSubResults.put("name", fileResult.name);
					jsonSubSubResults.put("nicename", fileResult.nicename);
					jsonSubSubResults.put("relativeHref", fileResult.relativeHref);
					jsonSubSubResults.put("prettyRelativeHref", fileResult.prettyRelativeHref);
					jsonSubSubResults.put("size", fileResult.size);
					jsonSubResultResults.add(jsonSubSubResults);
				}
				jsonSubResults.put("results", jsonSubResultResults);
				jsonResultResults.add(jsonSubResults);
			}
		}
		jsonResults.put("results", jsonResultResults);
		
		return jsonResults;
	}

	@Override
	public void delete() {
		Logger.debug("deleting "+this.id+" (sending DELETE request)");
		boolean success = Application.ws.deleteJob(this.engineId);
		if (!success) {
			Pipeline2Logger.logger().error("An error occured when trying to delete job "+this.id+" ("+this.engineId+") from the Pipeline 2 Engine");
		}
		asJob().getJobStorage().delete();
		super.delete();
	}
	
	@Override
	public void save() {
		// save to job storage as well
		org.daisy.pipeline.client.models.Job engineJob = asJob();
		engineJob.getJobStorage().save();
		jobUpdateHelper();
		
		synchronized (this) {
			super.save();
			
			if (id == null) {
				id = (Long) Job.find.orderBy("id desc").findIds().get(0);
				if (nicename == null) {
					nicename = "Job #"+id;
					super.save();
				}
			}
		}
		
		synchronized (this) {
			int messages;
			messages = clientlibJob.getMessages() != null ? clientlibJob.getMessages().size() : -2;
			Logger.debug("saved (with "+messages+" messages):");
			Logger.debug(XML.toString(clientlibJob.toXml()));
		}
	}

	private void jobUpdateHelper() {
		engineId = clientlibJob.getId();
		if (nicename == null) {
			nicename = clientlibJob.getNicename();
		}
		if (scriptId == null && clientlibJob.getScript() != null) {
			scriptId = clientlibJob.getScript().getId();
		}
		if (scriptName == null && clientlibJob.getScript() != null) {
			scriptName = clientlibJob.getScript().getNicename();
		}
		try {
			// if current status is one of the build-in types
			// (i.e. not "NEW", "UNDEFINED", "TEMPLATE" or anything else used only by the Web UI)
			// then get the status from the engine.
			if (Status.valueOf(status) != null) {
				status = clientlibJob.getStatus()+"";
			}
		}
		catch (IllegalArgumentException e) {}
		catch (NullPointerException e) {}
	}

	public org.daisy.pipeline.client.models.Job asJob() {
		if (clientlibJob == null) {
			Logger.debug("getting client job (not cached from earlier)");
			File jobStorageDir = new File(Setting.get("jobs"));
			clientlibJob = JobStorage.loadJob(""+id, jobStorageDir);
			if (clientlibJob == null) {
				Logger.debug("not found in job storage");
				getJobFromEngine(0);
				if (clientlibJob != null) {
					Logger.debug("got job from engine; setting job storage based on id");
					new JobStorage(clientlibJob, jobStorageDir, ""+id);
					if (clientlibJob.getNicename() == null) {
						Logger.debug("setting client nicename to "+nicename);
						clientlibJob.setNicename(nicename);
					}
				}
			} else Logger.debug("found in job storage");
			if (clientlibJob == null) {
				Logger.debug("not found in engine or storage; job is a new job");
				clientlibJob = new org.daisy.pipeline.client.models.Job();
				Logger.debug("setting job storage based on id");
				clientlibJob.setNicename(nicename);
				new JobStorage(clientlibJob, jobStorageDir, ""+id);
			}
			setJob(clientlibJob);
		}
		return clientlibJob;
	}
	
	public void setJob(org.daisy.pipeline.client.models.Job job) {
		if (clientlibJob == null) {
			clientlibJob = asJob();
		}
		
		if (job.getId() != null) {
		    clientlibJob.setId(job.getId());
		}
		if (job.getHref() != null) {
		    clientlibJob.setHref(job.getHref());
		}
		if (job.getStatus() != null) {
		    clientlibJob.setStatus(job.getStatus());
		}
		if (job.getPriority() != null) {
		    clientlibJob.setPriority(job.getPriority());
		}
		if (job.getLogHref() != null) {
		    clientlibJob.setLogHref(job.getLogHref());
		}
		if (job.getMessages() != null) {
		    clientlibJob.setMessages(job.getMessages());
		}
		
		if (clientlibJob.getBatchId() == null && job.getBatchId() != null) {
			clientlibJob.setBatchId(job.getBatchId());
		}
		if (clientlibJob.getCallback() == null && job.getCallback() != null) {
		    clientlibJob.setCallback(job.getCallback());
		}
		if (clientlibJob.getNicename() == null && job.getNicename() != null) {
		    clientlibJob.setNicename(job.getNicename());
		}
		if (clientlibJob.getInputs() == null && job.getInputs() != null) {
		    clientlibJob.setInputs(job.getInputs());
		}
		if (clientlibJob.getOutputs() == null && job.getOutputs() != null) {
		    clientlibJob.setOutputs(job.getOutputs());
		}
		if (clientlibJob.getScriptHref() == null && job.getScriptHref() != null) {
		    clientlibJob.setScriptHref(job.getScriptHref());
		}
		if (clientlibJob.getScript() == null && job.getScript() != null) {
		    clientlibJob.setScript(job.getScript());
		}
		if (clientlibJob.getScript() == null || job.getScript() != null && !clientlibJob.getScript().getId().equals(job.getScript().getId())) {
			if (job.getScript() != null) {
				clientlibJob.setScript(Application.ws.getScript(job.getScript().getId()));
				clientlibJob.setInputs(clientlibJob.getInputs());
			    clientlibJob.setOutputs(clientlibJob.getOutputs());
			}
		}
		
		jobUpdateHelper();
	}

	/** Use this method to get the job from the engine to ensure that the XML in the webuis job storage is always up to date */
	public org.daisy.pipeline.client.models.Job getJobFromEngine(int fromSequence) {
		Logger.debug("Getting job from engine: "+engineId);
		if (engineId == null) {
			return null;
		}
		org.daisy.pipeline.client.models.Job clientlibJob = Application.ws.getJob(engineId, fromSequence);
		if (clientlibJob == null) {
			return null;
		}
		synchronized (this) {
			int messages;
			messages = clientlibJob.getMessages() != null ? clientlibJob.getMessages().size() : -2;
			Logger.debug("received (with "+messages+" messages):");
			Logger.debug(XML.toString(clientlibJob.toXml()));
		}
		setJob(clientlibJob);
		save();
		return clientlibJob;
	}
	
}
