DP2Forms = {
	forms: [],
	validators: {},
	listeners: {},
	lastReport: {},

	onValidationReport: function(formName, listener, data) {
		console.log("adding event listener for validation of "+formName);
		if (!$.isArray(DP2Forms.listeners[formName])) DP2Forms.listeners[formName] = new Array();
		DP2Forms.listeners[formName].push({ fn: listener, data: data});
		if (typeof DP2Forms.lastReport[formName] !== "undefined") listener(lastReport[formName],data);
	},

	startValidation: function(formName, url, fields) {
		console.log("starting validation of "+formName);
		DP2Forms.forms.push(formName);
		DP2Forms.validators[formName] = {
			url: url,
			fields: fields,
			lastValidation: 0,
			lastValidationRequestTime: 0,
			interval: setInterval(DP2Forms._scheduleValidation, 5000, formName)
		};
	},

	stopValidation: function(formName) {
		console.log("stopping validation of "+formName);
		DP2Forms.forms.splice($.inArray(formName,DP2Forms.forms),1)
		clearInterval(DP2Forms.validators[formName]);
		DP2Forms.validators.splice($.inArray(formName,DP2Forms.validators),1)
	},

	/** No error will be reported when the value is the same as `initial`
	  * (used for forms that have not been filled yet). */
	validateAsTextField: function(formName, field, initial) {
		var data = {
			formName: formName,
			field: field,
			initial: initial
		};
		$("#"+formName+"-"+field).on('change keyup', data, function(event){
			$(this).data("text", $(this)[0].value);
			DP2Forms._scheduleValidation(formName);
			if ($(this).data("text") !== $(this)[0].value) {
				setTimeout(function(event){
					if (new Date().getTime() - DP2Forms.validators[event.data.formName].lastValidation >= 1000) {
						console.log("text in form field "+event.data.field+" changed; showing loading animation");
						$("#"+event.data.formName+"-"+event.data.field+"Group").removeClass("success error");
						$("#"+event.data.formName+"-"+event.data.field+"Help").html("");
						$("#"+event.data.formName+"-"+event.data.field+"HelpLoading").show();
					}
				},1000,event);
			}
		});
		DP2Forms.onValidationReport(formName, function(form, data){
			for (var p in DP2Forms.validators[form.data.formName].fields) {
				var field = DP2Forms.validators[form.data.formName].fields[p];
				console.log("report for form field "+field+" received; hiding loading animation");
				$("#"+form.data.formName+"-"+field+"HelpLoading").hide();
				if ($("#"+form.data.formName+"-"+field)[0].value === data.initial) {
					$("#"+form.data.formName+"-"+field+"Group").removeClass("error success");
					$("#"+form.data.formName+"-"+field+"Help").html("");
				} else if (form.errors.hasOwnProperty(field)) {
					$("#"+form.data.formName+"-"+field+"Group").removeClass("success").addClass("error");
					$("#"+form.data.formName+"-"+field+"Help").html(form.errors[field].join("<br/>"));
				} else {
					$("#"+form.data.formName+"-"+field+"Group").removeClass("error").addClass("success");
					$("#"+form.data.formName+"-"+field+"Help").html("");
				}
			}
		}, data);
	},

	disableButtonOnErrors: function(formName, field) {
		var data = {
			formName: formName,
			field: field
		};
		DP2Forms.onValidationReport(formName, function(form, data){
			var errors = false;
			for (var p in DP2Forms.validators[form.data.formName].fields) {
				var field = DP2Forms.validators[form.data.formName].fields[p];
				if (form.errors.hasOwnProperty(field)) {
					errors = true;
					break;
				}
			}
			if (errors) {
				$("#"+data.formName+"-"+data.field).attr("disabled","");
			} else {
				$("#"+data.formName+"-"+data.field).removeAttr("disabled");
			}
		}, data);
	},

	_scheduleValidation: function(formName) {
		if (new Date().getTime() - DP2Forms.validators[formName].lastValidation >= 490)
			DP2Forms._validate(formName);
		else
			setTimeout(function(formName){
				if (new Date().getTime() - DP2Forms.validators[formName].lastValidation >= 490)
					DP2Forms._validate(formName);
			},510,formName);
	},

	_validate: function(formName) {
		console.log("validating "+formName);
		DP2Forms.validators[formName].lastValidation = new Date().getTime();
		DP2Forms.validators[formName].validating = true;
		var form = $("#"+formName+"-form").serializeArray();
		form.push({ name: '_validationRequestTime', value: new Date().getTime() });
		$.post(
			DP2Forms.validators[formName].url,
			$.param(form),
			function(form, textStatus, jqXHR) {
				if (DP2Forms.validators[form.data.formName].lastValidationRequestTime <= parseInt(form.data._validationRequestTime)) {
					DP2Forms.validators[form.data.formName].lastValidationRequestTime = parseInt(form.data._validationRequestTime);
					DP2Forms.validators[form.data.formName].lastValidation = new Date().getTime();
					DP2Forms.validators[form.data.formName].validating = false;
					if ($.isArray(DP2Forms.listeners[form.data.formName])) {
						for (var l in DP2Forms.listeners[formName]) {
							DP2Forms.listeners[formName][l].fn(form, DP2Forms.listeners[formName][l].data);
						}
					}
				}
			},
			"json"
		);
	}
};