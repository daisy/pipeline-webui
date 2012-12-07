// based on http://stackoverflow.com/questions/11/calculating-relative-time
function fuzzyDate(date) {
	var SECOND = 1000;
	var MINUTE = 60 * SECOND;
	var HOUR = 60 * MINUTE;
	var DAY = 24 * HOUR;
	var WEEK = 7 * DAY;
	var MONTH = 30 * DAY;
	var YEAR = 365.25 * DAY;

	var now = new Date();
	var delta = now.getTime() - date.getTime();

	if (delta < 0) {
	  return "not yet";
	}
	if (delta < 1 * MINUTE) {
	  var seconds = Math.round(delta / SECOND);
	  return seconds == 0 ? "just now" : seconds == 1 ? "one second ago" : seconds + " seconds ago";
	}
	if (delta < 2 * MINUTE) {
	  return "a minute ago";
	}
	if (delta < 45 * MINUTE) {
	  return Math.round(delta / MINUTE) + " minutes ago";
	}
	if (delta < 90 * MINUTE) {
	  return "an hour ago";
	}
	if (delta < 24 * HOUR) {
	  return Math.round(delta / HOUR) + " hours ago";
	}
	if (delta < 48 * HOUR) {
	  return "yesterday";
	}
	if (delta < 7 * DAY) {
	  return Math.round(delta / DAY) + " days ago";
	}
	if (delta < 30 * DAY) {
	  var weeks = Math.round(delta / WEEK);
	  return weeks <= 1 ? "one week ago" : weeks + " weeks ago";
	}
	if (delta < 12 * MONTH) {
	  var months = (now.getYear()-date.getYear())*12 + now.getMonth()-date.getMonth();
	  return months <= 1 ? "one month ago" : months + " months ago";
	}
	else {
	  var years = Math.round(delta / YEAR);
	  return years <= 1 ? "one year ago" : years + " years ago";
	}
}

/** Returns the return value of window.setInterval(… , 1min) */
function updateFuzzy(id, date) {
	// now
	document.getElementById(id).innerHTML = fuzzyDate(new Date(date.getTime()));
	
	// every second the first minute
	var delta = new Date().getTime() - date.getTime();
	if (0 <= delta && delta < 60000) {
		for (var t = 1000; t < 60000; t += 1000) {
			window.setTimeout(function(){
				document.getElementById(id).innerHTML = fuzzyDate(new Date(date.getTime()));
			}, t);
		}
	}
	
	// every minute
	return window.setInterval(function(){
		document.getElementById(id).innerHTML = fuzzyDate(new Date(date.getTime()));
	}, 60000);
}