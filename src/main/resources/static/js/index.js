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
.controller('VideoListCtrl', function($scope, $timeout) {
	// -----       WebSockets     ------
	var socket = new SockJS("/stomp");
	var stompClient = Stomp.over(socket);

	// Récupérer l'état de la playlist depuis le template
	$scope.hasPlaylist = window.hasPlaylist || false;
	$scope.currentPlaylist = window.currentPlaylist || null;
	$scope.batches = window.batches || [];

	// la phrase à recopier avant de supprimer les métadonnées YouTube, à recopier et non à coller
	$scope.resetPhrase = window.resetPhrase;
	$scope.reset = {confirmation: ''};

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
								video.loudness = body.loudness;
								video.normalized = body.normalized;
								video.hasOriginal = body.hasOriginal;
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

	// ----- Son : mesuré par scripts/normalize.sh, rangé dans metadata.json -----

	var TARGET_I = -23;
	var TARGET_TP = -3;
	// au-delà de 1 LU, l'écart s'entend au passage d'une vidéo à l'autre ; même marge sur le pic,
	// que l'encodage AAC fait légèrement remonter après la normalisation
	var TOLERANCE = 1;

	$scope.loudnessOk = function(loudness) {
		return Math.abs(loudness.integrated - TARGET_I) <= TOLERANCE
			&& loudness.truePeak <= TARGET_TP + TOLERANCE;
	}

	$scope.loudnessLabel = function(video) {
		var loudness = video.loudness;
		if (!video.normalized) return 'Pas encore normalisée : lancer scripts/normalize.sh, seule la version normalisée est envoyée';
		if (!loudness) return 'Normalisée, mais son non mesuré : relancer scripts/normalize.sh';
		return 'Intégré ' + loudness.integrated.toFixed(1) + ' LUFS (cible ' + TARGET_I + '), '
			+ 'true peak ' + loudness.truePeak.toFixed(1) + ' dBFS (cible ' + TARGET_TP + ')';
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
		if (field === 'loudness') return video.loudness ? video.loudness.integrated : null;
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
		if (field === 'loudness') {
			// les vidéos non mesurées d'abord, comme ce qu'il reste à faire sur les états
			var la = valueOf(a, field), lb = valueOf(b, field);
			var diff = la === lb ? 0 : la === null ? -1 : lb === null ? 1 : la - lb;
			return diff !== 0 ? diff : valueOf(a, 'dirName').localeCompare(valueOf(b, 'dirName'));
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

	// clic sur une ligne : la description dans une fenêtre, l'infobulle native tardait trop
	$scope.showDetail = function(video) {
		$scope.detail = video;
		// après le cycle de rendu, sinon la fenêtre s'ouvre sur le contenu précédent
		$timeout(function() {
			bootstrap.Modal.getOrCreateInstance(document.getElementById('video-modal')).show();
		});
	}

	// ----- Navigation d'un talk à l'autre, dans l'ordre de la liste filtrée et triée -----

	// le voisin dans la liste affichée, null en bout de liste
	$scope.neighbor = function(video, step) {
		if (!video) return null;
		var list = $scope.visibleVideos();
		for (var i = 0; i < list.length; i++) {
			if (list[i].dirName === video.dirName) return list[i + step] || null;
		}
		return null;
	}

	function isShown(id) {
		return document.getElementById(id).classList.contains('show');
	}

	$scope.go = function(step) {
		if (isShown('player-modal')) {
			var next = $scope.neighbor($scope.player, step);
			if (!next) return;
			stopPlayers();
			$scope.player = next;
			$scope.detail = next;
			resetTransport();
		} else if (isShown('video-modal')) {
			$scope.detail = $scope.neighbor($scope.detail, step) || $scope.detail;
		}
	}

	document.addEventListener('keydown', function(event) {
		if (!isShown('player-modal') && !isShown('video-modal')) return;
		// la saisie et le slider gardent leurs touches
		var tag = event.target.tagName;
		if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return;

		var key = event.key;
		if (key === 'ArrowLeft' || key === 'ArrowRight') {
			event.preventDefault();
			$scope.$apply(function() { $scope.go(key === 'ArrowLeft' ? -1 : 1); });
		} else if (isShown('player-modal') && key === ' ' && tag !== 'BUTTON') {
			event.preventDefault();
			$scope.$apply($scope.togglePlay);
		} else if (isShown('player-modal') && (key === 'b' || key === 'B')) {
			$scope.$apply($scope.switchPlayer);
		}
	});

	// ----- Lecteurs : l'original et la normalisée, un seul son à la fois -----

	$scope.sides = [
		{key: 'original', label: 'Original', icon: 'fa-microphone', missing: 'Pas de fichier original.'},
		{key: 'normalized', label: 'Normalisée', icon: 'fa-volume-high', missing: 'Pas encore normalisée.'}
	];

	function playerElement(side) {
		return document.getElementById('player-' + side);
	}

	$scope.available = function(side) {
		return !!$scope.player && (side === 'original' ? $scope.player.hasOriginal : $scope.player.normalized);
	}

	// la normalisée par défaut, c'est celle qui part sur YouTube
	function resetTransport() {
		var active = $scope.transport && $scope.available($scope.transport.active)
			? $scope.transport.active
			: ($scope.available('normalized') ? 'normalized' : 'original');
		$scope.transport = {active: active, playing: false, time: 0, duration: 0};
	}
	$scope.transport = {active: 'normalized', playing: false, time: 0, duration: 0};

	function stopPlayers() {
		eachPlayer(function(el) {
			el.pause();
			if (el.meter) {
				el.meter.history = [];
				el.meter.last = undefined;
			}
		});
	}

	// l'original et la normalisée côte à côte, à la place du détail : bootstrap n'empile pas les fenêtres
	$scope.showPlayers = function(video) {
		$scope.player = video;
		resetTransport();
		bootstrap.Modal.getOrCreateInstance(document.getElementById('video-modal')).hide();
		$timeout(function() {
			bootstrap.Modal.getOrCreateInstance(document.getElementById('player-modal')).show();
		});
	}

	// les deux vidéos tournent ensemble, seule celle à l'écoute s'entend : basculer compare le même passage
	$scope.togglePlay = function() {
		var lead = playerElement($scope.transport.active);
		if (!lead) return;
		if (lead.paused) play();
		else eachPlayer(function(el) { el.pause(); });
	}

	function eachPlayer(action) {
		['original', 'normalized'].forEach(function(side) {
			var el = playerElement(side);
			if (el) action(el, side);
		});
	}

	function play() {
		eachPlayer(function(el, side) { meterOf(side); });
		applyGains();
		audioContext.resume();
		var time = playerElement($scope.transport.active).currentTime;
		eachPlayer(function(el) {
			el.currentTime = time;
			el.play();
		});
	}

	// couper par le gain et non par muted : l'analyseur, branché avant, voit encore le son de l'autre
	function applyGains() {
		eachPlayer(function(el, side) {
			if (el.meter) el.meter.gain.gain.value = side === $scope.transport.active ? 1 : 0;
		});
	}

	$scope.listenTo = function(side) {
		if (side === $scope.transport.active || !playerElement(side)) return;
		$scope.transport.active = side;
		applyGains();
		syncTransport();
	}

	$scope.switchPlayer = function() {
		$scope.listenTo($scope.transport.active === 'original' ? 'normalized' : 'original');
	}

	$scope.clickVideo = function(side) {
		if (side === $scope.transport.active) $scope.togglePlay();
		else $scope.listenTo(side);
	}

	// la vidéo muette suit celle à l'écoute : vitesse retouchée pour un petit écart, recalée au-delà
	function keepInSync() {
		var lead = playerElement($scope.transport.active);
		var follow = playerElement($scope.transport.active === 'original' ? 'normalized' : 'original');
		if (!lead || !follow) return;
		if (lead.paused) {
			if (!follow.paused) follow.pause();
			return;
		}
		if (follow.paused && !follow.ended) follow.play();
		var drift = follow.currentTime - lead.currentTime;
		if (Math.abs(drift) > 0.5) {
			follow.currentTime = lead.currentTime;
			follow.playbackRate = 1;
		} else if (Math.abs(drift) > 0.04) {
			follow.playbackRate = drift > 0 ? 0.95 : 1.05;
		} else {
			follow.playbackRate = 1;
		}
	}

	// les deux au même instant, pour que basculer compare le même passage
	function seek(time) {
		eachPlayer(function(el) { el.currentTime = time; });
	}

	$scope.formatTime = function(seconds) {
		seconds = Math.floor(seconds || 0);
		var h = Math.floor(seconds / 3600), m = Math.floor(seconds / 60) % 60, s = seconds % 60;
		return (h ? h + ':' + (m < 10 ? '0' : '') : '') + m + ':' + (s < 10 ? '0' : '') + s;
	}

	function syncTransport() {
		var el = playerElement($scope.transport.active);
		if (!el) return;
		$scope.transport.playing = !el.paused;
		$scope.transport.time = el.currentTime;
		$scope.transport.duration = isFinite(el.duration) ? el.duration : 0;
		var slider = document.getElementById('player-seek');
		if (slider && document.activeElement !== slider) {
			slider.max = $scope.transport.duration;
			slider.value = $scope.transport.time;
		}
	}

	var playerModal = document.getElementById('player-modal');
	// retirer les lecteurs du dom arrête la lecture et le téléchargement de la vidéo
	playerModal.addEventListener('hidden.bs.modal', function() {
		cancelAnimationFrame(drawing);
		$scope.$apply(function() { $scope.player = null; });
	});
	playerModal.addEventListener('shown.bs.modal', function() {
		drawing = requestAnimationFrame(drawMeters);
	});
	// quelques fois par seconde seulement : un cycle angular par image retrierait toute la liste
	['play', 'pause', 'ended', 'timeupdate', 'seeked', 'loadedmetadata', 'durationchange'].forEach(function(type) {
		playerModal.addEventListener(type, function() { $scope.$applyAsync(syncTransport); }, true);
	});
	playerModal.addEventListener('input', function(event) {
		if (event.target.id === 'player-seek') seek(+event.target.value);
	});
	// le slider relâché rend les flèches à la navigation entre talks
	playerModal.addEventListener('change', function(event) {
		if (event.target.id === 'player-seek') event.target.blur();
	});

	// ----- Visualisation du son : crête et RMS des dernières secondes, en dBFS -----

	var audioContext = null;
	var drawing = null;
	var FLOOR_DB = -60;

	// une seule source par élément, le navigateur refuse la seconde : gardée sur l'élément
	function meterOf(side) {
		var el = playerElement(side);
		if (!el.meter) {
			audioContext = audioContext || new (window.AudioContext || window.webkitAudioContext)();
			var source = audioContext.createMediaElementSource(el);
			var analyser = audioContext.createAnalyser();
			analyser.fftSize = 2048;
			source.connect(analyser);
			var gain = audioContext.createGain();
			source.connect(gain);
			gain.connect(audioContext.destination);
			el.meter = {analyser: analyser, gain: gain, samples: new Float32Array(analyser.fftSize), history: []};
		}
		return el.meter;
	}

	function toDb(amplitude) {
		return amplitude > 0 ? Math.max(FLOOR_DB, 20 * Math.log10(amplitude)) : FLOOR_DB;
	}

	function drawMeters() {
		keepInSync();
		// l'horloge de celle à l'écoute pour les deux : leurs colonnes restent alignées
		var lead = playerElement($scope.transport.active);
		var now = lead ? lead.currentTime : 0;
		['original', 'normalized'].forEach(function(side) {
			var canvas = document.getElementById('meter-' + side);
			if (canvas) drawMeter(canvas, playerElement(side), now);
		});
		drawing = requestAnimationFrame(drawMeters);
	}

	// une colonne par tranche de temps de la vidéo, et non par image affichée : la cadence d'affichage
	// varie (120 ou 60 Hz, images sautées sous la charge), et le défilement accélérait puis ralentissait
	var COLUMN_SECONDS = 0.05;

	function drawMeter(canvas, el, now) {
		var ratio = window.devicePixelRatio || 1;
		var width = Math.round(canvas.clientWidth * ratio), height = Math.round(canvas.clientHeight * ratio);
		if (canvas.width !== width || canvas.height !== height) {
			canvas.width = width;
			canvas.height = height;
		}
		var meter = el && el.meter;
		var bar = 2 * ratio;

		if (meter && !el.paused) {
			// un saut dans la vidéo : l'historique ne correspond plus à ce qui précède
			if (meter.last === undefined || now < meter.last || now - meter.last > 1) {
				meter.history = [];
				meter.last = now;
				meter.acc = {peak: 0, sum: 0, count: 0};
			}
			// cumulé sur toutes les images de la tranche, pour ne pas manquer une crête entre deux colonnes
			meter.analyser.getFloatTimeDomainData(meter.samples);
			for (var i = 0; i < meter.samples.length; i++) {
				var v = Math.abs(meter.samples[i]);
				if (v > meter.acc.peak) meter.acc.peak = v;
				meter.acc.sum += v * v;
			}
			meter.acc.count += meter.samples.length;

			var columns = Math.floor((now - meter.last) / COLUMN_SECONDS);
			if (columns > 0) {
				var column = {peak: toDb(meter.acc.peak), rms: toDb(Math.sqrt(meter.acc.sum / meter.acc.count))};
				for (var c = 0; c < columns; c++) meter.history.push(column);
				meter.last += columns * COLUMN_SECONDS;
				meter.acc = {peak: 0, sum: 0, count: 0};
			}
			var keep = Math.floor(width / bar);
			if (meter.history.length > keep) meter.history.splice(0, meter.history.length - keep);
		}

		var ctx = canvas.getContext('2d');
		ctx.clearRect(0, 0, width, height);
		// hauteur depuis le centre, en dB : un son faible reste visible, ce que l'échelle linéaire écrasait
		var y = function(db) { return (db - FLOOR_DB) / -FLOOR_DB * height / 2; };
		var history = meter ? meter.history : [];
		var x0 = width - history.length * bar;
		for (var j = 0; j < history.length; j++) {
			var p = y(history[j].peak), r = y(history[j].rms);
			ctx.fillStyle = '#6ea8fe';
			ctx.fillRect(x0 + j * bar, height / 2 - p, bar - ratio / 2, 2 * p);
			ctx.fillStyle = '#0d6efd';
			ctx.fillRect(x0 + j * bar, height / 2 - r, bar - ratio / 2, 2 * r);
		}

		// repères : la crête visée par la normalisation, et le niveau visé
		ctx.font = (10 * ratio) + 'px sans-serif';
		[{db: -3, color: '#dc3545'}, {db: -23, color: '#ffc107'}].forEach(function(mark) {
			ctx.strokeStyle = ctx.fillStyle = mark.color;
			ctx.setLineDash([4 * ratio, 4 * ratio]);
			ctx.beginPath();
			[height / 2 - y(mark.db), height / 2 + y(mark.db)].forEach(function(at) {
				ctx.moveTo(0, at);
				ctx.lineTo(width, at);
			});
			ctx.stroke();
			// sous la ligne : au-dessus, celle de -3 dB sortirait du cadre
			ctx.fillText(mark.db + ' dB', 4 * ratio, height / 2 - y(mark.db) + 11 * ratio);
		});
		ctx.setLineDash([]);

		if (history.length) {
			var last = history[history.length - 1];
			ctx.fillStyle = '#f8f9fa';
			ctx.textAlign = 'right';
			ctx.fillText('crête ' + last.peak.toFixed(1) + ' · RMS ' + last.rms.toFixed(1) + ' dBFS', width - 4 * ratio, 12 * ratio);
			ctx.textAlign = 'left';
		}
	}

	$scope.videoUrl = function(video, original) {
		return '/video?dir=' + encodeURIComponent(video.dirName) + (original ? '&original=true' : '');
	}

	// Jackson sérialise un Path en URI file:///…, seul le nom du fichier intéresse
	$scope.fileName = function(path) {
		return path ? decodeURIComponent(path.split('/').pop()) : '';
	}

	// les noms de répertoires contiennent espaces, parenthèses et virgules : à encoder
	$scope.thumbUrl = function(video) {
		return '/thumb?dir=' + encodeURIComponent(video.dirName);
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
