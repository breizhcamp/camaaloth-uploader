package org.breizhcamp.video.uploader.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.nio.file.Path;

/**
 * VideoInfo file location and status
 */
@Data @Builder
public class VideoInfo {
	public enum Status { NOT_STARTED, IN_PROGRESS, DONE }


	private Path path;
	private Path thumbnail;
	private String eventId;
	private Status status;
	private String youtubeId;
	private BigDecimal progression;

}
