package org.breizhcamp.video.uploader.controller;

import org.breizhcamp.video.uploader.dto.YoutubeSession;
import org.breizhcamp.video.uploader.services.FileSrv;
import org.breizhcamp.video.uploader.services.VideoSrv;
import org.breizhcamp.video.uploader.services.YoutubeSrv;
import org.springframework.beans.factory.annotation.Autowired;
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
	@Autowired
	private FileSrv fileSrv;

	@Autowired
	private VideoSrv videoSrv;

	@Autowired
	private YoutubeSrv youtubeSrv;

	@Autowired
	private YoutubeSession ytSession;

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
}
