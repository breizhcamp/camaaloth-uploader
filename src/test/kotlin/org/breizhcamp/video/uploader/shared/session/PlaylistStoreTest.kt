package org.breizhcamp.video.uploader.shared.session

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.api.services.youtube.model.Playlist
import com.google.api.services.youtube.model.PlaylistSnippet
import org.breizhcamp.video.uploader.file.service.FileService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.nio.file.Files
import java.nio.file.Path

class PlaylistStoreTest {

    private val fileService = mock(FileService::class.java)
    private lateinit var store: PlaylistStore

    @BeforeEach
    fun setUp(@TempDir dir: Path) {
        `when`(fileService.recordingDir).thenReturn(dir)
        store = PlaylistStore(fileService, jacksonObjectMapper())
    }

    @Test
    fun `should read back the playlist it wrote`() {
        store.write(playlist(id = "PL-1", title = "BreizhCamp 2026"))

        assertThat(store.read()).isEqualTo(SelectedPlaylist(id = "PL-1", title = "BreizhCamp 2026"))
    }

    @Test
    fun `should survive a restart of the whole store`(@TempDir dir: Path) {
        `when`(fileService.recordingDir).thenReturn(dir)
        store.write(playlist(id = "PL-1", title = "BreizhCamp 2026"))

        val reopened = PlaylistStore(fileService, jacksonObjectMapper())

        assertThat(reopened.read()?.id).isEqualTo("PL-1")
    }

    @Test
    fun `should forget the selection when nothing is selected`() {
        store.write(playlist(id = "PL-1", title = "BreizhCamp 2026"))

        store.write(null)

        assertThat(store.read()).isNull()
        assertThat(Files.exists(fileService.recordingDir.resolve("playlist.json"))).isFalse()
    }

    @Test
    fun `should read nothing when no selection was ever made`() {
        assertThat(store.read()).isNull()
    }

    @Test
    fun `should read nothing rather than fail on a damaged file`() {
        Files.writeString(fileService.recordingDir.resolve("playlist.json"), "{ ceci n'est pas du json")

        assertThat(store.read()).isNull()
    }

    @Test
    fun `should not fail when the recording directory does not exist`(@TempDir dir: Path) {
        `when`(fileService.recordingDir).thenReturn(dir.resolve("absent"))

        store.write(playlist(id = "PL-1", title = "BreizhCamp 2026"))

        assertThat(store.read()?.id).isEqualTo("PL-1")
    }

    private fun playlist(id: String, title: String) = Playlist().apply {
        this.id = id
        snippet = PlaylistSnippet().apply { this.title = title }
    }
}
