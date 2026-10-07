package org.breizhcamp.video.uploader.file.service

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.zip.ZipFile

class MetadataBackupServiceTest {

    @TempDir
    lateinit var recordingDir: Path
    private lateinit var service: MetadataBackupService

    @BeforeEach
    fun setUp() {
        val fileService = mock(FileService::class.java)
        `when`(fileService.recordingDir).thenReturn(recordingDir)
        val clock = Clock.fixed(Instant.parse("2026-10-07T19:45:12Z"), ZoneId.of("Europe/Paris"))
        service = MetadataBackupService(fileService, clock)
    }

    @Test
    fun `should zip every json file of the recording dir, at any depth`() {
        write("playlist.json", """{"id":"PL-1"}""")
        write("24.Amphi A.10-00 - Un talk - 1181830/metadata.json", """{"status":"DONE"}""")
        write("24.Amphi B.13-30 - Un autre - 1171038/metadata.json", """{"status":"NOT_STARTED"}""")

        val backup = requireNotNull(service.backup())

        assertThat(backup.files).isEqualTo(3)
        assertThat(entriesOf(backup.zip).keys).containsExactlyInAnyOrder(
            "playlist.json",
            "24.Amphi A.10-00 - Un talk - 1181830/metadata.json",
            "24.Amphi B.13-30 - Un autre - 1171038/metadata.json",
        )
        assertThat(entriesOf(backup.zip)["24.Amphi A.10-00 - Un talk - 1181830/metadata.json"])
            .isEqualTo("""{"status":"DONE"}""")
    }

    @Test
    fun `should name the backup after the local time and put it in the recording dir`() {
        write("playlist.json", "{}")

        val backup = requireNotNull(service.backup())

        assertThat(backup.zip).isEqualTo(recordingDir.resolve("metadata-backup-20261007-214512.zip"))
    }

    @Test
    fun `should leave out the videos, the thumbnails and the hidden files`() {
        write("talk/metadata.json", "{}")
        write("talk/1080p.mp4", "video")
        write("talk/thumb.png", "png")
        write("talk/._metadata.json", "resource fork")
        write(".datastore/token.json", "secret")

        val backup = requireNotNull(service.backup())

        assertThat(entriesOf(backup.zip).keys).containsExactlyInAnyOrder("talk/metadata.json")
    }

    @Test
    fun `should not write an empty backup`() {
        write("talk/1080p.mp4", "video")

        assertThat(service.backup()).isNull()
        assertThat(Files.list(recordingDir).use { it.anyMatch { f -> f.toString().endsWith(".zip") } }).isFalse()
    }

    private fun write(relative: String, content: String) {
        val file = recordingDir.resolve(relative)
        Files.createDirectories(file.parent)
        Files.writeString(file, content)
    }

    private fun entriesOf(zip: Path): Map<String, String> = ZipFile(zip.toFile()).use { file ->
        file.entries().asSequence().associate { it.name to file.getInputStream(it).readBytes().decodeToString() }
    }
}
