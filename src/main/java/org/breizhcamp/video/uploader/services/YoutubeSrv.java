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
import org.breizhcamp.video.uploader.controller.YoutubeCtrl;
import org.breizhcamp.video.uploader.dto.Event;
import org.breizhcamp.video.uploader.dto.VideoInfo;
import org.breizhcamp.video.uploader.exception.UpdateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

import static org.breizhcamp.video.uploader.dto.VideoInfo.Status.*;

/**
 * Youtube access service
 */
@Service
public class YoutubeSrv {
	private static final Logger logger = LoggerFactory.getLogger(YoutubeSrv.class);

	@Autowired
	private VideoSrv videoSrv;

	@Autowired
	private EventSrv eventSrv;

	@Autowired
	private GoogleAuthorizationCodeFlow ytAuthFlow;

	@Autowired
	private HttpTransport httpTransport;

	@Autowired
	private JacksonFactory jacksonFactory;

	@Autowired
	private SimpMessagingTemplate template;

	private YouTube ytCache;

	private YtUploader uploader;

	@PostConstruct
	public void setUp() {
		uploader = new YtUploader();
		uploader.start();
	}

	@PreDestroy
	public void tearDown() {
		if (uploader != null) uploader.shutdown();
	}

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
	 * @param videoInfo Video to upload
	 */
	public void upload(VideoInfo videoInfo) throws UpdateException {
		uploader.uploadVideo(videoInfo);
	}

	/**
	 * @return waiting video to upload
	 */
	public List<VideoInfo> listWaiting() {
		return uploader.listWaiting();
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

	/** Youtube uploader thread */
	private class YtUploader extends Thread {
		/** List of video to upload */
		private BlockingDeque<VideoInfo> videoToUpload = new LinkedBlockingDeque<>();

		private boolean running = true;

		public YtUploader() {
			super("YtUploader");
		}

		public void uploadVideo(VideoInfo videoInfo) throws UpdateException {
			videoToUpload.addLast(videoInfo);
			videoInfo.setStatus(WAITING);
			updateVideo(videoInfo);
		}

		@Override
		public void run() {
			String lastUpload = null;
			int nbErrors = 0;

			try {
				while (running) {
					VideoInfo videoInfo = videoToUpload.take();
					logger.info("Uploading video: [{}]", videoInfo.getPath());

					try {
						lastUpload = videoInfo.getDirName();
						Event event = eventSrv.readAndGetById(videoInfo.getEventId());

						YouTube youtube = getYoutube();

						String speakers = event.getSpeakers();
						if (speakers.endsWith(", ")) speakers = speakers.substring(0, speakers.length() - 2);

						Video video = new Video();

						VideoStatus videoStatus = new VideoStatus();
						videoStatus.setPrivacyStatus("unlisted");
						video.setStatus(videoStatus);

						VideoSnippet snippet = new VideoSnippet();
						video.setSnippet(snippet);
						snippet.setTitle(makeTitle(event, speakers));
						//youtube doesn't support formatting, we keep the markdown as it readable as is
						snippet.setDescription(event.getDescription().replace('<', '〈').replace('>', '〉'));

						FileContent videoContent = new FileContent("video/*", videoInfo.getPath().toFile());

						YouTube.Videos.Insert insert = youtube.videos().insert("snippet,status", video, videoContent);

						MediaHttpUploader uploader = insert.getMediaHttpUploader();
						uploader.setChunkSize(1024 * 1024 * 50); //10MB in order to have progress info often :p

						uploader.setProgressListener(httpUploader -> {
							try {
								switch (httpUploader.getUploadState()) {
									case NOT_STARTED:
										logger.info("[{}] Not started", videoInfo.getEventId());
										break;
									case INITIATION_STARTED:
										logger.info("[{}] Init started", videoInfo.getEventId());
										videoInfo.setStatus(INITIALIZING);
										updateVideo(videoInfo);

										break;
									case INITIATION_COMPLETE:
										logger.info("[{}] Init complete", videoInfo.getEventId());
										videoInfo.setStatus(IN_PROGRESS);
										videoInfo.setProgression(BigDecimal.ZERO);
										updateVideo(videoInfo);

										break;
									case MEDIA_IN_PROGRESS:
										double progress = httpUploader.getProgress();
										logger.info("[{}] Upload in progress: [{}]", videoInfo.getEventId(), progress);

										BigDecimal percent = new BigDecimal(progress * 100, new MathContext(3));

										videoInfo.setStatus(IN_PROGRESS);
										videoInfo.setProgression(percent);
										updateVideo(videoInfo);

										break;
									case MEDIA_COMPLETE:
										logger.info("[{}] Upload video file complete", videoInfo.getEventId());
										break;
								}
							} catch (UpdateException e) {
								//not a critical exception, let the upload continue
								logger.warn("Cannot send or write update for video [{}]", videoInfo.getDirName(), e);
							}
						});

						//this call is blocking until video is completely uploaded
						Video insertedVideo = insert.execute();
						videoInfo.setYoutubeId(insertedVideo.getId());
						videoInfo.setProgression(null);

						//upload thumbnail if available
						if (videoInfo.getThumbnail() != null) {
							videoInfo.setStatus(THUMBNAIL);
							updateVideo(videoInfo);

							uploadThumbnail(videoInfo);
						}

						insertInPlaylist(videoInfo);
						videoInfo.setStatus(DONE);
						updateVideo(videoInfo);

						logger.info("[{}] Video uploaded, end of process", videoInfo.getEventId());
						nbErrors = 0;

					} catch (UpdateException | GeneralSecurityException | IOException e) {
						logger.error("Error when uploading [{}]", lastUpload, e);
						videoInfo.setStatus(FAILED);
						try {
							updateVideo(videoInfo);
						} catch (UpdateException ex) {
							logger.error("Unable to update metadata", ex);
						}
						nbErrors++;

						if (nbErrors > 5) {
							throw new RuntimeException("At least 5 videos failed to upload, stopping thread");
						}
					}
				}

			} catch (InterruptedException e) {
				running = false;
			}
		}

		private void insertInPlaylist(VideoInfo videoInfo) throws GeneralSecurityException, IOException {
			String playlistId = videoInfo.getPlaylistId();
			if (playlistId == null) return;
			logger.info("[{}] Setting video in playlist [{}]", videoInfo.getEventId(), playlistId);

			PlaylistItem item = new PlaylistItem();
			PlaylistItemSnippet snippet = new PlaylistItemSnippet();
			item.setSnippet(snippet);
			snippet.setPlaylistId(playlistId);

			ResourceId resourceId = new ResourceId();
			resourceId.setVideoId(videoInfo.getYoutubeId());
			resourceId.setKind("youtube#video");

			snippet.setResourceId(resourceId);

			getYoutube().playlistItems().insert("snippet,status", item).execute();
			logger.info("[{}] Video set in playlist", videoInfo.getEventId());
		}

		private void uploadThumbnail(VideoInfo videoInfo) throws GeneralSecurityException, IOException {
			logger.info("[{}] Uploading and defining thumbnail [{}]", videoInfo.getEventId(), videoInfo.getThumbnail());
			FileContent thumb = new FileContent("image/png", videoInfo.getThumbnail().toFile());
			getYoutube().thumbnails().set(videoInfo.getYoutubeId(), thumb).execute();
			logger.info("[{}] Thumbnail set", videoInfo.getEventId());
		}

		void shutdown() {
			running = false;
		}

		List<VideoInfo> listWaiting() {
			return new ArrayList<>(videoToUpload);
		}

		private void updateVideo(VideoInfo video) throws UpdateException {
			try {
				template.convertAndSend(YoutubeCtrl.VIDEOS_TOPIC, video);
				videoSrv.updateVideo(video);
			} catch (MessagingException | IOException e) {
				throw new UpdateException(e);
			}
		}
	}

	/**
	 * Make a title compatible with Youtube : 100 chars with no < or >.
	 * https://developers.google.com/youtube/v3/docs/videos#snippet.title
	 * @param event Event detail
	 * @param speakers Speakers' name
	 * @return Compatible twitter video title
	 */
	private String makeTitle(Event event, String speakers) {
		String name = event.getName();

		if (name.length() + speakers.length() + 3 > 100) {
			name = name.substring(0, 100-speakers.length()-4) + "…";
		}

		name = name.replace('<', '〈').replace('>', '〉');

		return name + " (" + speakers + ")";
	}
}
