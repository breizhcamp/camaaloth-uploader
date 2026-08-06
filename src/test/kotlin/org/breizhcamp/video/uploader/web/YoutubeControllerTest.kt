package org.breizhcamp.video.uploader.web

import assertk.assertThat
import assertk.assertions.isNull
import com.google.api.services.youtube.model.Channel
import com.google.api.services.youtube.model.Playlist
import org.breizhcamp.video.uploader.file.service.FileService
import org.breizhcamp.video.uploader.shared.session.YoutubeSession
import org.breizhcamp.video.uploader.video.domain.VideoInfo
import org.breizhcamp.video.uploader.video.service.VideoService
import org.breizhcamp.video.uploader.video.service.YoutubeService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import java.nio.file.Paths
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class YoutubeControllerTest {

    private val youtubeService = mock(YoutubeService::class.java)
    private val fileService = mock(FileService::class.java)
    private val videoService = mock(VideoService::class.java)
    private val ytSession = YoutubeSession()
    private lateinit var controller: YoutubeController
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        `when`(youtubeService.getAuthUrl(anyString())).thenReturn(AUTH_URL)

        controller = YoutubeController(youtubeService, fileService, videoService, ytSession)
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build()
    }

    @Test
    fun `should redirect to Google using the base url given by the browser`() {
        mockMvc.perform(get("/yt/auth").param("baseUrl", "http://localhost:8080/"))
            .andExpect(status().is3xxRedirection())

        verify(youtubeService).getAuthUrl("http://localhost:8080/yt/return")
    }

    @Test
    fun `should fall back on the current request when no base url is given`() {
        mockMvc.perform(get("/yt/auth"))
            .andExpect(status().is3xxRedirection())

        verify(youtubeService).getAuthUrl("http://localhost/yt/return")
    }

    @Test
    fun `should ignore the query string of the base url`() {
        mockMvc.perform(get("/yt/auth").param("baseUrl", "http://localhost:8080/?done=1"))
            .andExpect(status().is3xxRedirection())

        verify(youtubeService).getAuthUrl("http://localhost:8080/yt/return")
    }

    @Test
    fun `should forget the stored credential and empty the session`() {
        ytSession.channels = listOf(Channel())
        ytSession.currentChannel = Channel()
        ytSession.playlists = listOf(Playlist())
        ytSession.curPlaylist = Playlist()

        mockMvc.perform(post("/yt/disconnect"))
            .andExpect(status().is3xxRedirection())

        verify(youtubeService).disconnect()
        assertThat(ytSession.channels).isNull()
        assertThat(ytSession.currentChannel).isNull()
        assertThat(ytSession.playlists).isNull()
        assertThat(ytSession.curPlaylist).isNull()
    }

    @Test
    fun `should purge then send the user straight back to Google`() {
        ytSession.currentChannel = Channel()

        mockMvc.perform(post("/yt/reconnect"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl(AUTH_URL))

        verify(youtubeService).disconnect()
        verify(youtubeService).getAuthUrl("http://localhost/yt/return")
        assertThat(ytSession.currentChannel).isNull()
    }

    @Test
    fun `should hand the playlist choice over to the service`() {
        mockMvc.perform(post("/yt/curPlaylist").param("playlist", "PL-1"))
            .andExpect(status().is3xxRedirection())

        verify(youtubeService).selectPlaylist("PL-1")
    }

    @Test
    fun `should queue the description of every video already online`() {
        mockMvc.perform(post("/yt/syncDescriptionsAll"))
            .andExpect(status().is3xxRedirection())

        verify(youtubeService).syncAllDescriptions()
    }

    @Test
    fun `should queue the thumbnail of every video already online`() {
        mockMvc.perform(post("/yt/syncThumbnailsAll"))
            .andExpect(status().is3xxRedirection())

        verify(youtubeService).syncAllThumbnails()
    }

    @Test
    fun `should queue the description of a single video`() {
        val dirName = "24.Amphi D.13-30 - Un talk (Alice Simon) - 1183944"
        val video = VideoInfo(
            path = Paths.get(dirName, "1080p.mp4"),
            thumbnail = null,
            eventId = "1183944",
            status = VideoInfo.Status.DONE,
            youtubeId = "yt-1",
        )
        `when`(fileService.recordingDir).thenReturn(Paths.get("videos"))
        `when`(videoService.getInformationsFrom(Paths.get("videos", dirName))).thenReturn(video)

        controller.syncDescription(dirName)
        controller.syncThumbnail(dirName)

        verify(youtubeService).syncDescription(video)
        verify(youtubeService).syncThumbnail(video)
    }

    @Test
    fun `should ignore a directory carrying no event id`() {
        controller.syncDescription("un répertoire sans identifiant")

        verifyNoInteractions(videoService)
    }

    companion object {
        private const val AUTH_URL = "https://accounts.google.com/o/oauth2/auth?client_id=test"
    }
}
