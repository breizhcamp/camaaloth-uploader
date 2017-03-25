package org.breizhcamp.video.uploader.services;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.*;
import org.breizhcamp.video.uploader.config.YoutubeConfig;
import org.breizhcamp.video.uploader.controller.HomeCtrl;
import org.breizhcamp.video.uploader.dto.Event;
import org.breizhcamp.video.uploader.dto.VideoInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.security.GeneralSecurityException;
import java.util.List;

/**
 * Youtube access service
 */
@Service
public class YoutubeSrv {
	private static final Logger logger = LoggerFactory.getLogger(YoutubeSrv.class);

	@Autowired
	private VideoSrv videoSrv;

	@Autowired
	private GoogleAuthorizationCodeFlow ytAuthFlow;

	@Autowired
	private HttpTransport httpTransport;

	@Autowired
	private JacksonFactory jacksonFactory;

	@Autowired
	private SimpMessagingTemplate template;

	private YouTube ytCache;


	/**
	 * Check if user is connected and has valid credentials
	 * @return True if connected, false otherwise
	 */
	public boolean isConnected() throws IOException {
		return getCurrentCred() != null;
	}

	/**
	 * Save oauth token for reuse
	 * @param token Token to save
	 */
	public void saveToken(GoogleTokenResponse token) throws IOException {
		ytAuthFlow.createAndStoreCredential(token, YoutubeConfig.YT_USER_ID);
		ytCache = null;
	}

	/**
	 * Retrieve all channels user can handle
	 * @return Channels list
	 */
	public List<Channel> getChannels() throws GeneralSecurityException, IOException {
		ChannelListResponse response = getYoutube().channels().list("id,snippet").setMine(true).execute();
		return response.getItems();
	}

	/**
	 * Retrieve all playlists for a channel id
	 * @param channelId Id of the channel whose playlists we want, must be accessible by the user
	 * @return List of playlist
	 */
	public List<Playlist> getPlaylists(String channelId) throws GeneralSecurityException, IOException {
		PlaylistListResponse response = getYoutube()
				.playlists().list("id,snippet").setChannelId(channelId).setMaxResults(50L)
				.execute();
		return response.getItems();
	}

	/**
	 * Upload a video
	 * @param event Event describing the video
	 * @param videoInfo File path of the video to upload
	 */
	public void upload(Event event, VideoInfo videoInfo) throws IOException, GeneralSecurityException {
		//TODO handle async / waiting list / threading
		logger.info("Uploading video: [{}]", videoInfo.getPath());

		YouTube youtube = getYoutube();

		String speakers = event.getSpeakers();
		if (speakers.endsWith(", ")) speakers = speakers.substring(0, speakers.length() - 2);

		Video video = new Video();

		VideoStatus videoStatus = new VideoStatus();
		videoStatus.setPrivacyStatus("unlisted");
		video.setStatus(videoStatus);

		VideoSnippet snippet = new VideoSnippet();
		video.setSnippet(snippet);
		snippet.setTitle(event.getName());
		snippet.setDescription("par " + speakers + "\n\n" + event.getDescription()); //TODO check markdown formatting

		FileContent videoContent = new FileContent("video/*", videoInfo.getPath().toFile());

		YouTube.Videos.Insert insert = youtube.videos().insert("snippet,status", video, videoContent);

		MediaHttpUploader uploader = insert.getMediaHttpUploader();

		//TODO send every state to gui
		uploader.setProgressListener(httpUploader -> {
			switch (httpUploader.getUploadState()) {
				case NOT_STARTED:
					logger.info("Not started");
					break;
				case INITIATION_STARTED:
					logger.info("Init started");
					break;
				case INITIATION_COMPLETE:
					logger.info("Init complete");
					break;
				case MEDIA_IN_PROGRESS:
					double progress = httpUploader.getProgress();
					logger.info("Upload in progress: " + progress);

					BigDecimal percent = new BigDecimal(progress * 100, new MathContext(2));

					videoInfo.setStatus(VideoInfo.Status.IN_PROGRESS);
					videoInfo.setProgression(percent);

					template.convertAndSend(HomeCtrl.VIDEOS_TOPIC, videoInfo);
					videoSrv.updateVideo(videoInfo);
					break;
				case MEDIA_COMPLETE:
					logger.info("Upload complete");
					break;
			}
		});

		Video insertedVideo = insert.execute();
		videoSrv.setVideoDone(videoInfo.getPath().getParent(), insertedVideo.getId());
	}


	private YouTube getYoutube() throws GeneralSecurityException, IOException {
		Credential credential = getCurrentCred();
		if (credential == null) {
			ytCache = null;
			throw new IllegalStateException("Not connected");
		}

		if (ytCache != null) {
			return ytCache;
		}

		ytCache = new YouTube.Builder(httpTransport, jacksonFactory, credential).setApplicationName("yt-uploader/1.0").build();
		return ytCache;
	}

	/**
	 * Retrieve current and valid credential
	 * @return Valid credential or null
	 */
	private Credential getCurrentCred() throws IOException {
		Credential credential = ytAuthFlow.loadCredential("user");
		if (credential == null || credential.getExpiresInSeconds() == null || credential.getExpiresInSeconds() < 10) {
			return null;
		}
		return credential;
	}

}
