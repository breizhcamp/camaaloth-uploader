package org.breizhcamp.video.uploader.shared.config

import assertk.assertThat
import assertk.assertions.containsExactly
import org.junit.jupiter.api.Test

class CliArgsTest {

    @Test
    fun `should merge spaced option with its value`() {
        val args = normalizeSpacedOptions(arrayOf("--oauth-google-path", "/tmp/oauth-google.json"))

        assertThat(args.toList()).containsExactly("--oauth-google-path=/tmp/oauth-google.json")
    }

    @Test
    fun `should merge inline json passed with a space`() {
        val json = """{"installed":{"client_id":"xxx"}}"""

        val args = normalizeSpacedOptions(arrayOf("--oauth-google", json))

        assertThat(args.toList()).containsExactly("--oauth-google=$json")
    }

    @Test
    fun `should leave equals form untouched`() {
        val args = normalizeSpacedOptions(arrayOf("--oauth-google-path=/tmp/oauth-google.json"))

        assertThat(args.toList()).containsExactly("--oauth-google-path=/tmp/oauth-google.json")
    }

    @Test
    fun `should leave option without value untouched`() {
        val args = normalizeSpacedOptions(arrayOf("--oauth-google-path"))

        assertThat(args.toList()).containsExactly("--oauth-google-path")
    }

    @Test
    fun `should not consume the next option as a value`() {
        val args = normalizeSpacedOptions(arrayOf("--oauth-google", "--camaaloth-uploader.recordingDir=vid"))

        assertThat(args.toList()).containsExactly("--oauth-google", "--camaaloth-uploader.recordingDir=vid")
    }

    @Test
    fun `should keep unrelated arguments in place`() {
        val args = normalizeSpacedOptions(
            arrayOf("--camaaloth-uploader.recordingDir=vid", "--oauth-google-path", "/tmp/o.json", "--debug")
        )

        assertThat(args.toList()).containsExactly(
            "--camaaloth-uploader.recordingDir=vid", "--oauth-google-path=/tmp/o.json", "--debug"
        )
    }
}
