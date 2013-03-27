var Job = {
	// properties
	debug: false,
	uploads: {},
	filesetTypes: [],
	filesetTypeDescriptions: {},

	// listeners
	uploadListeners: [],
	validators: [],
	filesetTypeListeners: [],
	
	// private properties
	_filesetTypeDeterminator: undefined,
	
	onNewUpload: function(listener) {
		Job.uploadListeners.push(listener);
		for (var id in Job.uploads) {
			listener(Job.uploads[id].fileset, Job.uploads[id].id);
		}
	},

	onFilesetTypeUpdate: function(listener) {
		Job.filesetTypeListeners.push(listener);
		var filesets = {};
		for (var i in Job.filesetTypes) {
			var id = Job.filesetTypes[i];
			filesets[id] = {
				type: Job.filesetTypeDescriptions[id].type,
				name: Job.filesetTypeDescriptions[id].name
			};
		}
		listener(filesets);
	},
	
	onValidate: function(listener) {
		Job.validators.push(listener);
	},
	
	submit: function() {
		return true;
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
	},

	determineFilesetType: function() {
		for (var id in Job.filesetTypeDescriptions) {
			var desc = Job.filesetTypeDescriptions[id];
			if (Job._determineFilesetType_both(desc.requirements)) {
				Job.filesetTypes.push(id);
			}
		}
		Job.filesetTypes = $.unique(Job.filesetTypes);

		var filesets = {};
		for (var i in Job.filesetTypes) {
			var id = Job.filesetTypes[i];
			filesets[id] = {
				type: Job.filesetTypeDescriptions[id].type,
				name: Job.filesetTypeDescriptions[id].name
			};
		}
		for (var l in Job.filesetTypeListeners) {
			Job.filesetTypeListeners[l](filesets);
		}
	},
	_determineFilesetType_both: function(tests) {
		for (var t in tests) {
			var test = tests[t];
			if ('either' in test && Job._determineFilesetType_either(test.either) === false) return false;
			if ('both' in test && Job._determineFilesetType_both(test.both) === false) return false;
			if (Job._determineFilesetType_test(test) === false) return false;
		}
		return true;
	},
	_determineFilesetType_either: function(tests) {
		for (var t in tests) {
			var test = tests[t];
			if ('either' in test && Job._determineFilesetType_either(test.either) === true) return true;
			if ('both' in test && Job._determineFilesetType_both(test.both) === true) return true;
			if (Job._determineFilesetType_test(test) === true) return true;
		}
		return false;
	},
	_determineFilesetType_test: function(test) {
		for (var u in Job.uploads) {
			for (var f in Job.uploads[u].fileset) {
				var file = Job.uploads[u].fileset[f];
				var foundMatchingFile = true;
				for (var property in test) {
					var restriction = test[property];
					if (restriction instanceof RegExp) {
						foundMatchingFile = foundMatchingFile && restriction.test(file[property]);
					} else {
						foundMatchingFile = foundMatchingFile && (restriction === file[property]);
					}
				}
				if (foundMatchingFile) return true;
			}
		}
		return false;
	}
};

/* Update the Job object when push notifications of type "uploads" arrive */
Notifications.listen("uploads", function(notification) {
	Job.uploads[notification.id] = notification;
	for (var n = 0; n < Job.uploadListeners.length; n++) {
		Job.uploadListeners[n](notification.fileset, notification.id);
	}
	clearTimeout(Job._filesetTypeDeterminator);
	Job._filesetTypeDeterminator = setTimeout(Job.determineFilesetType,100);
});

Job.filesetTypeDescriptions["daisy202"] = {
	type: "multipart/x.daisy202",
	name: "DAISY 2.02",
	requirements: [
		{ fileName: new RegExp("(^|/)ncc\\.html$","i") }
	]
};

Job.filesetTypeDescriptions["daisy3"] = {
	type: "multipart/x.daisy3",
	name: "DAISY 3",
	requirements: [
		{ contentType: "application/x-dtbncx+xml" },
		{ fileName: new RegExp("(^|/).*\\.smil$","i") }
	]
};

Job.filesetTypeDescriptions["dtbook"] = {
	type: "application/x-dtbook+xml",
	name: "DTBook",
	requirements: [
		{ contentType: "application/x-dtbook+xml" }
	]
};

Job.filesetTypeDescriptions["zedai"] = {
	type: "application/z3998-auth+xml",
	name: "ZedAI",
	requirements: [
		{ contentType: "application/z3998-auth+xml" }
	]
};

Job.filesetTypeDescriptions["epub"] = {
	type: "application/epub+zip",
	name: "EPUB",
	requirements: [
		{ either: [
			{ contentType: "application/epub+zip" },
			{ both: [
				{ contentType: "application/oebps-package+xml" },
				{ either: [
					{ contentType: "text/html" },
					{ contentType: "application/xhtml+xml" }
				] }
			]}
		] }
	]
};
