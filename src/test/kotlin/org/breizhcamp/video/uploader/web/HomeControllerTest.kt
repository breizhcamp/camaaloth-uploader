package org.breizhcamp.video.uploader.web

import com.google.api.services.youtube.model.Channel
import com.google.api.services.youtube.model.Playlist
import org.breizhcamp.video.uploader.event.service.EventService
import org.breizhcamp.video.uploader.file.service.FileService
import org.breizhcamp.video.uploader.shared.session.YoutubeSession
import org.breizhcamp.video.uploader.shared.batch.BatchProgressTracker
import org.breizhcamp.video.uploader.shared.config.PathsReport
import org.breizhcamp.video.uploader.video.service.VideoService
import org.breizhcamp.video.uploader.video.service.YoutubeService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.model
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.nio.file.Paths

class HomeControllerTest {

    private val fileService = mock(FileService::class.java)
    private val youtubeService = mock(YoutubeService::class.java)
    private val ytSession = YoutubeSession()
    private val batchProgress = mock(BatchProgressTracker::class.java)
    private val pathsReport = mock(PathsReport::class.java)
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        `when`(fileService.recordingDir).thenReturn(Paths.get("videos"))
        `when`(batchProgress.running()).thenReturn(emptyList())
        `when`(pathsReport.entries()).thenReturn(emptyList())

        mockMvc = MockMvcBuilders.standaloneSetup(
            HomeController(
                mock(EventService::class.java),
                fileService,
                mock(VideoService::class.java),
                youtubeService,
                ytSession,
                batchProgress,
                pathsReport,
            )
        ).build()
    }

    @Test
    fun `should offer to clear the datastore when the stored credential is unusable`() {
        `when`(youtubeService.isConnected()).thenReturn(false)
        `when`(youtubeService.hasStoredCredential()).thenReturn(true)

        mockMvc.perform(get("/"))
            .andExpect(model().attribute("connected", false))
            .andExpect(model().attribute("staleCredential", true))
    }

    @Test
    fun `should not offer to clear the datastore when nothing is stored`() {
        `when`(youtubeService.isConnected()).thenReturn(false)
        `when`(youtubeService.hasStoredCredential()).thenReturn(false)

        mockMvc.perform(get("/"))
            .andExpect(model().attribute("staleCredential", false))
    }

    @Test
    fun `should not offer to clear the datastore while connected`() {
        connected()

        mockMvc.perform(get("/"))
            .andExpect(model().attribute("connected", true))
            .andExpect(model().attribute("staleCredential", false))
    }

    @Test
    fun `should load the session before rendering`() {
        connected()

        mockMvc.perform(get("/"))

        verify(youtubeService).loadSessionIfNeeded()
    }

    @Test
    fun `should report no problem while disconnected`() {
        `when`(youtubeService.isConnected()).thenReturn(false)

        mockMvc.perform(get("/"))
            .andExpect(model().attribute("ytProblem", null as Any?))
    }

    @Test
    fun `should report no channel when the account owns none`() {
        connected()
        ytSession.channels = emptyList()

        mockMvc.perform(get("/"))
            .andExpect(model().attribute("ytProblem", "NO_CHANNEL"))
    }

    @Test
    fun `should report no channel when the session could not be loaded`() {
        connected()

        mockMvc.perform(get("/"))
            .andExpect(model().attribute("ytProblem", "NO_CHANNEL"))
    }

    @Test
    fun `should report no channel selected when the account owns several`() {
        connected()
        ytSession.channels = listOf(Channel(), Channel())

        mockMvc.perform(get("/"))
            .andExpect(model().attribute("ytProblem", "NO_CHANNEL_SELECTED"))
    }

    @Test
    fun `should report no playlist when the selected channel has none`() {
        connected()
        ytSession.channels = listOf(Channel())
        ytSession.currentChannel = Channel()
        ytSession.playlists = emptyList()

        mockMvc.perform(get("/"))
            .andExpect(model().attribute("ytProblem", "NO_PLAYLIST"))
    }

    @Test
    fun `should report no problem once a playlist is available`() {
        connected()
        ytSession.channels = listOf(Channel())
        ytSession.currentChannel = Channel()
        ytSession.playlists = listOf(Playlist())

        mockMvc.perform(get("/"))
            .andExpect(model().attribute("ytProblem", null as Any?))
    }

    private fun connected() {
        `when`(youtubeService.isConnected()).thenReturn(true)
        `when`(youtubeService.hasStoredCredential()).thenReturn(true)
    }
}
