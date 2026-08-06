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
import org.breizhcamp.video.uploader.event.domain.Event
import org.breizhcamp.video.uploader.event.service.EventService
import org.breizhcamp.video.uploader.shared.config.YoutubeAuthConfig.Companion.YT_USER_ID
import org.breizhcamp.video.uploader.shared.session.PlaylistStore
import org.breizhcamp.video.uploader.shared.session.SelectedPlaylist
import org.breizhcamp.video.uploader.shared.session.YoutubeSession
import org.breizhcamp.video.uploader.video.domain.VideoInfo
import org.breizhcamp.video.uploader.video.repository.YoutubeLibrary
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.timeout
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.messaging.simp.SimpMessagingTemplate
import java.nio.file.Paths
import java.net.URLDecoder
import java.nio.charset.StandardCharsets.UTF_8

class YoutubeServiceTest {

    private lateinit var flow: GoogleAuthorizationCodeFlow
    private lateinit var service: YoutubeService
    private val youtubeLibrary = mock(YoutubeLibrary::class.java)
    private val videoService = mock(VideoService::class.java)
    private val eventService = mock(EventService::class.java)
    private val playlistStore = mock(PlaylistStore::class.java)
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
            videoService,
            eventService,
            flow,
            mock(SimpMessagingTemplate::class.java),
            youtubeLibrary,
            ytSession,
            playlistStore,
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
    fun `should restore the playlist selected before the restart`() {
        val playlist = Playlist().apply { id = "PL-1" }
        `when`(youtubeLibrary.getChannels()).thenReturn(listOf(Channel().apply { id = "channel-1" }))
        `when`(youtubeLibrary.getPlaylists("channel-1")).thenReturn(listOf(playlist))
        `when`(playlistStore.read()).thenReturn(SelectedPlaylist(id = "PL-1", title = "BreizhCamp 2026"))

        service.loadSession()

        assertThat(ytSession.curPlaylist).isEqualTo(playlist)
    }

    @Test
    fun `should ignore a saved playlist the channel no longer owns`() {
        `when`(youtubeLibrary.getChannels()).thenReturn(listOf(Channel().apply { id = "channel-1" }))
        `when`(youtubeLibrary.getPlaylists("channel-1")).thenReturn(listOf(Playlist().apply { id = "PL-2" }))
        `when`(playlistStore.read()).thenReturn(SelectedPlaylist(id = "PL-gone", title = "Disparue"))

        service.loadSession()

        assertThat(ytSession.curPlaylist).isNull()
    }

    @Test
    fun `should remember the playlist the user selects`() {
        val playlist = Playlist().apply { id = "PL-1" }
        ytSession.playlists = listOf(playlist)

        service.selectPlaylist("PL-1")

        assertThat(ytSession.curPlaylist).isEqualTo(playlist)
        verify(playlistStore).write(playlist)
    }

    @Test
    fun `should forget the playlist when none is selected`() {
        ytSession.playlists = listOf(Playlist().apply { id = "PL-1" })
        ytSession.curPlaylist = Playlist().apply { id = "PL-1" }

        service.selectPlaylist("none")

        assertThat(ytSession.curPlaylist).isNull()
        verify(playlistStore).write(null)
    }

    @Test
    fun `should keep the current selection when the playlist is unknown`() {
        val playlist = Playlist().apply { id = "PL-1" }
        ytSession.playlists = listOf(playlist)
        ytSession.curPlaylist = playlist

        service.selectPlaylist("PL-unknown")

        assertThat(ytSession.curPlaylist).isEqualTo(playlist)
        verify(playlistStore, never()).write(null)
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
    fun `should push the description and the thumbnail of a video already online`() {
        val video = videoOnline(thumbnail = Paths.get("videos/talk/thumb.png"))
        `when`(eventService.findEventBy("1183944")).thenReturn(Event(id = "1183944", description = "Le résumé"))

        service.pushMetadata(video)

        verify(youtubeLibrary).updateDescription("yt-1", "Le résumé")
        verify(youtubeLibrary).uploadThumbnail(video)
    }

    @Test
    fun `should skip the description when the schedule has none`() {
        val video = videoOnline(thumbnail = null)
        `when`(eventService.findEventBy("1183944")).thenReturn(Event(id = "1183944", description = "   "))

        service.pushMetadata(video)

        verify(youtubeLibrary, never()).updateDescription(anyString(), anyString())
        verify(youtubeLibrary, never()).uploadThumbnail(video)
    }

    @Test
    fun `should ignore a video that was never uploaded`() {
        service.pushMetadata(videoOnline(thumbnail = null).apply { youtubeId = null })

        verify(youtubeLibrary, never()).updateDescription(anyString(), anyString())
    }

    @Test
    fun `should leave the recorded status alone once the metadata is pushed`() {
        val video = videoOnline(thumbnail = null)
        `when`(eventService.findEventBy("1183944")).thenReturn(Event(id = "1183944", description = "Le résumé"))

        service.pushMetadata(video)

        assertThat(video.status).isEqualTo(VideoInfo.Status.DONE)
        verify(videoService, never()).updateVideo(video)
    }

    @Test
    fun `should report a failure without touching the file on disk`() {
        val video = videoOnline(thumbnail = null)
        `when`(eventService.findEventBy("1183944")).thenReturn(Event(id = "1183944", description = "Le résumé"))
        doThrow(IllegalStateException("YouTube does not know any video [yt-1]"))
            .`when`(youtubeLibrary).updateDescription(anyString(), anyString())

        service.pushMetadata(video)

        assertThat(video.status).isEqualTo(VideoInfo.Status.FAILED)
        verify(videoService, never()).updateVideo(video)
    }

    @Test
    fun `should queue every video already online`() {
        val online = videoOnline(thumbnail = null)
        val notUploaded = videoOnline(thumbnail = null).apply { youtubeId = null }
        `when`(videoService.list()).thenReturn(listOf(online, notUploaded))
        `when`(eventService.findEventBy("1183944")).thenReturn(Event(id = "1183944", description = "Le résumé"))

        service.syncAllMetadata()

        verify(youtubeLibrary, timeout(1000)).updateDescription("yt-1", "Le résumé")
        verify(youtubeLibrary, never()).updateDescription("yt-2", "Le résumé")
    }

    private fun videoOnline(thumbnail: java.nio.file.Path?) = VideoInfo(
        path = Paths.get("videos/talk/1080p.mp4"),
        thumbnail = thumbnail,
        eventId = "1183944",
        status = VideoInfo.Status.DONE,
        youtubeId = "yt-1",
    )

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
