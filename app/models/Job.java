package models;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import pipeline2.Pipeline2WS;
import pipeline2.Pipeline2WSResponse;
import play.Logger;
import play.db.ebean.Model;

import javax.persistence.*;

import org.w3c.dom.Node;

import controllers.Application;

import play.data.validation.*;
import play.libs.XPath;
import utils.Pair;

@Entity
public class Job extends Model implements Comparable<Job> {
    
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
	
}