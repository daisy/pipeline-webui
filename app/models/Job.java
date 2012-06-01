package models;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import pipeline2.Pipeline2WSResponse;
import play.Logger;
import play.db.ebean.Model;

import javax.persistence.*;

import controllers.Application;
import controllers.Jobs;

import akka.actor.Cancellable;
import akka.util.Duration;

import play.data.validation.*;
import play.libs.Akka;

@Entity
public class Job extends Model implements Comparable<Job> {
	
	/** Key is the job ID; value is the sequence number of the last message read from the Pipeline 2 Web Service. */ 
	public static Map<String,Integer> lastMessageSequence = Collections.synchronizedMap(new HashMap<String,Integer>());
	public static Map<String,pipeline2.models.Job.Status> lastStatus = Collections.synchronizedMap(new HashMap<String,pipeline2.models.Job.Status>());
	
	
	@Id
	@Constraints.Required
	public String id;

	// General information
	public String nicename;
	public Date created;
	public Date started;
	public Long user;

	// Notification flags
	public boolean notifiedCreated;
	public boolean notifiedComplete;

	// Not stored in the job table; retrieved dynamically
	@Transient
	public String href;
	@Transient
	public String status;
	@Transient
	public String userNicename;

	@Transient
	private Cancellable pushNotifier;

	public Job(String id, User user) {
		this.id = id;
		this.user = user.id;
		this.nicename = id;
		this.created = new Date();
		this.notifiedCreated = false;
		this.notifiedComplete = false;
		this.userNicename = User.findById(user.id).name;
	}

	public int compareTo(Job other) {
		return created.compareTo(other.created);
	}

	// -- Queries

	public static Model.Finder<String,Job> find = new Model.Finder(String.class, Job.class);

	/** Retrieve a Job by its id. */
	public static Job findById(String id) {
		Job job = find.where().eq("id", id).findUnique();
		job.userNicename = User.findById(job.user).name;
		return job;
	}

	public void pushNotifications() {
		if (pushNotifier != null)
			return;

		pushNotifier = Akka.system().scheduler().schedule(
				Duration.create(0, TimeUnit.SECONDS),
				Duration.create(250, TimeUnit.MILLISECONDS),
				new Runnable() {
					public void run() {
						Integer fromSequence = Job.lastMessageSequence.containsKey(id) ? Job.lastMessageSequence.get(id) : 0;
						Logger.debug("Job update notifications");
						
						Pipeline2WSResponse wsJob = pipeline2.Jobs.get(Setting.get("dp2ws.endpoint"), Setting.get("dp2ws.authid"), Setting.get("dp2ws.secret"), id, fromSequence);
						
						if (wsJob.status != 200 && wsJob.status != 201) {
							return;
						}
						
						pipeline2.models.Job job = new pipeline2.models.Job(wsJob.asXml());
						
						if (job.status != pipeline2.models.Job.Status.RUNNING && job.status != pipeline2.models.Job.Status.IDLE) {
							pushNotifier.cancel();
						}
						
						Collections.sort(job.messages);
						Job webuiJob = Job.findById(job.id);
						for (pipeline2.models.job.Message message : job.messages) {
							Notification notification = new Notification("job-message-"+job.id, message);
							User.push(webuiJob.user, notification);
						}
						
						if (!job.status.equals(lastStatus.get(job.id))) {
							lastStatus.put(job.id, job.status);
							User.push(webuiJob.user, new Notification("job-status-"+job.id, job.status));
						}
					}
				}
				);
	}

}