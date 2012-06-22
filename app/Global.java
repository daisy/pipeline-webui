import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.codehaus.jackson.JsonNode;

import akka.util.Duration;
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
		
		// Not sure how well this scales...:
		Akka.system().scheduler().schedule(
				Duration.create(0, TimeUnit.SECONDS),
				Duration.create(1, TimeUnit.SECONDS),
				new Runnable() {
					public void run() {
						Date timeoutDate = new Date(new Date().getTime()-10*60*1000);
						synchronized (User.notificationQueues) {
							for (Long userId : User.notificationQueues.keySet()) {
								if (User.notificationQueues.get(userId).size() == 0) {
									User.notificationQueues.remove(User.notificationQueues.get(userId));
									continue;
								}
								
								if (User.notificationQueues.get(userId).get(0).getTime().before(timeoutDate)) {
									User.notificationQueues.get(userId).remove(0);
									
								}else{
									List<Notification> notificationQueue = User.notificationQueues.get(userId);
									if (notificationQueue.isEmpty()) {
										User.push(userId, new Notification("heartbeat", null));
									}
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