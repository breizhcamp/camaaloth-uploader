package org.breizhcamp.video.uploader.web

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.breizhcamp.video.uploader.event.service.EventService
import org.breizhcamp.video.uploader.file.service.FileService
import org.breizhcamp.video.uploader.video.service.VideoService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.nio.file.Files
import java.nio.file.Path

private const val TALK_DIR = "24.Amphi A.10-00 - Un talk (Alice Simon) - 1181830"

class VideoFileControllerTest {

    private val fileService = mock(FileService::class.java)
    private lateinit var recordingDir: Path
    private lateinit var talkDir: Path
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp(@TempDir dir: Path) {
        recordingDir = dir
        talkDir = Files.createDirectory(dir.resolve(TALK_DIR))
        `when`(fileService.recordingDir).thenReturn(dir)

        val videoService = VideoService(fileService, jacksonObjectMapper(), mock(EventService::class.java))
        mockMvc = MockMvcBuilders.standaloneSetup(VideoFileController(fileService, videoService)).build()
    }

    @Test
    fun `should serve the normalized video by default`() {
        Files.write(talkDir.resolve("1080p.mp4"), byteArrayOf(1, 2, 3))
        Files.write(talkDir.resolve("1080p.normalized.mp4"), byteArrayOf(4, 5, 6))

        mockMvc.perform(get("/video").param("dir", TALK_DIR))
            .andExpect(status().isOk)
            .andExpect(content().contentType("video/mp4"))
            .andExpect(content().bytes(byteArrayOf(4, 5, 6)))
    }

    @Test
    fun `should serve the original recording when asked`() {
        Files.write(talkDir.resolve("1080p.mp4"), byteArrayOf(1, 2, 3))
        Files.write(talkDir.resolve("1080p.normalized.mp4"), byteArrayOf(4, 5, 6))

        mockMvc.perform(get("/video").param("dir", TALK_DIR).param("original", "true"))
            .andExpect(status().isOk)
            .andExpect(content().bytes(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `should serve only the requested range, for the player to seek`() {
        Files.write(talkDir.resolve("1080p.normalized.mp4"), byteArrayOf(4, 5, 6, 7))

        mockMvc.perform(get("/video").param("dir", TALK_DIR).header(HttpHeaders.RANGE, "bytes=1-2"))
            .andExpect(status().isPartialContent)
            .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 1-2/4"))
            .andExpect(content().bytes(byteArrayOf(5, 6)))
    }

    @Test
    fun `should answer not found when the video is not normalized yet`() {
        Files.write(talkDir.resolve("1080p.mp4"), byteArrayOf(1, 2, 3))

        mockMvc.perform(get("/video").param("dir", TALK_DIR))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `should refuse to walk out of the recording directory`(@TempDir outside: Path) {
        Files.write(outside.resolve("1080p.normalized.mp4"), byteArrayOf(9))

        mockMvc.perform(get("/video").param("dir", "../${outside.fileName}"))
            .andExpect(status().isNotFound)

        mockMvc.perform(get("/video").param("dir", outside.toString()))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `should refuse a directory reaching into a sub folder`() {
        val nested = Files.createDirectories(talkDir.resolve("sub"))
        Files.write(nested.resolve("1080p.normalized.mp4"), byteArrayOf(7))

        mockMvc.perform(get("/video").param("dir", "$TALK_DIR/sub"))
            .andExpect(status().isNotFound)
    }
}
