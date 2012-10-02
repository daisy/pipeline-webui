var Job = {
	debug: false,
	uploads: {},
	uploadListeners: [],
	validators: [],
	
	onNewUpload: function(listener) {
		Job.uploadListeners.push(listener);
	},
	
	onValidate: function(listener) {
		Job.validators.push(listener);
	},
	
	submit: function() {
		var uploads = [];
		for (var id in Job.uploads) uploads.push(Job.uploads[id].id);
		$("#uploads").attr("value",uploads.join());
		
		var result = {valid: true, messages: []};
		
		for (var i = 0; i < Job.validators.length; i++) {
			var thisResult = Job.validators[i]();
			result.valid = result.valid && thisResult.valid; 
			for (var j = 0; j < thisResult.messages.length; j++) {
				result.messages.push(thisResult.messages[j]);
			}
		}
		
		// TODO: display messages
		
		return result.valid;
	}
};

/* Update the Job object when push notifications of type "uploads" arrive */
Notifications.listen("uploads", function(notification) {
	Job.uploads[notification.id] = notification;
	for (var n = 0; n < Job.uploadListeners.length; n++) {
		Job.uploadListeners[n](notification.fileset, notification.id);
	}
});