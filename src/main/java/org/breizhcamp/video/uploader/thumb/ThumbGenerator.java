package org.breizhcamp.video.uploader.thumb;

import org.apache.commons.io.IOUtils;
import org.breizhcamp.video.uploader.dto.Event;
import org.breizhcamp.video.uploader.services.EventSrv;
import org.breizhcamp.video.uploader.services.FileSrv;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

@SpringBootApplication
public class ThumbGenerator {

	public static void main(String[] args) throws Exception {
		ConfigurableApplicationContext ctx = new SpringApplicationBuilder(ThumbGenerator.class)
				.web(false)
				.run(args);

		EventSrv eventSrv = ctx.getBean(EventSrv.class);
		FileSrv fileSrv = ctx.getBean(FileSrv.class);
		List<Event> events = eventSrv.list();

		String svgThumb = IOUtils.toString(ThumbGenerator.class.getResourceAsStream("/thumb.svg"), UTF_8);
		String svgIntroVideo = IOUtils.toString(ThumbGenerator.class.getResourceAsStream("/intro.svg"), UTF_8);

		for (Event event : events) {
			if (event.getVenue().equals("Amphi C") || event.getVenue().equals("Amphi D")) {
				String speakers = event.getSpeakers().replaceAll("[\\\\/:*?\"<>|]", "-");
				if (speakers.endsWith(", ")) speakers = speakers.substring(0, speakers.length() - 2);

				Path destDir = fileSrv.getVideosDir().resolve(fileSrv.buildDirName(event));
				makeThumb(svgThumb, event.getName(), speakers, destDir, "thumb.png");
				makeThumb(svgIntroVideo, event.getName(), speakers, destDir, "intro.png");
			}
		}
	}

	private static void makeThumb(String svg, String title, String speakers, Path destDir, String targetName) throws IOException, InterruptedException {
		Path replaced = Files.createTempFile("thumb_", ".svg");
		String content = svg.replace("TitreTalk", title);
		content = content.replace("SpeakersTalk", speakers);

		Files.write(replaced, content.getBytes(UTF_8));

		String[] cmd = {"/usr/bin/inkscape", "--export-png=" + destDir.toString() + "/" + targetName, replaced.toAbsolutePath().toString()};

		System.out.println(Arrays.toString(cmd));
		Process p = Runtime.getRuntime().exec(cmd);
		int exit = p.waitFor();
		System.out.println("exit: " + exit);

		Files.delete(replaced);
	}


	@Bean
	public EventSrv getEventSrv() {
		return new EventSrv();
	}

	@Bean
	public FileSrv getFileSrv() {
		return new FileSrv();
	}

}
