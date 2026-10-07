package org.breizhcamp.video.uploader.web

import com.google.api.services.youtube.model.Channel
import com.google.api.services.youtube.model.Playlist
import org.breizhcamp.video.uploader.event.service.EventService
import org.breizhcamp.video.uploader.file.service.FileService
import org.breizhcamp.video.uploader.file.service.MetadataBackupService
import org.breizhcamp.video.uploader.file.service.YoutubeMetadataReset
import org.breizhcamp.video.uploader.shared.session.YoutubeSession
import org.breizhcamp.video.uploader.shared.batch.BatchProgress
import org.breizhcamp.video.uploader.shared.batch.BatchProgressTracker
import org.breizhcamp.video.uploader.shared.config.PathsReport
import org.breizhcamp.video.uploader.video.service.VideoService
import org.breizhcamp.video.uploader.video.service.YoutubeService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.model
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.nio.file.Paths

class HomeControllerTest {

    private val fileService = mock(FileService::class.java)
    private val youtubeService = mock(YoutubeService::class.java)
    private val ytSession = YoutubeSession()
    private val batchProgress = mock(BatchProgressTracker::class.java)
    private val pathsReport = mock(PathsReport::class.java)
    private val metadataBackup = mock(MetadataBackupService::class.java)
    private val youtubeMetadataReset = mock(YoutubeMetadataReset::class.java)
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
                metadataBackup,
                youtubeMetadataReset,
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

    @Test
    fun `should tell where the metadata were saved`() {
        `when`(metadataBackup.backup()).thenReturn(
            MetadataBackupService.Backup(Paths.get("videos/metadata-backup-20261007-214512.zip"), files = 97)
        )

        mockMvc.perform(post("/backupMetadata"))
            .andExpect(redirectedUrl("/"))
            .andExpect(flash().attribute("backupMessage", "97 fichiers .json sauvegardés dans metadata-backup-20261007-214512.zip"))
            .andExpect(flash().attribute("backupFailed", false))
    }

    @Test
    fun `should say so when there is nothing to save`() {
        `when`(metadataBackup.backup()).thenReturn(null)

        mockMvc.perform(post("/backupMetadata"))
            .andExpect(flash().attribute("backupMessage", "Aucun fichier .json à sauvegarder"))
            .andExpect(flash().attribute("backupFailed", true))
    }

    @Test
    fun `should report a backup that could not be written`() {
        `when`(metadataBackup.backup()).thenThrow(java.io.UncheckedIOException(java.io.IOException("No space left on device")))

        mockMvc.perform(post("/backupMetadata"))
            .andExpect(flash().attribute("backupMessage", "Sauvegarde impossible : No space left on device"))
            .andExpect(flash().attribute("backupFailed", true))
    }

    @Test
    fun `should not reset the YouTube metadata without the exact confirmation`() {
        mockMvc.perform(post("/resetYoutubeMetadata").param("confirmation", "oui"))
            .andExpect(redirectedUrl("/"))
            .andExpect(flash().attribute("backupMessage", "Confirmation incorrecte : rien n'a été supprimé"))
            .andExpect(flash().attribute("backupFailed", true))

        verify(youtubeMetadataReset, never()).reset()
    }

    @Test
    fun `should not reset the YouTube metadata while an operation is running`() {
        `when`(batchProgress.running()).thenReturn(listOf(BatchProgress("uploads", "Envoi des vidéos", 3, 1)))

        mockMvc.perform(post("/resetYoutubeMetadata").param("confirmation", HomeController.RESET_PHRASE))
            .andExpect(flash().attribute("backupMessage", "Une opération est en cours (Envoi des vidéos) : rien n'a été supprimé"))

        verify(youtubeMetadataReset, never()).reset()
    }

    @Test
    fun `should reset the YouTube metadata once confirmed, and tell where the backup went`() {
        `when`(youtubeMetadataReset.reset()).thenReturn(
            YoutubeMetadataReset.Result(
                backup = MetadataBackupService.Backup(Paths.get("videos/metadata-backup-20261007-214512.zip"), files = 97),
                cleaned = 95,
                failed = emptyList(),
            )
        )

        mockMvc.perform(post("/resetYoutubeMetadata").param("confirmation", HomeController.RESET_PHRASE))
            .andExpect(flash().attribute(
                "backupMessage",
                "Métadonnées YouTube supprimées de 95 fichiers, sauvegardés avant dans metadata-backup-20261007-214512.zip",
            ))
            .andExpect(flash().attribute("backupFailed", false))
    }

    @Test
    fun `should name the files the reset could not read`() {
        `when`(youtubeMetadataReset.reset()).thenReturn(
            YoutubeMetadataReset.Result(
                backup = MetadataBackupService.Backup(Paths.get("videos/metadata-backup-20261007-214512.zip"), files = 2),
                cleaned = 1,
                failed = listOf(Paths.get("videos/talk/metadata.json")),
            )
        )

        mockMvc.perform(post("/resetYoutubeMetadata").param("confirmation", HomeController.RESET_PHRASE))
            .andExpect(flash().attribute(
                "backupMessage",
                "Métadonnées YouTube supprimées de 1 fichiers, sauvegardés avant dans metadata-backup-20261007-214512.zip. " +
                    "Illisibles, laissés tels quels : talk/metadata.json",
            ))
            .andExpect(flash().attribute("backupFailed", true))
    }

    private fun connected() {
        `when`(youtubeService.isConnected()).thenReturn(true)
        `when`(youtubeService.hasStoredCredential()).thenReturn(true)
    }
}
