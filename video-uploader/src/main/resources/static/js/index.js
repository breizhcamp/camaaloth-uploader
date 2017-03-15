$(function() {
	var ytAuth = $('#yt-auth');
	ytAuth.attr('href', ytAuth.attr('href') + "?baseUrl=" + encodeURI(window.location.href));

	$('#select-playlist').on('change', function () {
		$('#form-playlist').submit();
	});



	// -----       WebSockets     ------
	var socket = new SockJS("/stomp");
	var stompClient = Stomp.over(socket);

	stompClient.connect({}, function() {
		stompClient.subscribe("/upload", function(msg) {
			console.log(msg);
			if (msg.command == "MESSAGE" && msg.body) {
				var body = JSON.parse(msg.body);

				if (body.eventId) {
					var progress = body.percent/10 + '%';
					$('#progress-' + body.eventId).width(progress).text(progress);
				}
			}
		});
	});
});