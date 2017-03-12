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
		});
	});
});