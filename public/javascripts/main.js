
function toggleRadio(idPrefix, state, defaultState) {
	$('#'+idPrefix+'-value').val(state?'true':'false');
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

function toggleMultiRadio(classPrefix, value, defaultValue) {
	$('#'+classPrefix+'-value').val(value);
	$('.'+classPrefix+'-btn').each(function(index, btn) {
		if ($(btn).val() === value) {
			$(btn).addClass('active');
			$(btn).attr('aria-checked','true');
		} else {
			$(btn).removeClass('active');
			$(btn).attr('aria-checked','false');
		}
	});
	toggleChanged(classPrefix, value, defaultValue);
}

function toggleChanged(idPrefix, value, defaultValue) {
	if (value === defaultValue)
		$('#'+idPrefix+'Group').removeClass("changed");
	else
		$('#'+idPrefix+'Group').addClass("changed");
}
