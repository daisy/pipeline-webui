var Job = {
	uploading: {},
	uploads: {},
	fileset: [],
	updateFormFileParameter: function() {
		var file = {uploads:[],inputs:{}};
		for (var uploadId in Job.uploads) file.uploads.push(uploadId);
		for (var f = 0; f < Job.fileset.length; f++) {
			var currentFile = Job.fileset[f];
			var selected = parseInt(currentFile.selected);
			if (!isNaN(selected) && selected >= 0) {
				var inputPort = Job.inputPorts[selected];
				if (typeof file.inputs[inputPort.name] !== "object") {
					file.inputs[inputPort.name] = [];
				}
				file.inputs[inputPort.name].push(currentFile.href);
			}
		}
		$('#files').val(JSON.stringify(file));
	},
	moveUp: function(pos) {
		if (pos <= 0) return;
		for (var nextIsXml = false; pos-1 >= 0 && !nextIsXml; pos--) {
			nextIsXml = true;
			var element = Job.fileset.splice(pos-1,1)[0];
	    	Job.fileset.splice(pos,0,element);
	    	if (!Job.isXmlContentType(Job.fileset[pos].contentType)) {
	    		nextIsXml = false;
	    	}
    	}
    	Job.refreshFiles();
	},
	moveDown: function(pos) {
		if (pos >= Job.fileset.length-1) return;
		for (var nextIsXml = false; pos+1 < Job.fileset.length && !nextIsXml; pos++) {
			nextIsXml = true;
			var element = Job.fileset.splice(pos,1)[0];
	    	Job.fileset.splice(pos+1,0,element);
	    	if (!Job.isXmlContentType(Job.fileset[pos].contentType)) {
	    		nextIsXml = false;
	    	}
    	}
    	Job.refreshFiles();
   	},
	refreshUploading: function() {
		var rows = $('#filetable tr.filetable-uploading');
		var previousSibling = $('#filetable tr.filetable-headline').last();
		
		// remove finished uploads
		$(rows).each(function(i,tr) {
			var trFilename = $(tr).data("filename");
			var found = false;
			for (var filename in Job.uploading) {
				if (filename === trFilename) {
					found = true;
					break;
				}
			}
			if (!found) {
				$(tr).remove();
			}
		});
		if ($(rows).size() > 0)
			previousSibling = $(rows).last();
		
		// add unfinished uploads
		for (var filename in Job.uploading) {
			var found = false;
			for (var i = 0 ; i < $(rows).size(); i++) {
				if ($($(rows).get(i)).data("filename") === filename) {
					found = true;
					break;
				}
			}
			
			if (!found) {
				var uploading = Job.uploading[filename];
				console.log("DEBUG: Math.floor(("+uploading.total+"==0?0:"+uploading.loaded+"/"+uploading.total+")*100));");
				var element = $('<tr class="filetable-uploading">'+
									'<td><button title="Cancel upload" onclick="alert(\'TODO\');"><i class="icon-stop"></i> Cancel</button></td>'+
									'<td>'+uploading.href+'</td>'+
									'<td colspan="2">&nbsp;</td>'+
									'<td colspan="'+(Job.inputPorts.length+1)+'">'+
										'<div class="progress progress-info progress-striped active" role="progressbar" aria-valuemin="0" aria-valuenow="0" aria-valuemax="100">'+
											'<div class="bar" style="width: 0%;">'+Math.floor((uploading.total==0?0:uploading.loaded/uploading.total)*100)+'%</div>'+
										'</div>'+
									'</td>'+
								'</tr>');
				previousSibling.after(element);
				$(element).data("filename",filename);
				previousSibling = element;
			}
		}
		
		// show table if hidden
		if ($('.filetable-headline').hasClass('hidden'))
			$('.filetable-headline').removeClass('hidden');
	},
	refreshUploaded: function() {
		var rows = $('#filetable tr.filetable-uploaded');
		var previousSibling = $('#filetable tr.filetable-headline, #filetable tr.filetable-uploading').last();
		
		// remove deleted uploads
		$(rows).each(function(i,tr) {
			var trUploadId = $(tr).data("upload-id");
			var found = false;
			for (var uploadId in Job.uploads) {
				if (uploadId === trUploadId) {
					found = true;
					break;
				}
			}
			if (!found) {
				$(tr).remove();
			}
		});
		if ($(rows).size() > 0)
			previousSibling = $(rows).last();
		
		// add finished uploads
		for (var uploadId in Job.uploads) {
			if (Job.isXmlContentType(Job.uploads[uploadId].contentType))
				continue;
			
			var found = false;
			for (var i = 0 ; i < $(rows).size(); i++) {
				if ($($(rows).get(i)).data("upload-id") === uploadId) {
					found = true;
					break;
				}
			}
			if (!found) {
				var uploaded = Job.uploads[uploadId];
				var totalSize = uploaded.total;
				if (totalSize > Math.pow(2,40)) totalSize = Math.round(totalSize*100 / Math.pow(2,40))/100 + " TiB";
				else if (totalSize > Math.pow(2,30)) totalSize = Math.round(totalSize*100 / Math.pow(2,30))/100 + " GiB";
				else if (totalSize > Math.pow(2,20)) totalSize = Math.round(totalSize*100 / Math.pow(2,20))/100 + " MiB";
				else if (totalSize > Math.pow(2,10)) totalSize = Math.round(totalSize*100 / Math.pow(2,10))/100 + " kiB";
				else totalSize = totalSize + " TiB";
				var element = $('<tr class="filetable-uploaded"><td><button title="Remove uploaded file" onclick="alert(\'TODO: remove '+uploadId+'\');"><i class="icon-minus"></i> Remove</button></td><td>'+uploaded.href+'</td><td colspan="2">&nbsp;</td><td colspan="'+(Job.inputPorts.length+1)+'">'+totalSize+'</td></tr>');
				previousSibling.after(element);
				$(element).data("upload-id",uploadId);
				previousSibling = element;
			}
		}
		
		// show table if hidden
		if ($('.filetable-headline').hasClass('hidden'))
			$('.filetable-headline').removeClass('hidden');
	},
	refreshNonXml: function() {
		var rows = $('#filetable tr.filetable-nonxml');
		var previousSibling = $('#filetable tr.filetable-headline, #filetable tr.filetable-uploading, #filetable tr.filetable-uploaded').last();
		
		var nonXmlCount = 0;
		for (var f = 0; f < Job.fileset.length; f++) {
			var file = Job.fileset[f];
			if (!Job.isXmlContentType(file.contentType)) {
				nonXmlCount++;
			}
		}
		
		// remove non-xml count
		if (nonXmlCount === 0) {
			$(rows).remove();
		
		// update non-xml count
		} else if ($(rows).size() > 0) {
			var td = $(rows).find("td").get(1);
			if (td !== null)
				$(td).html(nonXmlCount+" non-XML files");
		
		// add non-xml count
		} else {
			var element = $('<tr class="filetable-nonxml"><td>&nbsp;</td><td>'+nonXmlCount+' non-XML files</td><td colspan="2">&nbsp;</td><td><input type="radio" name="nonxml" value="nonxml" title="Context"</td><td colspan="'+Job.inputPorts.length+'">&nbsp;</td></tr>');
			$(element).find("input[type=radio]").each(function(i,e) { e.checked = true; e.disabled = true; });
			previousSibling.after(element);
		}
		
		// show table if hidden
		if ($('.filetable-headline').hasClass('hidden'))
			$('.filetable-headline').removeClass('hidden');
	},
	refreshFiles: function() {
		$('#filetable tr.filetable-file').remove();
		var previousSibling = $('#filetable tr.filetable-headline, #filetable tr.filetable-uploading, #filetable tr.filetable-uploaded, #filetable tr.filetable-nonxml, #filetable').last();
		
		// TODO: fix inefficient remove()
		
		for (var f = 0; f < Job.fileset.length; f++) {
			var file = Job.fileset[f];
			if (Job.isXmlContentType(file.contentType)) {
				var removable = '<td>&nbsp;</td>';
				if (typeof Job.uploads[file.uploadId] !== 'undefined' && typeof Job.uploads[file.uploadId].contentType !== 'undefined' && Job.uploads[file.uploadId].contentType.substring(0,15) !== "application/zip")
					removable = '<td><button title="Remove uploaded file" onclick="alert(\'TODO: remove '+file.uploadId+'\')"><i class="icon-minus"></i> Remove</button></td>';
				var moveUp = '<td><button title="Move file up" onclick="Job.moveUp('+f+')"><i class="icon-arrow-up"></i></button></td>';
				var moveDown = '<td><button title="Move file down" onclick="Job.moveDown('+f+')"><i class="icon-arrow-down"></i></button></td>';
				var selectable = '<td><input type="radio" name="file-'+file.id+'" value="context" title="Context" onclick="Job.fileset['+f+'].selected=\'context\';Job.updateFormFileParameter();"/></td>';
				for (var i = 0; i < Job.inputPorts.length; i++) {
					selectable += "<td><input type='radio' name='file-"+file.id+"' value='"+i+"' title='"+Job.inputPorts[i].desc+"' onclick='Job.fileset["+f+"].selected=\""+i+"\";Job.updateFormFileParameter();'/></td>";
				}
				var element = $('<tr class="filetable-file">'+removable+'<td>'+file.href+'</td>'+moveUp+moveDown+selectable+'</tr>');
				$(element).find("input[type=radio]").each(function(i,e) { if (e.value === file.selected) { e.checked = true; } });
				previousSibling.after(element);
				previousSibling = element;
			}
		}
		$($('#filetable tr.filetable-file').last().children()[3]).html('');
		$($('#filetable tr.filetable-file').first().children()[2]).html('');
		Job.updateFormFileParameter();
		if ($('.filetable-headline').hasClass('hidden'))
			$('.filetable-headline').removeClass('hidden');
	},
	refreshMoreXml: function() {
		$('#filetable tr.filetable-morexml').remove();
		
		// TODO: fix inefficient remove()
		
		var previousSibling = $('#filetable tr.filetable-headline, #filetable tr.filetable-uploading, #filetable tr.filetable-uploaded, #filetable tr.filetable-nonxml, #filetable tr.filetable-file').last();
		if ($('.filetable-headline').hasClass('hidden'))
			$('.filetable-headline').removeClass('hidden');
		// TODO
	},
	isXmlContentType: function(contentType) {
		if (typeof contentType === 'undefined') return false;
		return (contentType === "application/xml" || contentType === "text/xml"
				|| contentType.indexOf("+xml", contentType.length - "+xml".length) !== -1);	
	},
	removeFileHoverClassEventElement: null,
	removeFileHoverClassEvent: null,
	removeFileHoverClassEventTimer: function() {
		if (Job.removeFileHoverClassEventElement === null)
			$("body").removeClass("file-hover");
		else
			Job.removeFileHoverClassEvent = window.setTimeout(Job.removeFileHoverClassEventTimer,100); // using a timer to avoid flickering
	}
};

// Event handlers for drag-and-drop CSS effect
$(function(){
	$(".body").bind('dragstart dragenter dragover', function (e) {
		if (typeof e.originalEvent !== "undefined"
			&& typeof e.originalEvent.dataTransfer !== "undefined"
			&& typeof e.originalEvent.dataTransfer.files !== "undefined") {
			
			window.clearTimeout(Job.removeFileHoverClassEvent);
			Job.removeFileHoverClassEventElement = e.srcElement;
			$("body").addClass("file-hover");
		}
	});
	
	$(".body").bind('dragleave drop dragend', function (e) {
		if (typeof e.originalEvent !== "undefined"
			&& typeof e.originalEvent.dataTransfer !== "undefined"
			&& typeof e.originalEvent.dataTransfer.files !== "undefined") {
			
			window.clearTimeout(Job.removeFileHoverClassEvent);
			if (Job.removeFileHoverClassEventElement == e.srcElement)
				Job.removeFileHoverClassEventElement = null;
			Job.removeFileHoverClassEvent = window.setTimeout(Job.removeFileHoverClassEventTimer,100); // using a timer to avoid flickering
		}
	});
});

// --------------------------------------------------------------------------------------------

/**
 * Instantiate file upload button
 */
$(function(){
	var uploader = new qq.FileUploader({
	    element: document.getElementById('file-uploader'),
	    action: '/uploads',
	    encoding: 'multipart',
    	debug: true,
    	onSubmit: function(id, fileName) {
    		console.log("["+fileName+"]: submit");
    		Job.uploading[fileName] = {href: fileName, loaded: 0};
        	Job.refreshUploading();
        	return true;
    	},
    	onProgress: function(id, fileName, loaded, total) {
        	Job.uploading[fileName].total = total;
    		Job.uploading[fileName].loaded = loaded;
    		var progressbar = $("tr.filetable-uploading").filter(function(){return $(this).data("filename")===fileName;}).find("div[role=progressbar]");
    		var bar = $(progressbar).find("div.bar");
    		$(progressbar).attr("aria-valuenow",Math.floor(100*(total==0?0:loaded/total)));
    		$(bar).css("width", (100*(total==0?0:loaded/total))+"%");
    		$(bar).html(Math.floor(100*(total==0?0:loaded/total))+"%");
        	Job.refreshUploading();
    	},
    	onComplete: function(id, fileName, responseJSON) {
    		console.log("done");
            Job.uploads[responseJSON.uploadId] = {href: fileName, contentType: Job.uploading[fileName].contentType, total: Job.uploading[fileName].total, fileset: []};
            console.log(id+" | "+fileName+" | "+responseJSON);
            console.log(responseJSON);
            $.ajax({
            	url: '/uploads/'+responseJSON.uploadId,
            	dataType: 'json',
            	context: {uploadId:responseJSON.uploadId},
            	error: function(jqXHR, textStatus, errorThrown) {
            		console.log("AJAX error:");
            		console.log(textStatus);
            		console.log(errorThrown);
            		delete Job.uploading[Job.uploads[responseJSON.uploadId].href]; // TODO: show error in place of progressbar
            	},
            	success: function(data, textStatus, jqXHR) {
            		console.log('retrieved /uploads/'+responseJSON.uploadId+" successfully")
            		Job.uploads[responseJSON.uploadId].fileset = data;
            		$(data).each($.proxy(function(i, file) {
            			var port = "context";
            			if (Job.isXmlContentType(file.contentType)) {
            				for (var i = 0; i < Job.inputPorts.length; i++) {
                				if (file.contentType === Job.inputPorts[i].mediaType) {
                					port = ""+i;
                					break;
                				}
                			}
            				if (port === "context") {
                				for (var i = 0; i < Job.inputPorts.length; i++) {
	                				if (Job.inputPorts[i].mediaType === "application/xml") {
	                					port = ""+i;
	                					break;
	                				}
	                			}
            				}
            			}
            			Job.fileset.push({id: Job.fileset.length, href: file.href, contentType: file.contentType, uploadId: responseJSON.uploadId, port: "", selected: port});
        			},this));
        			delete Job.uploading[Job.uploads[responseJSON.uploadId].href];
        		},
        		complete: function(jqXHR, textStatus) {
            		console.log('request complete for /uploads/'+responseJSON.uploadId);
            		Job.refreshUploaded();
		            Job.refreshNonXml();
        			Job.refreshFiles();
        			Job.refreshMoreXml();
        			Job.refreshUploading();
            	}
            });
            console.log("["+fileName+","+responseJSON.uploadId+"]: "+".queue refreshUploading");
            Job.refreshUploading();
    	},
    	onCancel: function(id, fileName) {
    		alert("TODO: cancel is not implemented.");
    	},
    	messages: {
    	    // TODO i18n: error messages, see qq.FileUploaderBasic for content            
    	},
    	showMessage: function(message){ alert(message); }
	});
});