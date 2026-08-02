package org.breizhcamp.video.uploader.shared.config

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
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

/**
 * Configuration file for Youtube access
 */
@Configuration
class YoutubeAuthConfig(
    @Value("\${videos.dir:./videos}/.datastore") private val dataStoreDir: File,
    private val secretsLoader: GoogleClientSecretsLoader,
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
        val secrets = secretsLoader.load(jacksonFactory)
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
