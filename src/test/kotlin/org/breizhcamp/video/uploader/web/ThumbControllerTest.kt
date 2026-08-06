package org.breizhcamp.video.uploader.web

import org.breizhcamp.video.uploader.file.service.FileService
import org.breizhcamp.video.uploader.shared.batch.BatchProgressTracker
import org.breizhcamp.video.uploader.thumb.ThumbGenerationTask
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.nio.file.Files
import java.nio.file.Path

private const val TALK_DIR = "24.Amphi A.10-00 - Un talk (Alice Simon) - 1181830"

class ThumbControllerTest {

    private val fileService = mock(FileService::class.java)
    private lateinit var recordingDir: Path
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp(@TempDir dir: Path) {
        recordingDir = dir
        `when`(fileService.recordingDir).thenReturn(dir)

        mockMvc = MockMvcBuilders.standaloneSetup(
            ThumbController(
                mock(ThumbGenerationTask::class.java),
                mock(BatchProgressTracker::class.java),
                fileService,
            )
        ).build()
    }

    @Test
    fun `should serve the thumbnail of a talk`() {
        Files.createDirectory(recordingDir.resolve(TALK_DIR))
        Files.write(recordingDir.resolve(TALK_DIR).resolve("thumb.png"), byteArrayOf(1, 2, 3))

        mockMvc.perform(get("/thumb").param("dir", TALK_DIR))
            .andExpect(status().isOk)
            .andExpect(content().contentType("image/png"))
            .andExpect(content().bytes(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `should answer not found when the talk has no thumbnail`() {
        Files.createDirectory(recordingDir.resolve(TALK_DIR))

        mockMvc.perform(get("/thumb").param("dir", TALK_DIR))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `should refuse to walk out of the recording directory`(@TempDir outside: Path) {
        val secret = Files.write(outside.resolve("thumb.png"), byteArrayOf(9))

        mockMvc.perform(get("/thumb").param("dir", "../${outside.fileName}"))
            .andExpect(status().isNotFound)

        mockMvc.perform(get("/thumb").param("dir", secret.parent.toString()))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `should refuse a directory reaching into a sub folder`() {
        val nested = Files.createDirectories(recordingDir.resolve(TALK_DIR).resolve("sub"))
        Files.write(nested.resolve("thumb.png"), byteArrayOf(7))

        mockMvc.perform(get("/thumb").param("dir", "$TALK_DIR/sub"))
            .andExpect(status().isNotFound)
    }
}
