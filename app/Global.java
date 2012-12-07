import play.*;
import models.*;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.daisy.pipeline.client.Alive;
import org.daisy.pipeline.client.Pipeline2WS;
import org.daisy.pipeline.client.Pipeline2WSException;
import org.daisy.pipeline.client.Pipeline2WSResponse;
import org.daisy.pipeline.client.models.Script;

import akka.util.Duration;
import play.libs.Akka;
import utils.CommandExecutor;

public class Global extends GlobalSettings {
	
	public synchronized void beforeStart(Application app) {
		
	}
	
//	@Override
	public synchronized void onStart(Application app) {
		// Application has started...
		final String datasource = Configuration.root().getString("dp2.datasource");
		
		if ("desktop".equals(controllers.Application.deployment())) {
			Setting.set("dp2fwk.dir", null); // reconfigure fwk dir each time, in case the install dir has changed
		}
		
		if (User.findAll().size() > 0 && controllers.Application.deployment() == null)
			Setting.set("deployment", "server");
		
		if (Setting.get("appearance.title") == null)
			Setting.set("appearance.title", "DAISY Pipeline 2");
		
		if (Setting.get("appearance.theme") == null)
			Setting.set("appearance.theme", "");
		
		if (Setting.get("jobs.hideAdvancedOptions") == null)
			Setting.set("jobs.hideAdvancedOptions", "true");
		
		if (Setting.get("jobs.deleteAfterDuration") == null)
			Setting.set("jobs.deleteAfterDuration", "0");
		
		if (Play.isDev())
			Pipeline2WS.debug = true;
		
		Akka.system().scheduler().schedule(
				Duration.create(0, TimeUnit.SECONDS),
				Duration.create(10, TimeUnit.SECONDS),
				new Runnable() {
					public void run() {
						if (Setting.get("dp2ws.endpoint") == null)
							return;
						
						Pipeline2WSResponse response;
						Alive alive = null;
						String error = null;

						int status = 200;
						try {
							response = org.daisy.pipeline.client.Alive.get(Setting.get("dp2ws.endpoint"));
							if (response.status != 200) {
								status = response.status;
								error = response.asText();
								
							} else {
								controllers.Application.alive = new org.daisy.pipeline.client.models.Alive(response);
							}
						} catch (Pipeline2WSException e) {
							Logger.error(e.getMessage(), e);
							status = 500;
							error = e.getMessage();
						}
					}
				});
		
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
//										Logger.debug("Browser: user #"+userId+" timed out browser window #"+browsers.get(c).browserId+" (last read: "+browsers.get(c).lastRead+")");
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
						
						// unused uploads are deleted after one hour
						Date timeoutDate = new Date(new Date().getTime() - 3600000L);
						
						List<Upload> uploads = Upload.find.all();
						for (Upload upload : uploads) {
							if (upload.job == null && NotificationConnection.getBrowser(upload.browserId) == null && upload.uploaded.before(timeoutDate)) {
								Logger.info("Deleting old upload that is not open in any browser window: "+upload.id+(upload.getFile()!=null?" ("+upload.getFile().getName()+")":""));
								upload.delete(datasource);
							}
						}
						
						// jobs are only deleted if that option is set in admin settings
						if ("0".equals(Setting.get("jobs.deleteAfterDuration")))
							return;
						
						timeoutDate = new Date(new Date().getTime() - Long.parseLong(Setting.get("jobs.deleteAfterDuration")));
						
						List<Job> jobs = Job.find.all();
						for (Job job : jobs) {
							if (job.finished != null && job.finished.before(timeoutDate)) {
								Logger.info("Deleting old job: "+job.id+" ("+job.nicename+")");
								job.delete(datasource);
							}
						}
					}
				}
				);
		
		// If jobs.deleteAfterDuration is not set; clean up jobs that no longer exists in the Pipeline engine. This typically happens if the Pipeline engine is restarted.
		Akka.system().scheduler().schedule(
				Duration.create(1, TimeUnit.MINUTES),
				Duration.create(5, TimeUnit.MINUTES),
				new Runnable() {
					public void run() {
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
								Logger.info("Deleting job that no longer exists in the Pipeline engine: "+webUiJob.id+" ("+webUiJob.nicename+")");
								webUiJob.delete(datasource);
							}
						}
						
//						for (org.daisy.pipeline.client.models.Job fwkJob : fwkJobs) {
//							boolean exists = false;
//							for (Job webUiJob : webUiJobs) {
//								if (fwkJob.id.equals(webUiJob.id)) {
//									exists = true;
//									break;
//								}
//							}
//							if (!exists) {
//								Logger.info("Adding job from the Pipeline engine that does not exist in the Web UI: "+fwkJob.id);
//								// TODO: add job to webui ?
//							}
//						}
					}
				}
			);
		
		if ("server".equals(controllers.Application.deployment())) Setting.set("dp2fwk.state","RUNNING"); // assume that it is running when in server mode
		else Setting.set("dp2fwk.state","STOPPED");
		
//		if (!"server".equals(controllers.Application.deployment())) {
			
			Akka.system().scheduler().schedule(
					Duration.create(0, TimeUnit.SECONDS),
					Duration.create(1, TimeUnit.MINUTES),
					new Runnable() {
						public void run() {
							// If running in server mode; just report whether the Pipeline engine is running or not
							if ("server".equals(controllers.Application.deployment())) {
								String endpoint = Setting.get("dp2ws.endpoint");
								if (endpoint == null || !Alive.isAlive(endpoint)) {
									Setting.set("dp2fwk.state","STOPPED");
									NotificationConnection.pushAll(new Notification("dp2fwk.state", "STOPPED"));
									
								} else {
									Setting.set("dp2fwk.state","RUNNING");
									NotificationConnection.pushAll(new Notification("dp2fwk.state", "RUNNING"));
								}
							}
							
							// If running in desktop mode; also restart DP2 automatically if it crashes
							else if ("desktop".equals(controllers.Application.deployment())) {
								String dp2fwkDir = Setting.get("dp2fwk.dir");
								
								if (dp2fwkDir == null || "".equals(dp2fwkDir))
									return;
								
								if (!Alive.isAlive(controllers.Application.DEFAULT_DP2_ENDPOINT_LOCAL)) {
									Logger.info("Attempting to start the Pipeline engine...");
									Setting.set("dp2fwk.state","STARTING");
									NotificationConnection.pushAll(new Notification("dp2fwk.state", "STARTING"));
									int exitValue = CommandExecutor.executeCommandWithWorker(controllers.Application.DP2_START, new File(dp2fwkDir, "cli"), 20000L);
									if (exitValue != 0) {
										Setting.set("dp2fwk.state","RUNNING");
										NotificationConnection.pushAll(new Notification("dp2fwk.state", "RUNNING"));
										Logger.info("Started the Pipeline engine");
									} else {
										Setting.set("dp2fwk.state","STOPPED");
										Logger.info("Failed to start the Pipeline engine");
									}
									return;
								}
							}
							
						}
					}
					);
		
//		}
	}

//	@Override
	public void onStop(Application app) {
		// Application shutdown...
		if ("server".equals(controllers.Application.deployment()))
			return;
		
		String dp2fwkDir = Setting.get("dp2fwk.dir");
		
		if (dp2fwkDir == null || "".equals(dp2fwkDir))
			return;
		
		if (Alive.isAlive(controllers.Application.DEFAULT_DP2_ENDPOINT_LOCAL)) {
			CommandExecutor.executeCommandWithWorker(controllers.Application.DP2_HALT, new File(dp2fwkDir, "cli"), 20000L);
		}
	}

}