var Job = {
	debug: false,
	uploads: {},
	uploadListeners: [],
	validators: [],
	
	onNewUpload: function(listener) {
		Job.uploadListeners.push(listener);
		for (id in Job.uploads) {
			listener(Job.uploads[id].fileset, Job.uploads[id].id);
		}
	},
	
	onValidate: function(listener) {
		Job.validators.push(listener);
	},
	
	submit: function() {
		var uploads = [];
		for (var id in Job.uploads) uploads.push(Job.uploads[id].id);
		$("input[name=uploads]").attr("value",uploads.join());
		
		var result = {valid: true, messages: []};
		
		for (var i = 0; i < Job.validators.length; i++) {
			var thisResult = Job.validators[i]();
			result.valid = result.valid && thisResult.valid; 
			for (var j = 0; j < thisResult.messages.length; j++) {
				result.messages.push(thisResult.messages[j]);
			}
		}
		
		// TODO: display validation messages
		
		return result.valid;
	},

	upload: function(fileset, id) {
		for (var n = 0; n < Job.uploadListeners.length; n++) {
			Job.uploadListeners[n](fileset, id);
		}
	},

	prettySize: function(bytes) {
		if (bytes < 1000) return bytes+" B";
		if (bytes < 1000000) return (Math.round(bytes/100)/10).toLocaleString()+" kB";
		if (bytes < 1000000000) return (Math.round(bytes/100000)/10).toLocaleString()+" MB";
		if (bytes < 1000000000000) return (Math.round(bytes/100000000)/10).toLocaleString()+" GB";
		return (Math.round(bytes/100000000000)/10).toLocaleString()+" TB";
	}
};

/* Update the Job object when push notifications of type "uploads" arrive */
Notifications.listen("uploads", function(notification) {
	Job.uploads[notification.id] = notification;
	for (var n = 0; n < Job.uploadListeners.length; n++) {
		Job.uploadListeners[n](notification.fileset, notification.id);
	}
});