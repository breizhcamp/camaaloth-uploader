package org.breizhcamp.video.uploader.controller;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.services.youtube.model.Channel;
import com.google.api.services.youtube.model.Playlist;
import org.breizhcamp.video.uploader.dto.VideoInfo;
import org.breizhcamp.video.uploader.dto.YoutubeSession;
import org.breizhcamp.video.uploader.exception.UpdateException;
import org.breizhcamp.video.uploader.services.FileSrv;
import org.breizhcamp.video.uploader.services.VideoSrv;
import org.breizhcamp.video.uploader.services.YoutubeSrv;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

/**
 * Controller to handle Google authentication
 */
@Controller @RequestMapping("/yt")
public class YoutubeCtrl {
	public static final String VIDEOS_TOPIC = "/videos";

	@Autowired
	private YoutubeSrv youtubeSrv;

	@Autowired
	private FileSrv fileSrv;

	@Autowired
	private VideoSrv videoSrv;

	@Autowired
	private YoutubeSession ytSession;

	@Autowired
	private GoogleAuthorizationCodeFlow ytAuthFlow;

	private String redirectUrl;

	@GetMapping("/auth")
	public String auth(@RequestParam String baseUrl) throws IOException {
		redirectUrl = baseUrl + "yt/return";
		return "redirect:" + ytAuthFlow.newAuthorizationUrl().setRedirectUri(redirectUrl).setAccessType("offline").build();
	}

	@GetMapping("/return")
	public String returnAuth(@RequestParam String code) throws IOException, GeneralSecurityException {
		GoogleTokenResponse token = ytAuthFlow.newTokenRequest(code).setRedirectUri(redirectUrl).execute();
		youtubeSrv.saveToken(token);
		reloadYtSession();

		return "redirect:/";
	}

	@GetMapping("/reload")
	public String reloadYtSession() throws GeneralSecurityException, IOException {
		List<Channel> channels = youtubeSrv.getChannels();
		ytSession.setChannels(channels);

		if (channels != null && channels.size() == 1) {
			ytSession.setCurChan(ytSession.getChannels().get(0));
			ytSession.setPlaylists(youtubeSrv.getPlaylists(ytSession.getCurChan().getId()));
		}

		return "redirect:/";
	}

	@PostMapping("/curPlaylist")
	public String changeCurPlaylist(@RequestParam String playlist) {
		if ("none".equals(playlist)) {
			ytSession.setCurPlaylist(null);

		} else if (ytSession.getPlaylists() != null) {
			for (Playlist p : ytSession.getPlaylists()) {
				if (p.getId().equals(playlist)) {
					ytSession.setCurPlaylist(p);
					break;
				}
			}
		}

		return "redirect:/";
	}

	@PostMapping("uploadAll")
	public String uploadAll() throws IOException, UpdateException {
		for (VideoInfo video : videoSrv.list()) {
			if (video.getStatus() == VideoInfo.Status.NOT_STARTED) {
				video.setPlaylistId(ytSession.getCurPlaylist().getId());
				youtubeSrv.upload(video);
			}
		}

		return "redirect:/";
	}

	@SubscribeMapping(VIDEOS_TOPIC)
	public Collection<VideoInfo> subscribe() throws IOException {
		Map<String, VideoInfo> videoById = videoSrv.list().stream().collect(Collectors.toMap(VideoInfo::getEventId, Function.identity()));

		youtubeSrv.listWaiting().stream()
				.map(VideoInfo::getEventId)
				.forEach(id -> videoById.get(id).setStatus(VideoInfo.Status.WAITING));

		return videoById.values().stream()
				.sorted(comparing(VideoInfo::getDirName))
				.collect(toList());
	}

	@MessageMapping(VIDEOS_TOPIC + "/upload")
	public void upload(@Payload String path) throws UpdateException {
		String id = fileSrv.getIdFromPath(path);
		if (id != null) {
			VideoInfo videoInfo = videoSrv.readDir(fileSrv.getRecordingDir().resolve(path));
			if (ytSession.getCurPlaylist() != null) {
				videoInfo.setPlaylistId(ytSession.getCurPlaylist().getId());
			}
			youtubeSrv.upload(videoInfo);
		}
	}
}
