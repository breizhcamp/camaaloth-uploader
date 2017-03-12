package org.breizhcamp.video.uploader.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.nio.file.Path;

/**
 * Video file location and status
 */
@Data @Builder
public class Video {
	public enum Status { NOT_STARTED, IN_PROGRESS, DONE }


	private Path path;
	private Status status;
	private String youtubeId;
	private BigDecimal progression;


	public String getEventId() {
		String dirName = path.getParent().toString();
		int dash = dirName.lastIndexOf('-');
		if (dash < 0) {
			return null;
		}

		return dirName.substring(dash + 1);
	}

}
