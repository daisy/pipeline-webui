function radioToggle(idPrefix, state, defaultState) {
	$('#'+idPrefix+'-value').attr('value',state?'true':'false');
	$('#'+idPrefix+'-on').attr('aria-checked',state?'true':'false');
	$('#'+idPrefix+'-off').attr('aria-checked',state?'false':'true');
	if ((state?'true':'false') === defaultState)
		document.getElementById(idPrefix+'Group').style.backgroundColor='#FFF';
	else
		document.getElementById(idPrefix+'Group').style.backgroundColor='#DDF';
}