package org.breizhcamp.video.uploader.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.nio.file.Path;

/**
 * VideoInfo file location and status
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VideoInfo {
	public enum Status {
		NOT_STARTED,
		/** In upload queue */ WAITING,
		/** Initializing upload */ INITIALIZING,
		/** Upload in progress, progression should be populated */ IN_PROGRESS,
		/** Setting thumbnail in progress */ THUMBNAIL,
		/** Upload done, youtubeId should be set */ DONE }


	private Path path;
	private Path thumbnail;
	private String eventId;
	private Status status;
	private String youtubeId;
	private BigDecimal progression;

	/** Id of the playlist the video is inserted into after the upload */
	private String playlistId;

	/**
	 * @return The name of the directory the videos is
	 */
	public String getDirName() {
		if (path == null) return "";
		return path.getParent().getFileName().toString();
	}
}
