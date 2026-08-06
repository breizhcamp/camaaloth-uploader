$(function() {
	$('.yt-auth-link').each(function () {
		var link = $(this);
		link.attr('href', link.attr('href') + "?baseUrl=" + encodeURIComponent(window.location.href));
	});

	$('#select-playlist').on('change', function () {
		$('#form-playlist').submit();
	});

	// only rendered when the YouTube panel is connected but unusable
	var problem = document.getElementById('yt-problem-modal');
	if (problem) {
		new bootstrap.Modal(problem).show();
	}
});

angular.module('videosApp', [])
.controller('VideoListCtrl', function($scope) {
	// -----       WebSockets     ------
	var socket = new SockJS("/stomp");
	var stompClient = Stomp.over(socket);

	// Récupérer l'état de la playlist depuis le template
	$scope.hasPlaylist = window.hasPlaylist || false;
	$scope.currentPlaylist = window.currentPlaylist || null;
	$scope.batches = window.batches || [];

	// pourcentage d'avancement d'un lot, pour la largeur de la barre
	$scope.percent = function(batch) {
		if (!batch.total) return 0;
		return Math.round(batch.done * 100 / batch.total);
	}

	$scope.isRunning = function(id) {
		for (var i = 0; i < $scope.batches.length; i++) {
			if ($scope.batches[i].id === id) return true;
		}
		return false;
	}

	stompClient.connect({}, function() {

		// avancement des opérations longues
		stompClient.subscribe("/batch", function(msg) {
			if (msg.command === "MESSAGE" && msg.body) {
				var batches = JSON.parse(msg.body);
				$scope.$apply(function() {
					$scope.batches = batches;
				});
			}
		});

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
								video.descriptionStatus = body.descriptionStatus;
								video.thumbnailStatus = body.thumbnailStatus;
								video.thumbnail = body.thumbnail;
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

	// ----- État : une icône par élément, colorée selon son avancement -----

	var PUSH_LABELS = {
		NOT_STARTED: 'à envoyer',
		IN_PROGRESS: 'en cours',
		DONE: 'envoyé',
		FAILED: 'en erreur'
	};

	var VIDEO_LABELS = {
		NOT_STARTED: 'Vidéo : à envoyer',
		WAITING: 'Vidéo : en attente',
		INITIALIZING: 'Vidéo : initialisation',
		IN_PROGRESS: 'Vidéo : envoi en cours',
		THUMBNAIL: 'Vidéo : envoi de la miniature',
		DONE: 'Vidéo : envoyée',
		FAILED: 'Vidéo : en erreur'
	};

	// ramène l'état d'un upload sur les quatre mêmes valeurs que les poussées
	$scope.videoState = function(video) {
		switch (video.status) {
			case 'DONE': return 'DONE';
			case 'FAILED': return 'FAILED';
			case 'NOT_STARTED': return 'NOT_STARTED';
			default: return 'IN_PROGRESS';
		}
	}

	$scope.videoLabel = function(video) {
		return VIDEO_LABELS[video.status] || video.status;
	}

	$scope.pushLabel = function(status) {
		return PUSH_LABELS[status] || PUSH_LABELS.NOT_STARTED;
	}

	$scope.iconClass = function(status) {
		switch (status) {
			case 'DONE': return 'text-success';
			case 'FAILED': return 'text-danger';
			case 'IN_PROGRESS': return 'text-primary';
			default: return 'text-muted opacity-50';
		}
	}

	// la petite pastille collée à l'icône, qui dit l'état sans dépendre de la couleur
	$scope.badgeClass = function(status) {
		switch (status) {
			case 'DONE': return 'fa-check text-success';
			case 'FAILED': return 'fa-xmark text-danger';
			case 'IN_PROGRESS': return 'fa-spinner fa-spin text-primary';
			default: return 'fa-minus text-muted opacity-50';
		}
	}

	// combien de vidéos en ligne attendent encore cet envoi, ce que le bouton global traitera
	$scope.pending = function(field) {
		var count = 0;
		var videos = $scope.videos || [];
		for (var i = 0; i < videos.length; i++) {
			if (videos[i].youtubeId && videos[i][field] !== 'DONE') count++;
		}
		return count;
	}

	$scope.syncDescription = function(video) {
		stompClient.send('/videos/syncDescription', {}, video.dirName);
	}

	$scope.syncThumbnail = function(video) {
		stompClient.send('/videos/syncThumbnail', {}, video.dirName);
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
