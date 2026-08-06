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

	// ----- Filtrage et tri, entièrement côté navigateur -----

	// l'ordre des états suit l'avancement, pas l'alphabet : trier remonte ce qu'il reste à faire
	var STATE_RANK = {NOT_STARTED: 0, IN_PROGRESS: 1, FAILED: 2, DONE: 3};

	function queryParam(name) {
		var found = new RegExp('[?&]' + name + '=([^&]*)').exec(window.location.search);
		return found ? decodeURIComponent(found[1].replace(/\+/g, ' ')) : '';
	}

	// l'état des filtres vit dans l'url, ce qui rend une vue rechargeable et partageable
	$scope.filters = {
		name: queryParam('nom'),
		video: queryParam('video'),
		description: queryParam('description'),
		thumbnail: queryParam('miniature')
	};
	$scope.sort = {
		field: queryParam('tri') || 'dirName',
		asc: queryParam('sens') !== 'desc'
	};

	// la valeur sur laquelle une colonne filtre et trie
	function valueOf(video, field) {
		if (field === 'dirName') return (video.dirName || '').toLowerCase();
		if (field === 'video') return $scope.videoState(video);
		if (field === 'description') return video.descriptionStatus || 'NOT_STARTED';
		return video.thumbnailStatus || 'NOT_STARTED';
	}

	function matches(video) {
		var name = $scope.filters.name.toLowerCase();
		if (name && valueOf(video, 'dirName').indexOf(name) === -1) return false;

		var columns = ['video', 'description', 'thumbnail'];
		for (var i = 0; i < columns.length; i++) {
			var wanted = $scope.filters[columns[i]];
			if (wanted && valueOf(video, columns[i]) !== wanted) return false;
		}
		return true;
	}

	function compare(a, b) {
		var field = $scope.sort.field;
		if (field === 'dirName') {
			return valueOf(a, field).localeCompare(valueOf(b, field));
		}
		var rank = STATE_RANK[valueOf(a, field)] - STATE_RANK[valueOf(b, field)];
		// à état égal, l'ordre alphabétique garde la liste stable
		return rank !== 0 ? rank : valueOf(a, 'dirName').localeCompare(valueOf(b, 'dirName'));
	}

	$scope.visibleVideos = function() {
		var kept = ($scope.videos || []).filter(matches);
		kept.sort(compare);
		return $scope.sort.asc ? kept : kept.reverse();
	}

	$scope.sortBy = function(field) {
		if ($scope.sort.field === field) $scope.sort.asc = !$scope.sort.asc;
		else { $scope.sort.field = field; $scope.sort.asc = true; }
	}

	$scope.sortIcon = function(field) {
		if ($scope.sort.field !== field) return 'fa-sort text-muted opacity-50';
		return $scope.sort.asc ? 'fa-sort-up' : 'fa-sort-down';
	}

	$scope.clearFilters = function() {
		$scope.filters = {name: '', video: '', description: '', thumbnail: ''};
	}

	$scope.filtering = function() {
		var f = $scope.filters;
		return !!(f.name || f.video || f.description || f.thumbnail);
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
