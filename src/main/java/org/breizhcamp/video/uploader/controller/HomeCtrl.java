package org.breizhcamp.video.uploader.controller;

import org.breizhcamp.video.uploader.dto.YoutubeSession;
import org.breizhcamp.video.uploader.services.EventSrv;
import org.breizhcamp.video.uploader.services.FileSrv;
import org.breizhcamp.video.uploader.services.VideoSrv;
import org.breizhcamp.video.uploader.services.YoutubeSrv;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;

@Controller
public class HomeCtrl {
	private final EventSrv eventSrv;
	private final FileSrv fileSrv;
	private final VideoSrv videoSrv;
	private final YoutubeSrv youtubeSrv;
	private final YoutubeSession ytSession;

	public HomeCtrl(EventSrv eventSrv, FileSrv fileSrv, VideoSrv videoSrv, YoutubeSrv youtubeSrv, YoutubeSession ytSession) {
		this.eventSrv = eventSrv;
		this.fileSrv = fileSrv;
		this.videoSrv = videoSrv;
		this.youtubeSrv = youtubeSrv;
		this.ytSession = ytSession;
	}

	@GetMapping("/")
	public String home(Model model) throws IOException, GeneralSecurityException {
		Path videosDir = fileSrv.getRecordingDir();

		model.addAttribute("videosDir", videosDir);
		model.addAttribute("dirExists", Files.isDirectory(videosDir));

		boolean connected = youtubeSrv.isConnected();
		model.addAttribute("connected", connected);
		model.addAttribute("ytSession", ytSession);

		return "index";
	}

	@PostMapping("/createDir")
	public String createDir() throws IOException {
		fileSrv.createDirs();
		return "redirect:./";
	}

	@PostMapping("/generateSchedule")
	public String generateSchedule() throws IOException {
		videoSrv.generateUpdatedSchedule();
		return "redirect:/";
	}

	@PostMapping("/fixMissingIdsInSchedule")
	public String fixMissingIdsInSchedule() throws IOException {
		eventSrv.generateMissingIdsAndWrite();
		return "redirect:./";
	}
}
