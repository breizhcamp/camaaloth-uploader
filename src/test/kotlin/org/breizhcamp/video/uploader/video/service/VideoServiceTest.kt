package org.breizhcamp.video.uploader.video.service

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.breizhcamp.video.uploader.event.service.EventService
import org.breizhcamp.video.uploader.file.service.FileService
import org.breizhcamp.video.uploader.video.domain.PushStatus
import org.breizhcamp.video.uploader.video.domain.VideoInfo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.nio.file.Files
import java.nio.file.Path

class VideoServiceTest {

    private val fileService = mock(FileService::class.java)
    private lateinit var service: VideoService
    private lateinit var talkDir: Path

    @BeforeEach
    fun setUp(@TempDir dir: Path) {
        talkDir = Files.createDirectory(dir.resolve("24.Amphi A.10-00 - Un talk (Alice Simon) - 1181830"))
        Files.createFile(talkDir.resolve("1080p.mp4"))
        `when`(fileService.recordingDir).thenReturn(dir)

        service = VideoService(fileService, jacksonObjectMapper(), mock(EventService::class.java))
    }

    @Test
    fun `should read back the push statuses it wrote`() {
        service.updateVideo(video().apply {
            descriptionStatus = PushStatus.DONE
            thumbnailStatus = PushStatus.FAILED
        })

        val reloaded = requireNotNull(service.getInformationsFrom(talkDir))
        assertThat(reloaded.descriptionStatus).isEqualTo(PushStatus.DONE)
        assertThat(reloaded.thumbnailStatus).isEqualTo(PushStatus.FAILED)
    }

    @Test
    fun `should treat a metadata file written before the push statuses as nothing pushed`() {
        Files.writeString(
            talkDir.resolve("metadata.json"),
            """{"status":"DONE","youtubeId":"abc123"}""",
        )

        val reloaded = requireNotNull(service.getInformationsFrom(talkDir))
        assertThat(reloaded.status).isEqualTo(VideoInfo.Status.DONE)
        assertThat(reloaded.youtubeId).isEqualTo("abc123")
        assertThat(reloaded.descriptionStatus).isEqualTo(PushStatus.NOT_STARTED)
        assertThat(reloaded.thumbnailStatus).isEqualTo(PushStatus.NOT_STARTED)
    }

    @Test
    fun `should leave the upload status and the youtube id untouched`() {
        service.updateVideo(video().apply {
            status = VideoInfo.Status.DONE
            youtubeId = "abc123"
            descriptionStatus = PushStatus.DONE
        })

        val reloaded = requireNotNull(service.getInformationsFrom(talkDir))
        assertThat(reloaded.status).isEqualTo(VideoInfo.Status.DONE)
        assertThat(reloaded.youtubeId).isEqualTo("abc123")
    }

    private fun video() = VideoInfo(
        path = talkDir.resolve("1080p.mp4"),
        thumbnail = null,
        eventId = "1181830",
        status = VideoInfo.Status.DONE,
    )
}
