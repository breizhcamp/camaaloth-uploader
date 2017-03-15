package org.breizhcamp.video.uploader.controller;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.services.youtube.model.Channel;
import com.google.api.services.youtube.model.Playlist;
import org.breizhcamp.video.uploader.dto.Event;
import org.breizhcamp.video.uploader.dto.UploadProgress;
import org.breizhcamp.video.uploader.dto.YoutubeSession;
import org.breizhcamp.video.uploader.services.EventSrv;
import org.breizhcamp.video.uploader.services.FileSrv;
import org.breizhcamp.video.uploader.services.VideoSrv;
import org.breizhcamp.video.uploader.services.YoutubeSrv;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

/**
 * Controller to handle Google authentication
 */
@Controller @RequestMapping("/yt")
public class YoutubeCtrl {

	@Autowired
	private YoutubeSrv youtubeSrv;

	@Autowired
	private FileSrv fileSrv;

	@Autowired
	private EventSrv eventSrv;

	@Autowired
	private VideoSrv videoSrv;

	@Autowired
	private YoutubeSession ytSession;

	@Autowired
	private GoogleAuthorizationCodeFlow ytAuthFlow;

	@Autowired
	private SimpMessagingTemplate template;

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

	@PostMapping("/upload")
	public String uploadVideo(@RequestParam String path) throws IOException {
		String id = fileSrv.getIdFromPath(path);
		if (id != null) {
			Event event = eventSrv.getFromId(id);
			youtubeSrv.upload(event, videoSrv.readDir(fileSrv.getVideosDir().resolve(path)));
		}

		return "redirect:/";
	}

	@GetMapping("/test")
	@ResponseBody
	public String test() {
		template.convertAndSend("/upload", UploadProgress.builder().eventId("928").percent(254).build());
		return "sent";
	}
}
