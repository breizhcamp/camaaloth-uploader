package org.breizhcamp.video.uploader.dto;

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
public class VideoInfo {
	public enum Status { NOT_STARTED, IN_PROGRESS, DONE }


	private Path path;
	private Path thumbnail;
	private String eventId;
	private Status status;
	private String youtubeId;
	private BigDecimal progression;

	/**
	 * @return The name of the directory the videos is
	 */
	public String getDirName() {
		if (path == null) return "";
		return path.getParent().getFileName().toString();
	}
}
