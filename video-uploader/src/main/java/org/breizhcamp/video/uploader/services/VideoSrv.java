package org.breizhcamp.video.uploader.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.breizhcamp.video.uploader.dto.VideoInfo;
import org.breizhcamp.video.uploader.dto.VideoMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.breizhcamp.video.uploader.dto.VideoInfo.Status.NOT_STARTED;

@Service
public class VideoSrv {

	@Autowired
	private FileSrv fileSrv;

	@Autowired
	private ObjectMapper objectMapper;

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
			Path videoFile = getFirstFileFromExt(dir, "mp4", "mkv");
			if (videoFile == null) return null;

			Path thumbnail = getFirstFileFromExt(dir, "png");

			VideoInfo videoInfo = VideoInfo.builder()
					.path(videoFile)
					.status(NOT_STARTED)
					.thumbnail(thumbnail)
					.eventId(fileSrv.getIdFromPath(dir.getFileName().toString()))
					.build();

			Path statusFile = dir.resolve("metadata.json");
			if (Files.exists(statusFile)) {
				VideoMetadata metadata = objectMapper.readValue(statusFile.toFile(), VideoMetadata.class);
				videoInfo.setStatus(metadata.getStatus());
				videoInfo.setProgression(metadata.getProgression());
				videoInfo.setYoutubeId(metadata.getYoutubeId());
			}

			return videoInfo;

		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Update video status in metadata
	 * @param video Video to update
	 */
	public void updateVideo(VideoInfo video) throws IOException {
		VideoMetadata metadata = VideoMetadata.builder()
				.status(video.getStatus())
				.progression(video.getProgression())
				.youtubeId(video.getYoutubeId())
				.build();

		Path statusFile = video.getPath().getParent().resolve("metadata.json");
		objectMapper.writeValue(statusFile.toFile(), metadata);
	}

	/**
	 * List a directory to retrieve the first file with the specified extension
	 * @param dir Directory to read
	 * @param ext Extension to find
	 * @return First file found or null if any file with specified extension exists within the directory
	 */
	private Path getFirstFileFromExt(Path dir, String... ext) {
		List<String> suffixes = Arrays.stream(ext).map(e -> "." + e).collect(toList());

		try (Stream<Path> list = Files.list(dir)) {
			return list
					.filter(f -> suffixes.stream().anyMatch((suffix) -> f.toString().toLowerCase().endsWith(suffix)))
					.findFirst()
					.orElse(null);

		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
