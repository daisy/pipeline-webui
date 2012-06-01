function toggleRadio(idPrefix, state, defaultState) {
	$('#'+idPrefix+'-value').attr('value',state?'true':'false');
	$('#'+idPrefix+'-on').attr('aria-checked',state?'true':'false');
	$('#'+idPrefix+'-off').attr('aria-checked',state?'false':'true');
	if (state) {
		$('#'+idPrefix+'-on').addClass('active');
		$('#'+idPrefix+'-off').removeClass('active');
	} else {
		$('#'+idPrefix+'-on').removeClass('active');
		$('#'+idPrefix+'-off').addClass('active');
	}
	toggleChanged(idPrefix, state?'true':'false', defaultState);
}

function toggleChanged(idPrefix, value, defaultValue) {
	if (value === defaultValue)
		document.getElementById(idPrefix+'Group').style.backgroundColor='#FFF';
	else
		document.getElementById(idPrefix+'Group').style.backgroundColor='#DDF';
}