package org.breizhcamp.video.uploader.controller;

import org.breizhcamp.video.uploader.dto.VideoInfo;
import org.breizhcamp.video.uploader.dto.YoutubeSession;
import org.breizhcamp.video.uploader.services.FileSrv;
import org.breizhcamp.video.uploader.services.VideoSrv;
import org.breizhcamp.video.uploader.services.YoutubeSrv;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
public class HomeCtrl {
	public static final String VIDEOS_TOPIC = "/videos";

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
		Path videosDir = fileSrv.getVideosDir();

		model.addAttribute("videosDir", videosDir);
		model.addAttribute("dirExists", Files.isDirectory(videosDir));

		boolean connected = youtubeSrv.isConnected();
		model.addAttribute("connected", connected);
		model.addAttribute("ytSession", ytSession);

		return "index";
	}

	@SubscribeMapping(VIDEOS_TOPIC)
	public Collection<VideoInfo> subscribe() throws IOException {
		Map<String, VideoInfo> videoById = videoSrv.list().stream().collect(Collectors.toMap(VideoInfo::getEventId, Function.identity()));

		youtubeSrv.listWaiting().stream()
			.map(VideoInfo::getEventId)
			.forEach(id -> videoById.get(id).setStatus(VideoInfo.Status.WAITING));

		return videoById.values();
	}

	@PostMapping("/createDir")
	public String createDir() throws IOException {
		fileSrv.createDirs();
		return "redirect:./";
	}

}
