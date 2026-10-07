package org.breizhcamp.video.uploader.file.service

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.zip.ZipFile

class YoutubeMetadataResetTest {

    @TempDir
    lateinit var recordingDir: Path
    private val mapper = jacksonObjectMapper()
    private lateinit var backup: MetadataBackupService
    private lateinit var reset: YoutubeMetadataReset

    @BeforeEach
    fun setUp() {
        val fileService = mock(FileService::class.java)
        `when`(fileService.recordingDir).thenReturn(recordingDir)
        val clock = Clock.fixed(Instant.parse("2026-10-07T19:45:12Z"), ZoneId.of("Europe/Paris"))
        backup = MetadataBackupService(fileService, clock)
        reset = YoutubeMetadataReset(backup, mapper)
    }

    @Test
    fun `should forget the upload of every talk and keep its loudness`() {
        write(
            "talk/metadata.json",
            """{"status":"DONE","progression":100,"youtubeId":"abc123","descriptionStatus":"DONE",""" +
                """"thumbnailStatus":"FAILED","loudness":{"integrated":-23.0,"truePeak":-16.8}}""",
        )

        reset.reset()

        assertThat(read("talk/metadata.json")).isEqualTo(
            mapOf("status" to "NOT_STARTED", "loudness" to mapOf("integrated" to -23.0, "truePeak" to -16.8))
        )
    }

    @Test
    fun `should remove the video url of every event of the exported schedule`() {
        write(
            "schedule.json",
            """[{"id":"1","name":"Un talk","video_url":"https://www.youtube.com/watch?v=abc123"},{"id":"2","name":"Un autre"}]""",
        )

        reset.reset()

        assertThat(read("schedule.json")).isEqualTo(
            listOf(mapOf("id" to "1", "name" to "Un talk"), mapOf("id" to "2", "name" to "Un autre"))
        )
    }

    @Test
    fun `should leave a file without any YouTube information untouched`() {
        write("playlist.json", """{ "id" : "PL-1" }""")

        val result = reset.reset()

        assertThat(Files.readString(recordingDir.resolve("playlist.json"))).isEqualTo("""{ "id" : "PL-1" }""")
        assertThat(result.cleaned).isEqualTo(0)
    }

    @Test
    fun `should save everything before cleaning`() {
        write("talk/metadata.json", """{"status":"DONE","youtubeId":"abc123"}""")

        val result = reset.reset()

        val zip = requireNotNull(result.backup).zip
        val saved = ZipFile(zip.toFile()).use { it.getInputStream(it.getEntry("talk/metadata.json")).readBytes().decodeToString() }
        assertThat(saved).isEqualTo("""{"status":"DONE","youtubeId":"abc123"}""")
        assertThat(result.cleaned).isEqualTo(1)
    }

    @Test
    fun `should clean nothing when the backup fails`() {
        write("talk/metadata.json", """{"status":"DONE","youtubeId":"abc123"}""")
        val failing = mock(MetadataBackupService::class.java)
        `when`(failing.backup()).thenThrow(UncheckedIOException(IOException("No space left on device")))

        assertThrows<UncheckedIOException> { YoutubeMetadataReset(failing, mapper).reset() }

        assertThat(Files.readString(recordingDir.resolve("talk/metadata.json")))
            .isEqualTo("""{"status":"DONE","youtubeId":"abc123"}""")
    }

    @Test
    fun `should go on past an unreadable file and report it`() {
        write("broken/metadata.json", "{ not json")
        write("talk/metadata.json", """{"status":"DONE","youtubeId":"abc123"}""")

        val result = reset.reset()

        assertThat(result.failed).containsExactly(recordingDir.resolve("broken/metadata.json"))
        assertThat(result.cleaned).isEqualTo(1)
        assertThat((read("talk/metadata.json") as Map<*, *>)["youtubeId"]).isNull()
    }

    @Test
    fun `should do nothing when there is no json file`() {
        val result = reset.reset()

        assertThat(result.backup).isNull()
        assertThat(result.cleaned).isEqualTo(0)
    }

    private fun write(relative: String, content: String) {
        val file = recordingDir.resolve(relative)
        Files.createDirectories(file.parent)
        Files.writeString(file, content)
    }

    private fun read(relative: String): Any = mapper.readValue(recordingDir.resolve(relative).toFile(), Any::class.java)
}
