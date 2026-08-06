package org.breizhcamp.video.uploader.video.service

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.MemoryDataStoreFactory
import com.google.api.services.youtube.YouTubeScopes
import com.google.api.services.youtube.model.Channel
import com.google.api.services.youtube.model.Playlist
import org.breizhcamp.video.uploader.event.service.EventService
import org.breizhcamp.video.uploader.shared.config.YoutubeAuthConfig.Companion.YT_USER_ID
import org.breizhcamp.video.uploader.shared.session.YoutubeSession
import org.breizhcamp.video.uploader.video.repository.YoutubeLibrary
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.messaging.simp.SimpMessagingTemplate
import java.net.URLDecoder
import java.nio.charset.StandardCharsets.UTF_8

class YoutubeServiceTest {

    private lateinit var flow: GoogleAuthorizationCodeFlow
    private lateinit var service: YoutubeService
    private val youtubeLibrary = mock(YoutubeLibrary::class.java)
    private val ytSession = YoutubeSession()

    @BeforeEach
    fun setUp() {
        flow = GoogleAuthorizationCodeFlow.Builder(
            NetHttpTransport(),
            JacksonFactory.getDefaultInstance(),
            secrets(),
            listOf(YouTubeScopes.YOUTUBE_UPLOAD, YouTubeScopes.YOUTUBE),
        ).setDataStoreFactory(MemoryDataStoreFactory()).build()

        service = YoutubeService(
            mock(VideoService::class.java),
            mock(EventService::class.java),
            flow,
            mock(SimpMessagingTemplate::class.java),
            youtubeLibrary,
            ytSession,
        )
    }

    @Test
    fun `should select the only channel and load its playlists`() {
        val channel = Channel().apply { id = "channel-1" }
        val playlist = Playlist().apply { id = "playlist-1" }
        `when`(youtubeLibrary.getChannels()).thenReturn(listOf(channel))
        `when`(youtubeLibrary.getPlaylists("channel-1")).thenReturn(listOf(playlist))

        service.loadSession()

        assertThat(ytSession.channels).isEqualTo(listOf(channel))
        assertThat(ytSession.currentChannel).isEqualTo(channel)
        assertThat(ytSession.playlists).isEqualTo(listOf(playlist))
    }

    @Test
    fun `should not pick a channel when the account owns several`() {
        `when`(youtubeLibrary.getChannels())
            .thenReturn(listOf(Channel().apply { id = "a" }, Channel().apply { id = "b" }))

        service.loadSession()

        assertThat(ytSession.channels).isNotNull().hasSize(2)
        assertThat(ytSession.currentChannel).isNull()
        verify(youtubeLibrary, never()).getPlaylists(anyString())
    }

    @Test
    fun `should load the session on first use when connected`() {
        `when`(youtubeLibrary.isConnected()).thenReturn(true)
        `when`(youtubeLibrary.getChannels()).thenReturn(listOf(Channel().apply { id = "channel-1" }))

        service.loadSessionIfNeeded()

        assertThat(ytSession.channels).isNotNull().hasSize(1)
    }

    @Test
    fun `should not reload a session already loaded`() {
        `when`(youtubeLibrary.isConnected()).thenReturn(true)
        ytSession.channels = emptyList()

        service.loadSessionIfNeeded()

        verify(youtubeLibrary, never()).getChannels()
    }

    @Test
    fun `should not load the session when disconnected`() {
        `when`(youtubeLibrary.isConnected()).thenReturn(false)

        service.loadSessionIfNeeded()

        verify(youtubeLibrary, never()).getChannels()
        assertThat(ytSession.channels).isNull()
    }

    @Test
    fun `should keep the session empty when YouTube cannot be reached`() {
        `when`(youtubeLibrary.isConnected()).thenReturn(true)
        `when`(youtubeLibrary.getChannels()).thenThrow(IllegalStateException("Not connected"))

        service.loadSessionIfNeeded()

        assertThat(ytSession.channels).isNull()
    }

    @Test
    fun `should ask Google for the account picker and a fresh consent`() {
        val url = URLDecoder.decode(service.getAuthUrl("http://localhost:8080/yt/return"), UTF_8)

        assertThat(url).contains("access_type=offline")
        assertThat(url).contains("prompt=select_account consent")
    }

    @Test
    fun `should keep the stored refresh token when Google does not return a new one`() {
        flow.createAndStoreCredential(
            GoogleTokenResponse().apply {
                accessToken = "old-access"
                refreshToken = "stored-refresh"
                expiresInSeconds = 3600
            },
            YT_USER_ID,
        )

        service.saveToken(
            GoogleTokenResponse().apply {
                accessToken = "new-access"
                expiresInSeconds = 3600
            }
        )

        val credential = requireNotNull(flow.loadCredential(YT_USER_ID))
        assertThat(credential.accessToken).isEqualTo("new-access")
        assertThat(credential.refreshToken).isEqualTo("stored-refresh")
    }

    @Test
    fun `should store the refresh token returned by Google`() {
        service.saveToken(
            GoogleTokenResponse().apply {
                accessToken = "new-access"
                refreshToken = "new-refresh"
                expiresInSeconds = 3600
            }
        )

        val credential = requireNotNull(flow.loadCredential(YT_USER_ID))
        assertThat(credential.refreshToken).isEqualTo("new-refresh")
    }

    private fun secrets() = GoogleClientSecrets().setInstalled(
        GoogleClientSecrets.Details()
            .setClientId("client-id")
            .setClientSecret("client-secret")
    )
}
