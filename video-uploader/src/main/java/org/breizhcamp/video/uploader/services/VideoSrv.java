package org.breizhcamp.video.uploader.services;

import org.breizhcamp.video.uploader.dto.VideoInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

@Service
public class VideoSrv {

	@Autowired
	private FileSrv fileSrv;

	/**
	 * List all videos found in video directory
	 * @return VideoInfo found and status
	 */
	public List<VideoInfo> list() throws IOException {
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
	 * @return VideoInfo object filled or null if directory does not contains video file
	 */
	public VideoInfo readDir(Path dir) {
		//retrieving first video file
		try {
			Path videoFile = getFirstFileFromExt(dir, "mp4");
			if (videoFile == null) return null;

			Path thumbnail = getFirstFileFromExt(dir, "png");

			VideoInfo.Status status = VideoInfo.Status.NOT_STARTED;
			BigDecimal progress = null;
			Path statusFile = dir.resolve("status.txt");
			if (Files.exists(statusFile)) {
				List<String> lines = Files.readAllLines(statusFile);
				if (!lines.isEmpty()) {
					String line = lines.get(0).trim();
					if (line.equals("done")) {
						status = VideoInfo.Status.DONE;
					} else {
						status = VideoInfo.Status.IN_PROGRESS;
						progress = new BigDecimal(line);
					}
				}
			}

			return VideoInfo.builder()
					.path(videoFile)
					.thumbnail(thumbnail)
					.eventId(fileSrv.getIdFromPath(dir.getFileName().toString()))
					.status(status)
					.progression(progress)
					.build();

		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Define video state to "in progress" with its upload percent
	 * @param videoDir Directory containing the video
	 * @param percent Upload percentage
	 */
	public void setVideoProgress(Path videoDir, BigDecimal percent) {
		//TODO
	}

	/**
	 * Define video state to done with its id
	 * @param videoDir Directory containing the video
	 * @param videoId Id of the uploaded video
	 */
	public void setVideoDone(Path videoDir, String videoId) {
		//TODO
	}

	/**
	 * List a directory to retrieve the first file with the specified extension
	 * @param dir Directory to read
	 * @param ext Extension to find
	 * @return First file found or null if any file with specified extension exists within the directory
	 */
	private Path getFirstFileFromExt(Path dir, String ext) {
		String suffix = "." + ext;

		try (Stream<Path> list = Files.list(dir)) {
			return list.filter(f -> f.toString().toLowerCase().endsWith(suffix)).findFirst().orElse(null);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
