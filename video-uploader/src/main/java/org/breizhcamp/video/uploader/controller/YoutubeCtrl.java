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
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

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

	//TODO handle upload in websocket
	@PostMapping("/upload")
	public String uploadVideo(@RequestParam String path) throws IOException, GeneralSecurityException, UpdateException {
		String id = fileSrv.getIdFromPath(path);
		if (id != null) {
			VideoInfo videoInfo = videoSrv.readDir(fileSrv.getVideosDir().resolve(path));
			videoInfo.setPlaylistId(ytSession.getCurPlaylist().getId());
			youtubeSrv.upload(videoInfo);
		}

		return "redirect:/";
	}

}
