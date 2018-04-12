package org.breizhcamp.video.uploader.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/**
 * JSON file stored aside of the video file to keep record of the current status
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VideoMetadata {

	private VideoInfo.Status status;
	private BigDecimal progression;
	private String youtubeId;

	public VideoMetadata() {
	}

	public VideoMetadata(VideoInfo.Status status, BigDecimal progression, String youtubeId) {
		this.status = status;
		this.progression = progression;
		this.youtubeId = youtubeId;
	}

	public VideoInfo.Status getStatus() {
		return status;
	}

	public void setStatus(VideoInfo.Status status) {
		this.status = status;
	}

	public BigDecimal getProgression() {
		return progression;
	}

	public void setProgression(BigDecimal progression) {
		this.progression = progression;
	}

	public String getYoutubeId() {
		return youtubeId;
	}

	public void setYoutubeId(String youtubeId) {
		this.youtubeId = youtubeId;
	}
}
