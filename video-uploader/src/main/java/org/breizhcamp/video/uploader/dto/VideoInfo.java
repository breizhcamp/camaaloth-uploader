package org.breizhcamp.video.uploader.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.nio.file.Path;

/**
 * VideoInfo file location and status
 */
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

	public VideoInfo() {
	}

	public VideoInfo(Path path, Path thumbnail, String eventId, Status status, String youtubeId, BigDecimal progression, String playlistId) {
		this.path = path;
		this.thumbnail = thumbnail;
		this.eventId = eventId;
		this.status = status;
		this.youtubeId = youtubeId;
		this.progression = progression;
		this.playlistId = playlistId;
	}

	/**
	 * @return The name of the directory the videos is
	 */
	public String getDirName() {
		if (path == null) return "";
		return path.getParent().getFileName().toString();
	}

	public Path getPath() {
		return path;
	}

	public void setPath(Path path) {
		this.path = path;
	}

	public Path getThumbnail() {
		return thumbnail;
	}

	public void setThumbnail(Path thumbnail) {
		this.thumbnail = thumbnail;
	}

	public String getEventId() {
		return eventId;
	}

	public void setEventId(String eventId) {
		this.eventId = eventId;
	}

	public Status getStatus() {
		return status;
	}

	public void setStatus(Status status) {
		this.status = status;
	}

	public String getYoutubeId() {
		return youtubeId;
	}

	public void setYoutubeId(String youtubeId) {
		this.youtubeId = youtubeId;
	}

	public BigDecimal getProgression() {
		return progression;
	}

	public void setProgression(BigDecimal progression) {
		this.progression = progression;
	}

	public String getPlaylistId() {
		return playlistId;
	}

	public void setPlaylistId(String playlistId) {
		this.playlistId = playlistId;
	}
}
