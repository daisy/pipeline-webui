import play.*;
import models.*;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.daisy.pipeline.client.Pipeline2WSException;

import akka.util.Duration;
import play.libs.Akka;

public class Global extends GlobalSettings {
	
	public synchronized void beforeStart(Application app) {
		Logger.debug("Application is about to start...");

	}
	
//	@Override
	public synchronized void onStart(Application app) {
		// Application has started...
		final String datasource = Configuration.root().getString("dp2.datasource");
		
		if (Setting.get("branding.title") == null)
			Setting.set("branding.title", "DAISY Pipeline 2");
		
		if (Setting.get("branding.theme") == null)
			Setting.set("branding.theme", "");
		
		if (Setting.get("jobs.hideAdvancedOptions") == null)
			Setting.set("jobs.hideAdvancedOptions", "true");
		
		if (Setting.get("jobs.deleteAfterDuration") == null)
			Setting.set("jobs.deleteAfterDuration", "0");
		
		NotificationConnection.notificationConnections = new ConcurrentHashMap<Long,List<NotificationConnection>>();
		
		// Push "heartbeat" notifications (keeping the push notification connections alive). Hopefully this scales...
		Akka.system().scheduler().schedule(
				Duration.create(0, TimeUnit.SECONDS),
				Duration.create(1, TimeUnit.SECONDS),
				new Runnable() {
					public void run() {
						synchronized (NotificationConnection.notificationConnections) {
							for (Long userId : NotificationConnection.notificationConnections.keySet()) {
								List<NotificationConnection> browsers = NotificationConnection.notificationConnections.get(userId);
								
								for (int c = browsers.size()-1; c >= 0; c--) {
									if (!browsers.get(c).isAlive()) {
										Logger.debug("Browser: user #"+userId+" timed out browser window #"+browsers.get(c).browserId+" (last read: "+browsers.get(c).lastRead+")");
										browsers.remove(c);
									}
								}
								
								for (NotificationConnection c : browsers) {
									if (c.notifications.size() == 0) {
//										Logger.debug("*heartbeat* for user #"+userId+" and browser window #"+c.browserId);
										c.push(new Notification("heartbeat", null));
									}
								}
							}
						}
					}
				}
			);
		
		// Delete jobs and uploads after a certain time. Configurable by administrators.
		Akka.system().scheduler().schedule(
				Duration.create(1, TimeUnit.MINUTES),
				Duration.create(1, TimeUnit.MINUTES),
				new Runnable() {
					public void run() {
						if ("0".equals(Setting.get("jobs.deleteAfterDuration")))
							return;
						
						Date timeoutDate = new Date(new Date().getTime() - Long.parseLong(Setting.get("jobs.deleteAfterDuration")));
						
						List<Job> jobs = Job.find.all();
						for (Job job : jobs) {
							if (job.finished != null && job.finished.before(timeoutDate)) {
								Logger.info("Deleting old job: "+job.id+" ("+job.nicename+")");
								job.delete(datasource);
							}
						}
						
						List<Upload> uploads = Upload.find.all();
						for (Upload upload : uploads) {
							if (upload.job == null && upload.uploaded.before(timeoutDate)) {
								Logger.info("Deleting old upload: "+upload.id+(upload.getFile()!=null?" ("+upload.getFile().getName()+")":""));
								upload.delete(datasource);
							}
						}
					}
				}
				);
		
		// If jobs.deleteAfterDuration is not set; clean up jobs that no longer exists in the Pipeline 2 framework. This typically happens if the framework is restarted.
		Akka.system().scheduler().schedule(
				Duration.create(1, TimeUnit.MINUTES),
				Duration.create(1, TimeUnit.HOURS),
				new Runnable() {
					public void run() {
						if (Setting.get("jobs.deleteAfterDuration") != null && !"0".equals(Setting.get("jobs.deleteAfterDuration")))
							return;
						
						String endpoint = Setting.get("dp2ws.endpoint");
						if (endpoint == null)
							return;
						
						List<org.daisy.pipeline.client.models.Job> fwkJobs;
						try {
							fwkJobs = org.daisy.pipeline.client.models.Job.getJobs(org.daisy.pipeline.client.Jobs.get(endpoint, Setting.get("dp2ws.authid"), Setting.get("dp2ws.secret")));
							
						} catch (Pipeline2WSException e) {
							Logger.error(e.getMessage(), e);
							return;
						}
						
						List<Job> webUiJobs = Job.find.all();
						
						for (Job webUiJob : webUiJobs) {
							boolean exists = false;
							for (org.daisy.pipeline.client.models.Job fwkJob : fwkJobs) {
								if (webUiJob.id.equals(fwkJob.id)) {
									exists = true;
									break;
								}
							}
							if (!exists) {
								Logger.info("Deleting job that no longer exists in the Pipeline 2 framework: "+webUiJob.id+" ("+webUiJob.nicename+")");
								webUiJob.delete(datasource);
							}
						}
					}
				}
			);
	}

//	@Override
	public void onStop(Application app) {
		// Application shutdown...
	}

}