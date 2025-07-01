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

	// Récupérer l'état de la playlist depuis le template
	$scope.hasPlaylist = window.hasPlaylist || false;
	$scope.currentPlaylist = window.currentPlaylist || null;

	stompClient.connect({}, function() {

		stompClient.subscribe("/videos", function(msg) {
			if (msg.command === "MESSAGE" && msg.body) {
				var body = JSON.parse(msg.body);

				$scope.$apply(function() {
					if (angular.isArray(body)) {
							$scope.videos = body;
							$scope.loaded = true;

					} else if (body.eventId) {
						for (var i = 0; i < $scope.videos.length; i++) {
							var video = $scope.videos[i];
							if (video.eventId === body.eventId) {
								video.progression = body.progression;
								video.status = body.status;
								video.youtubeId = body.youtubeId;
							}
						}
					}
				});
			}
		});
	});

	$scope.upload = function(video) {
		// Vérifier qu'une playlist est sélectionnée
		if (!$scope.hasPlaylist) {
			alert('Veuillez sélectionner une playlist avant de lancer l\'upload.');
			return;
		}
		
		stompClient.send('/videos/upload', {}, video.dirName);
	}

	$scope.uploadAll = function() {
		// Vérifier qu'une playlist est sélectionnée
		if (!$scope.hasPlaylist) {
			alert('Veuillez sélectionner une playlist avant de lancer l\'upload de toutes les vidéos.');
			return false;
		}
		
		return true;
	}

	// Fonction pour vérifier si l'upload est disponible
	$scope.canUpload = function() {
		return $scope.hasPlaylist;
	}
});
