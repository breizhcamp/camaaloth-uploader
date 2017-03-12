package org.breizhcamp.video.uploader.services;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Channel;
import com.google.api.services.youtube.model.ChannelListResponse;
import com.google.api.services.youtube.model.Playlist;
import com.google.api.services.youtube.model.PlaylistListResponse;
import org.breizhcamp.video.uploader.config.YoutubeConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

/**
 * Youtube access service
 */
@Service
public class YoutubeSrv {

	@Autowired
	private GoogleAuthorizationCodeFlow ytAuthFlow;

	@Autowired
	private HttpTransport httpTransport;

	@Autowired
	private JacksonFactory jacksonFactory;

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
		if (credential.getExpiresInSeconds() == null || credential.getExpiresInSeconds() < 10) {
			return null;
		}
		return credential;
	}

}
