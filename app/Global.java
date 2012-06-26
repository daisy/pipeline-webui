import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.codehaus.jackson.JsonNode;

import akka.util.Duration;
import models.Job;
import models.Setting;
import models.Upload;
import models.User;
import models.Notification;
import play.*;
import play.libs.Akka;
import play.mvc.WebSocket;

public class Global extends GlobalSettings {

	@Override
	public synchronized void onStart(Application app) {
		// Application has started...
		
		User.notificationQueues = new ConcurrentHashMap<Long,List<Notification>>();
		User.websockets = new ConcurrentHashMap<Long,List<WebSocket.Out<JsonNode>>>();
		
		// Push "heartbeat" notifications (keeping the push notification connections alive). Not sure how well this scales...
		Akka.system().scheduler().schedule(
				Duration.create(0, TimeUnit.SECONDS),
				Duration.create(1, TimeUnit.SECONDS),
				new Runnable() {
					public void run() {
						Date timeoutDate = new Date(new Date().getTime()-10*60*1000);
						synchronized (User.notificationQueues) {
							for (Long userId : User.notificationQueues.keySet()) {
								while (User.notificationQueues.get(userId).size() > 0 && User.notificationQueues.get(userId).get(0).getTime().before(timeoutDate)) {
									User.notificationQueues.get(userId).remove(0);
								}
								
								// TODO: Heartbeats keeps pumping even when the user is not logged in. Could be a problem if there are many users.
								List<Notification> notificationQueue = User.notificationQueues.get(userId);
								if (notificationQueue.isEmpty()) {
									User.push(userId, new Notification("heartbeat", null));
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
						if (Setting.get("jobs.deleteAfterDuration") == null || "0".equals(Setting.get("jobs.deleteAfterDuration")))
							Setting.set("jobs.deleteAfterDuration", ""+(10*60*1000));//TODO: use widget in admin settings instead
//							return;
						
						Date timeoutDate = new Date(new Date().getTime() - Long.parseLong(Setting.get("jobs.deleteAfterDuration")));
						
						List<Job> jobs = Job.find.all();
						for (Job job : jobs) {
							if (job.finished != null && job.finished.before(timeoutDate)) {
								Logger.info("Deleting old job: "+job.id+" ("+job.nicename+")");
								job.delete();
							}
						}
						
						List<Upload> uploads = Upload.find.all();
						for (Upload upload : uploads) {
							if (upload.job == null && upload.uploaded.before(timeoutDate)) {
								Logger.info("Deleting old upload: "+upload.id+(upload.getFile()!=null?" ("+upload.getFile().getName()+")":""));
								upload.delete();
							}
						}
					}
				}
				);
		
		// Clean up jobs that no longer exists in the Pipeline 2 framework. This typically happens if the framework is restarted.
		Akka.system().scheduler().schedule(
				Duration.create(1, TimeUnit.MINUTES),
				Duration.create(1, TimeUnit.HOURS),
				new Runnable() {
					public void run() {
						
						List<pipeline2.models.Job> fwkJobs = pipeline2.models.Job.getJobs(pipeline2.Jobs.get(Setting.get("dp2ws.endpoint"), Setting.get("dp2ws.authid"), Setting.get("dp2ws.secret")));
						List<Job> webUiJobs = Job.find.all();
						
						for (Job webUiJob : webUiJobs) {
							boolean exists = false;
							for (pipeline2.models.Job fwkJob : fwkJobs) {
								if (webUiJob.id.equals(fwkJob.id))
									exists = true;
								
								if (!exists) {
									Logger.info("Deleting job that no longer exists in the Pipeline 2 framework: "+webUiJob.id+" ("+webUiJob.nicename+")");
									webUiJob.delete();
								}
							}
						}
					}
				}
				);
	}  

	@Override
	public void onStop(Application app) {
		// Application shutdown...
	}



}