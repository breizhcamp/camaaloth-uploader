package org.breizhcamp.video.uploader.services;

import org.breizhcamp.video.uploader.dto.Video;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

@Service
public class VideoSrv {

	@Autowired
	private FileSrv fileSrv;

	/**
	 * List all videos found in video directory
	 * @return Video found and status
	 */
	public List<Video> list() throws IOException {
		Path dir = fileSrv.getVideosDir();

		try (Stream<Path> list = Files.list(dir)) {
			return list.filter(Files::isDirectory)
					.map(this::readDir)
					.filter(Objects::nonNull)
					.collect(toList());
		}
	}


	/**
	 * Read a directory and create the associate video object
	 * @param dir Directory to read
	 * @return Video object filled or null if directory empty
	 */
	private Video readDir(Path dir) {
		//retrieving first video file
		Path videoFile;
		try (Stream<Path> list = Files.list(dir)) {
			Optional<Path> video = list.filter(f -> f.toString().toLowerCase().endsWith(".mp4"))
					.findFirst();

			if (video.isPresent()) {
				videoFile = video.get();
			} else {
				return null;
			}


			Video.Status status = Video.Status.NOT_STARTED;
			BigDecimal progress = null;
			Path statusFile = dir.resolve("status.txt");
			if (Files.exists(statusFile)) {
				List<String> lines = Files.readAllLines(statusFile);
				if (!lines.isEmpty()) {
					String line = lines.get(0).trim();
					if (line.equals("done")) {
						status = Video.Status.DONE;
					} else {
						status = Video.Status.IN_PROGRESS;
						progress = new BigDecimal(line);
					}
				}
			}

			return Video.builder()
					.path(videoFile)
					.status(status)
					.progression(progress)
					.build();

		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
