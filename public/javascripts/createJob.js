var Job = {
	debug: false,
	uploads: {},
	uploadListeners: [],
	submitListeners: [],
	
	onNewUpload: function(listener) {
		Job.uploadListeners.push(listener);
	},
	
	onSubmit: function(listener) {
		Job.submitListeners.push(listener);
	},
	
	submit: function() {
		var uploads = [];
		for (var id in Job.uploads) uploads.push(Job.uploads[id].id);
		$("#uploads").attr("value",uploads.join());
		
		for (var i = 0; i < Job.submitListeners.length; i++) {
			Job.submitListeners[i]();
		}
	}
};

/* Update the Job object when push notifications of type "uploads" arrive */
Notifications.listen("uploads", function(notification) {
	Job.uploads[notification.id] = notification;
	for (var n = 0; n < Job.uploadListeners.length; n++) {
		Job.uploadListeners[n](notification.fileset, notification.id);
	}
});