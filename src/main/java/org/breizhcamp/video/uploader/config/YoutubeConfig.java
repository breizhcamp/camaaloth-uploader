package org.breizhcamp.video.uploader.config;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import org.breizhcamp.video.uploader.dto.YoutubeSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.Arrays;

import static com.google.api.services.youtube.YouTubeScopes.YOUTUBE;
import static com.google.api.services.youtube.YouTubeScopes.YOUTUBE_UPLOAD;

/**
 * Configuration file for Youtube access
 */
@Configuration
public class YoutubeConfig {

	/** Id of the user for storing credential */
	public static final String YT_USER_ID = "user";

	@Value("${videos.dir:./videos}/.datastore")
	private File dataStoreDir;

	@Bean
	public JacksonFactory jacksonFactory() {
		return JacksonFactory.getDefaultInstance();
	}

	@Bean
	public HttpTransport httpTransport() throws GeneralSecurityException, IOException {
		return GoogleNetHttpTransport.newTrustedTransport();
	}

	@Bean
	public GoogleAuthorizationCodeFlow ytAuthFlow() throws IOException, GeneralSecurityException {
		FileDataStoreFactory dataStoreFactory = new FileDataStoreFactory(dataStoreDir);

		GoogleClientSecrets secrets = GoogleClientSecrets.load(jacksonFactory(),
				new InputStreamReader(YoutubeConfig.class.getResourceAsStream("/oauth-google.json")));

		return new GoogleAuthorizationCodeFlow
				.Builder(httpTransport(), jacksonFactory(), secrets, Arrays.asList(YOUTUBE_UPLOAD, YOUTUBE))
				.setDataStoreFactory(dataStoreFactory)
				.build();
	}

	@Bean
	public YoutubeSession ytSession() {
		return new YoutubeSession();
	}
}
