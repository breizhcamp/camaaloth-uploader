package org.breizhcamp.video.uploader.config;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.youtube.YouTube;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class YoutubeConfig {

    @Bean
    public YouTube youtube(HttpTransport httpTransport, JacksonFactory jacksonFactory, Credential ytCredential) {
        return new YouTube.Builder(httpTransport, jacksonFactory, ytCredential)
                .setApplicationName("yt-uploader/1.0")
                .build();
    }
}
