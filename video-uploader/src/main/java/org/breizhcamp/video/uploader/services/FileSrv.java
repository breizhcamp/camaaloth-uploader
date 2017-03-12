package org.breizhcamp.video.uploader.services;

import org.breizhcamp.video.uploader.dto.Event;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Handle directory and files operations
 */
@Service
public class FileSrv {

	@Value("${videos.dir:./videos}")
	private Path videosDir;

	@Autowired
	private EventSrv eventSrv;

	private DateTimeFormatter dayFormat = DateTimeFormatter.ofPattern("dd");
	private DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("HH-mm");

	public Path getVideosDir() {
		return videosDir.toAbsolutePath();
	}

	/**
	 * Create one directory for each event in videosDir.
	 */
	public void createDirs() throws IOException {
		Files.createDirectories(videosDir);
		List<Event> events = eventSrv.list();

		for (Event event : events) {
			Path dir = videosDir.resolve(buildDirName(event));
			if (!Files.exists(dir)) {
				Files.createDirectory(dir);
			}
		}
	}


	/**
	 * @return the directory name for a specific event
	 */
	private String buildDirName(Event event) {
		return dayFormat.format(event.getEventStart()) + "." + event.getVenue() + "." + timeFormat.format(event.getEventStart())
				+ " - " + event.getName() + " - " + event.getId();
	}
}
