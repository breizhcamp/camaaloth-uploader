package org.breizhcamp.video.uploader.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Object send in websocket to notify the progress of the upload
 */
@Data @Builder
@AllArgsConstructor @NoArgsConstructor
public class UploadProgress {

	private String eventId;

	/** Percent * 10 */
	private Integer percent;

}
