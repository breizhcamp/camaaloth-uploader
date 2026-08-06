package org.breizhcamp.video.uploader.shared.config

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.breizhcamp.video.uploader.CamaalothUploaderProps
import org.breizhcamp.video.uploader.thumb.InkscapeLocator
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.mock.env.MockEnvironment
import java.nio.file.Files
import java.nio.file.Path

class PathsReportTest {

    private val inkscape = mock(InkscapeLocator::class.java)

    @BeforeEach
    fun setUp() {
        `when`(inkscape.path).thenReturn("/usr/bin/inkscape")
    }

    @Test
    fun `should point at the configured directories`(@TempDir dir: Path) {
        val report = report(dir, MockEnvironment())

        assertThat(entry(report, "Vidéos").path).isEqualTo(dir.resolve("videos").toAbsolutePath().toString())
        assertThat(entry(report, "Assets").path).isEqualTo(dir.resolve("assets").toAbsolutePath().toString())
    }

    @Test
    fun `should tell an existing file from a missing one`(@TempDir dir: Path) {
        Files.createDirectories(dir.resolve("assets"))
        Files.writeString(dir.resolve("assets/schedule.json"), "[]")

        val report = report(dir, MockEnvironment())

        assertThat(entry(report, "schedule.json").exists).isEqualTo(true)
        assertThat(entry(report, "thumb.svg").exists).isEqualTo(false)
    }

    @Test
    fun `should count the events of the schedule`(@TempDir dir: Path) {
        Files.createDirectories(dir.resolve("assets"))
        Files.writeString(dir.resolve("assets/schedule.json"), """[{"id":"a"},{"id":"b"},{"id":"c"}]""")

        assertThat(entry(report(dir, MockEnvironment()), "schedule.json").note).isEqualTo("3 events")
    }

    @Test
    fun `should say nothing about a schedule it cannot read`(@TempDir dir: Path) {
        Files.createDirectories(dir.resolve("assets"))
        Files.writeString(dir.resolve("assets/schedule.json"), "{ pas du json")

        assertThat(entry(report(dir, MockEnvironment()), "schedule.json").note).isNull()
    }

    @Test
    fun `should name the source of the oauth client without printing it`(@TempDir dir: Path) {
        val env = MockEnvironment().withProperty(OAUTH_GOOGLE_PROPERTY, """{"installed":{"client_secret":"s3cr3t"}}""")

        val oauth = entry(report(dir, env), "Client OAuth")

        assertThat(oauth.path).contains(OAUTH_GOOGLE_PROPERTY)
        assertThat(oauth.path).doesNotContain("s3cr3t")
    }

    @Test
    fun `should show the path of the oauth client file`(@TempDir dir: Path) {
        val file = Files.writeString(dir.resolve("oauth.json"), "{}")
        val env = MockEnvironment().withProperty(OAUTH_GOOGLE_PATH_PROPERTY, file.toString())

        val oauth = entry(report(dir, env), "Client OAuth")

        assertThat(oauth.path).isEqualTo(file.toString())
        assertThat(oauth.exists).isEqualTo(true)
    }

    @Test
    fun `should report the inkscape binary it would run`(@TempDir dir: Path) {
        `when`(inkscape.path).thenReturn("/Applications/Inkscape.app/Contents/MacOS/inkscape")

        assertThat(entry(report(dir, MockEnvironment()), "Inkscape").path)
            .isEqualTo("/Applications/Inkscape.app/Contents/MacOS/inkscape")
    }

    private fun report(dir: Path, env: MockEnvironment) = PathsReport(
        CamaalothUploaderProps(
            recordingDir = dir.resolve("videos").toString(),
            assetsDir = dir.resolve("assets").toString(),
        ),
        env,
        inkscape,
    ).entries()

    private fun entry(entries: List<PathEntry>, label: String) =
        requireNotNull(entries.firstOrNull { it.label == label }) { "no entry labelled [$label]" }
}
