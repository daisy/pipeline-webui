import play.*;
import models.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;





//import org.daisy.pipeline.client.Pipeline2;
import org.daisy.pipeline.client.Pipeline2Exception;
import org.daisy.pipeline.client.Pipeline2Logger;
import org.daisy.pipeline.client.http.WSResponse;
//import org.daisy.pipeline.client.Pipeline2Logger;





import controllers.Administrator;
import controllers.Application;
import controllers.FirstUse;
import play.libs.Akka;
import scala.concurrent.duration.Duration;
import utils.Pipeline2PlayLogger;

public class Global extends GlobalSettings {
	
	@Override
	public synchronized void beforeStart(play.Application app) {
		
	}
	
	@Override
	public synchronized void onStart(play.Application app) {
		// Application has started...
		
		Pipeline2Logger.setLogger(new Pipeline2PlayLogger());
		if ("DEBUG".equals(Configuration.root().getString("logger.application"))) {
			Logger.debug("Enabling clientlib debug mode");
			Pipeline2Logger.logger().setLevel(Pipeline2Logger.LEVEL.DEBUG);
		}
		
		NotificationConnection.notificationConnections = new ConcurrentHashMap<Long,List<NotificationConnection>>();
		
		if (Setting.get("appearance.title") == null)
			Setting.set("appearance.title", "DAISY Pipeline 2");
		
		if (Setting.get("appearance.titleLink") == null)
			Setting.set("appearance.titleLink", "scripts");
		
		if (Setting.get("appearance.titleLink.newWindow") == null)
			Setting.set("appearance.titleLink.newWindow", "false");
		
		if (Setting.get("appearance.landingPage") == null)
			Setting.set("appearance.landingPage", "welcome");
		
		if (Setting.get("appearance.theme") == null)
			Setting.set("appearance.theme", "");
		
		if (Setting.get("jobs.hideAdvancedOptions") == null)
			Setting.set("jobs.hideAdvancedOptions", "true");
		
		if (Setting.get("jobs.deleteAfterDuration") == null)
			Setting.set("jobs.deleteAfterDuration", "0");
		
		Akka.system().scheduler().schedule(
				Duration.create(0, TimeUnit.SECONDS),
				Duration.create(10, TimeUnit.SECONDS),
				new Runnable() {
					public void run() {
						try {
							if (Setting.get("dp2ws.endpoint") == null)
								return;

							String endpoint = Setting.get("dp2ws.endpoint");
							if (endpoint == null) {
								Application.setAlive(null);
								return;
							}

							Application.setAlive(controllers.Application.ws.alive());
							
						} catch (javax.persistence.PersistenceException e) {
							// Ignores this exception that happens on shutdown:
							// javax.persistence.PersistenceException: java.sql.SQLException: Attempting to obtain a connection from a pool that has already been shutdown.
							// Should be safe to ignore I think...
						}
					}
				},
				Akka.system().dispatcher()
				);
		
		// Push "heartbeat" notifications (keeping the push notification connections alive). Hopefully this scales...
		Akka.system().scheduler().schedule(
				Duration.create(0, TimeUnit.SECONDS),
				Duration.create(1, TimeUnit.SECONDS),
				new Runnable() {
					public void run() {
						try {
							synchronized (NotificationConnection.notificationConnections) {
								for (Long userId : NotificationConnection.notificationConnections.keySet()) {
									List<NotificationConnection> browsers = NotificationConnection.notificationConnections.get(userId);
									
									for (int c = browsers.size()-1; c >= 0; c--) {
										if (!browsers.get(c).isAlive()) {
	//										Logger.debug("Browser: user #"+userId+" timed out browser window #"+browsers.get(c).browserId+" (last read: "+browsers.get(c).lastRead+")");
											browsers.remove(c);
										}
									}
									
									for (NotificationConnection c : browsers) {
										if (c.notifications.size() == 0) {
	//										Logger.debug("*heartbeat* for user #"+userId+" and browser window #"+c.browserId);
											c.push(new Notification("heartbeat", controllers.Application.pipeline2EngineAvailable()));
										}
									}
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
		
		// Delete jobs and uploads after a certain time. Configurable by administrators.
		Akka.system().scheduler().schedule(
				Duration.create(1, TimeUnit.MINUTES),
				Duration.create(1, TimeUnit.MINUTES),
				new Runnable() {
					public void run() {
						try {
							// jobs are only deleted if that option is set in admin settings
							if ("0".equals(Setting.get("jobs.deleteAfterDuration")))
								return;
							
							Date timeoutDate = new Date(new Date().getTime() - Long.parseLong(Setting.get("jobs.deleteAfterDuration")));
							
							List<Job> jobs = Job.find.all();
							for (Job job : jobs) {
								if (job.finished != null && job.finished.before(timeoutDate)) {
									Logger.info("Deleting old job: "+job.id+" ("+job.nicename+")");
									job.delete();
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
		
		// If jobs.deleteAfterDuration is not set; clean up jobs that no longer exists in the Pipeline engine. This typically happens if the Pipeline engine is restarted.
		Akka.system().scheduler().schedule(
				Duration.create(1, TimeUnit.MINUTES),
				Duration.create(1, TimeUnit.MINUTES),
				new Runnable() {
					public void run() {
						try {
							String endpoint = Setting.get("dp2ws.endpoint");
							if (endpoint == null)
								return;
							
							List<org.daisy.pipeline.client.models.Job> engineJobs = controllers.Application.ws.getJobs();
							if (engineJobs == null) {
								return;
							}
							
							List<Job> webUiJobs = Job.find.all();
							
							for (Job webUiJob : webUiJobs) {
								if (webUiJob.engineId != null) {
									boolean exists = false;
									for (org.daisy.pipeline.client.models.Job engineJob : engineJobs) {
										if (webUiJob.engineId.equals(engineJob.getId())) {
											exists = true;
											break;
										}
									}
									if (!exists) {
										Logger.info("Deleting job that no longer exists in the Pipeline engine: "+webUiJob.id+" ("+webUiJob.engineId+" - "+webUiJob.nicename+")");
										webUiJob.delete();
									}
								}
							}
							
							if (controllers.Application.getAlive() != null) {
								for (org.daisy.pipeline.client.models.Job engineJob : engineJobs) {
									boolean exists = false;
									for (Job webUiJob : webUiJobs) {
										if (engineJob.getId().equals(webUiJob.engineId)) {
											exists = true;
											break;
										}
									}
									if (!exists) {
										Logger.info("Adding job from the Pipeline engine that does not exist in the Web UI: "+engineJob.getId());
										User notLoggedIn = User.findById(-1L);
										if (notLoggedIn == null) {
											notLoggedIn = new User("not-logged-in@example.net", "Not logged in", "not logged in", false);
										}
										Job webUiJob = new Job(engineJob, notLoggedIn); // TODO: ensure that user with ID=-1 exists at this point
										webUiJob.save();
									}
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

}
