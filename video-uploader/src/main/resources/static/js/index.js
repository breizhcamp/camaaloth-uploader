$(function() {
	var ytAuth = $('#yt-auth');
	ytAuth.attr('href', ytAuth.attr('href') + "?baseUrl=" + encodeURI(window.location.href));

	$('#select-playlist').on('change', function () {
		$('#form-playlist').submit();
	});
});

angular.module('videosApp', [])
.controller('VideoListCtrl', function($scope) {
	// -----       WebSockets     ------
	var socket = new SockJS("/stomp");
	var stompClient = Stomp.over(socket);

	stompClient.connect({}, function() {

		stompClient.subscribe("/videos", function(msg) {
			if (msg.command == "MESSAGE" && msg.body) {
				var body = JSON.parse(msg.body);

				$scope.$apply(function() {
					if (angular.isArray(body)) {
							$scope.videos = body;
							$scope.loaded = true;

					} else if (body.eventId) {
						for (var i = 0; i < $scope.videos.length; i++) {
							var video = $scope.videos[i];
							if (video.eventId == body.eventId) {
								video.progression = body.progression;
								video.status = body.status;
							}
						}
					}
				});
			}
		});
	});

});
