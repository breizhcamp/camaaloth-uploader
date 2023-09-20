package org.breizhcamp.video.uploader.shared.config

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.HttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.youtube.YouTubeScopes
import org.breizhcamp.video.uploader.shared.session.YoutubeSession
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.File
import java.io.InputStreamReader

/**
 * Configuration file for Youtube access
 */
@Configuration
class YoutubeAuthConfig(
    @Value("\${videos.dir:./videos}/.datastore") private val dataStoreDir: File
) {

    @Bean
    fun jacksonFactory(): JacksonFactory {
        return JacksonFactory.getDefaultInstance()
    }

    @Bean
    fun httpTransport(): HttpTransport {
        return GoogleNetHttpTransport.newTrustedTransport()
    }

    @Bean
    fun ytAuthFlow(jacksonFactory: JacksonFactory, httpTransport: HttpTransport): GoogleAuthorizationCodeFlow {
        val secrets = GoogleClientSecrets.load(
            jacksonFactory, InputStreamReader(
                YoutubeAuthConfig::class.java.getResourceAsStream("/oauth-google.json")
                    ?: error("No oauth-google.json file")
            )
        )
        return GoogleAuthorizationCodeFlow.Builder(
            httpTransport,
            jacksonFactory,
            secrets,
            listOf(YouTubeScopes.YOUTUBE_UPLOAD, YouTubeScopes.YOUTUBE)
        ).setDataStoreFactory(FileDataStoreFactory(dataStoreDir))
            .build()
    }

    @Bean
    fun ytSession(): YoutubeSession {
        return YoutubeSession()
    }

    companion object {
        const val YT_USER_ID = "user"
    }
}
