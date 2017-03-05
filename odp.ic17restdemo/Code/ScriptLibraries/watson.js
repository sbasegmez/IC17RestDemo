function ask(questionId) {
	$('button[data-question="#'+questionId+'"]').button('loading');
	watson.askWatson(questionId).addCallback(function(resp) {
		renderReply(questionId, resp);
	});
}

function renderReply(questionId, resp) {
	// No need to reset, Watson will not change his answer!
	//	$('button[data-question="#'+questionId+'"]').button('reset');
	
	try {
		var response = XSP.fromJson(resp);
		var classes = response.classes;
		var txt = '<ul class="list-group center-block" style="width:50%">';
		
		for(idx in classes) {
			if(idx>4) break;
			
			var cls = classes[idx];
			var confStyleClass = "label pull-right";
			if(cls.confidence>0.4) {
				confStyleClass += " label-success";
			} else if(cls.confidence>0.2) {
				confStyleClass += " label-warning";
			} else {
				confStyleClass += " label-default";
			}
			
			txt += '<li class="list-group-item">';
			txt += '<span class="'+confStyleClass+'">' + (cls.confidence * 100).toFixed(2) + '%</span>';
			txt += '<strong>' + cls['class_name'] + '</strong>';
			txt += '</li>';
		}

		txt += '</ul>';
		
		$('button[data-question="#'+questionId+'"]').hide();
		$('div[data-question="#'+questionId+'"] .panel-body').html(txt);
		$('div[data-question="#'+questionId+'"]').collapse('show');

	} catch(e) {
		alert("IBM Watson couldn't reply back. Blame the Wi-fi!");
		console.log(e);
	}
}