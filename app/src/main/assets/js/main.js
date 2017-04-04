var GRT = (function() {
    var countdown = 5;

	function executeCommand(key) {
		window.JSInterface.executeCommand(key);
	}

	function onVoiceInactive() {
        var speech = $('.speech');
        if (!speech) return;
        speech.removeClass('ready active error');
    }

	function onVoiceReady() {
	    var speech = $('.speech');
	    if (!speech) return;
	    speech.removeClass('active error');
	    speech.addClass('ready');
	}

	function onVoiceActive() {
	    var speech = $('.speech');
	    if (!speech) return;
	    speech.removeClass('ready error');
	    speech.addClass('active');
	}

	function onVoiceError() {
	    var speech = $('.speech');
	    if (!speech) return;
	    speech.removeClass('ready active');
	    speech.addClass('error');
	}

	function onCommandSelected(key) {
        var button = document.getElementById('btn-' + key);
        button.setAttribute('class', 'selected');
	}

	function playMedia() {
	    var videoPlayer = document.getElementById('videoPlayer');
        videoPlayer.addEventListener("pause", function(e) {
            window.JSInterface.log("Video playback completed.");
	        window.JSInterface.mediaPlaybackComplete();
        }, false);
	    videoPlayer.play();
	}

	function startCountdown() {
	    $(".countdown").pietimer({
	        seconds: 5,
	        color: 'rgba(237,127,0,1.0)',
	        height: 40,
	        width: 40
        });
        $(".countdown").pietimer('start');
	}

	return {
		executeCommand : executeCommand,
		onVoiceInactive : onVoiceInactive,
		onVoiceReady : onVoiceReady,
		onVoiceActive : onVoiceActive,
		onVoiceError : onVoiceError,
		onCommandSelected : onCommandSelected,
		playMedia : playMedia,
		startCountdown : startCountdown
	};
})();
