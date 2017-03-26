package org.breizhcamp.video.uploader.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * JSON file stored aside of the video file to keep record of the current status
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VideoMetadata {

	private VideoInfo.Status status;
	private BigDecimal progression;
	private String youtubeId;

}
