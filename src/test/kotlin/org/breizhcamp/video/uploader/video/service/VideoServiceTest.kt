package org.breizhcamp.video.uploader.video.service

import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.breizhcamp.video.uploader.event.domain.Event
import org.breizhcamp.video.uploader.event.service.EventService
import org.breizhcamp.video.uploader.file.service.FileService
import org.breizhcamp.video.uploader.video.domain.Loudness
import org.breizhcamp.video.uploader.video.domain.PushStatus
import org.breizhcamp.video.uploader.video.domain.VideoInfo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.nio.file.Files
import java.nio.file.Path

class VideoServiceTest {

    private val fileService = mock(FileService::class.java)
    private val eventService = mock(EventService::class.java)
    private lateinit var service: VideoService
    private lateinit var talkDir: Path

    @BeforeEach
    fun setUp(@TempDir dir: Path) {
        talkDir = Files.createDirectory(dir.resolve("24.Amphi A.10-00 - Un talk (Alice Simon) - 1181830"))
        Files.createFile(talkDir.resolve("1080p.mp4"))
        `when`(fileService.recordingDir).thenReturn(dir)

        service = VideoService(fileService, jacksonObjectMapper(), eventService)
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

    @Test
    fun `should read the loudness written by the normalization script`() {
        Files.writeString(
            talkDir.resolve("metadata.json"),
            """{"status":"NOT_STARTED","loudness":{"integrated":-23.0,"truePeak":-16.8}}""",
        )

        val reloaded = requireNotNull(service.getInformationsFrom(talkDir))
        assertThat(reloaded.loudness).isEqualTo(Loudness(integrated = -23.0, truePeak = -16.8))
    }

    @Test
    fun `should keep the loudness when the upload rewrites the metadata`() {
        Files.writeString(
            talkDir.resolve("metadata.json"),
            """{"status":"NOT_STARTED","loudness":{"integrated":-23.0,"truePeak":-16.8}}""",
        )

        val video = requireNotNull(service.getInformationsFrom(talkDir))
        service.updateVideo(video.apply { status = VideoInfo.Status.DONE; youtubeId = "abc123" })

        val reloaded = requireNotNull(service.getInformationsFrom(talkDir))
        assertThat(reloaded.loudness).isEqualTo(Loudness(integrated = -23.0, truePeak = -16.8))
    }

    @Test
    fun `should leave the loudness empty for a video never normalized`() {
        service.updateVideo(video())

        assertThat(requireNotNull(service.getInformationsFrom(talkDir)).loudness).isNull()
        assertThat(Files.readString(talkDir.resolve("metadata.json"))).doesNotContain("loudness")
    }

    @Test
    fun `should carry the talk description on every listed video`() {
        `when`(eventService.getEvents()).thenReturn(
            listOf(Event(id = "1181830", name = "Un talk", description = "Le résumé du talk"))
        )

        assertThat(service.list().single().description).isEqualTo("Le résumé du talk")
    }

    @Test
    fun `should read the schedule once for the whole list`() {
        `when`(eventService.getEvents()).thenReturn(listOf(Event(id = "1181830")))

        service.list()

        verify(eventService).getEvents()
        verify(eventService, never()).findEventBy(anyString())
    }

    @Test
    fun `should leave the description empty when the talk is not in the schedule`() {
        `when`(eventService.getEvents()).thenReturn(emptyList())

        assertThat(service.list().single().description).isNull()
    }

    private fun video() = VideoInfo(
        path = talkDir.resolve("1080p.mp4"),
        thumbnail = null,
        eventId = "1181830",
        status = VideoInfo.Status.DONE,
    )
}
