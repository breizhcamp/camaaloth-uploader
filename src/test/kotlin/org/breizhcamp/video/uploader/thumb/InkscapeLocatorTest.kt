package org.breizhcamp.video.uploader.thumb

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.mock.env.MockEnvironment
import java.nio.file.Path

class InkscapeLocatorTest {

    @Test
    fun `should prefer the configured path over the candidates`(@TempDir dir: Path) {
        val candidate = executable(dir, "candidate")

        val path = resolveInkscapePath("/custom/inkscape", listOf(candidate))

        assertThat(path).isEqualTo("/custom/inkscape")
    }

    @Test
    fun `should keep the configured path even when it does not exist`() {
        val path = resolveInkscapePath("/nope/inkscape", emptyList())

        assertThat(path).isEqualTo("/nope/inkscape")
    }

    @Test
    fun `should ignore a blank configured path`(@TempDir dir: Path) {
        val candidate = executable(dir, "candidate")

        val path = resolveInkscapePath("  ", listOf(candidate))

        assertThat(path).isEqualTo(candidate)
    }

    @Test
    fun `should pick the first executable candidate`(@TempDir dir: Path) {
        val missing = dir.resolve("absent").toString()
        val notExecutable = dir.resolve("plain").toFile().apply { writeText("") }
        val candidate = executable(dir, "inkscape")

        val path = resolveInkscapePath(null, listOf(missing, notExecutable.absolutePath, candidate))

        assertThat(path).isEqualTo(candidate)
    }

    @Test
    fun `should fall back to the PATH when no candidate is executable`(@TempDir dir: Path) {
        val path = resolveInkscapePath(null, listOf(dir.resolve("absent").toString()))

        assertThat(path).isEqualTo("inkscape")
    }

    @Test
    fun `should read the configured path from the environment`() {
        val env = MockEnvironment().withProperty(INKSCAPE_PATH_PROPERTY, "/from/env/inkscape")

        assertThat(InkscapeLocator(env).path).isEqualTo("/from/env/inkscape")
    }

    private fun executable(dir: Path, name: String): String =
        dir.resolve(name).toFile().apply { writeText(""); setExecutable(true) }.absolutePath
}
