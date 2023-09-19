package org.breizhcamp.video.uploader.config

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.http.HttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.youtube.YouTube
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.test.context.ActiveProfiles

@Configuration
@ActiveProfiles("e2e")
@Primary
class YoutubeConfig {

    @Bean
    fun youtube(httpTransport: HttpTransport?, jacksonFactory: JacksonFactory?, ytCredential: Credential?): YouTube {
        return YouTube.Builder(httpTransport, jacksonFactory, ytCredential)
            .setApplicationName("yt-uploader/1.0")
            .setRootUrl("http://localhost:20000")
            .build()
    }
}