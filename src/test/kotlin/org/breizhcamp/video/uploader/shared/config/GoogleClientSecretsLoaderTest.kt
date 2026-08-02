package org.breizhcamp.video.uploader.shared.config

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import com.google.api.client.json.jackson2.JacksonFactory
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.springframework.mock.env.MockEnvironment
import java.io.File
import java.nio.file.Path

class GoogleClientSecretsLoaderTest {

    private val jsonFactory = JacksonFactory.getDefaultInstance()

    @Test
    fun `should load secrets from inline json`() {
        val env = MockEnvironment().withProperty(OAUTH_GOOGLE_PROPERTY, secretsJson("inline-id"))

        val secrets = GoogleClientSecretsLoader(env).load(jsonFactory)

        assertThat(secrets.details.clientId).isEqualTo("inline-id")
    }

    @Test
    fun `should load secrets from a file path`(@TempDir dir: Path) {
        val file = writeSecrets(dir, "file-id")
        val env = MockEnvironment().withProperty(OAUTH_GOOGLE_PATH_PROPERTY, file.absolutePath)

        val secrets = GoogleClientSecretsLoader(env).load(jsonFactory)

        assertThat(secrets.details.clientId).isEqualTo("file-id")
    }

    @Test
    fun `should prefer inline json over the file path`(@TempDir dir: Path) {
        val file = writeSecrets(dir, "file-id")
        val env = MockEnvironment()
            .withProperty(OAUTH_GOOGLE_PROPERTY, secretsJson("inline-id"))
            .withProperty(OAUTH_GOOGLE_PATH_PROPERTY, file.absolutePath)

        val secrets = GoogleClientSecretsLoader(env).load(jsonFactory)

        assertThat(secrets.details.clientId).isEqualTo("inline-id")
    }

    @Test
    fun `should fail with the missing path in the message`(@TempDir dir: Path) {
        val missing = dir.resolve("absent.json").toFile()
        val env = MockEnvironment().withProperty(OAUTH_GOOGLE_PATH_PROPERTY, missing.absolutePath)

        val error = assertThrows<IllegalStateException> { GoogleClientSecretsLoader(env).load(jsonFactory) }

        assertThat(error.message!!).contains(missing.absolutePath)
    }

    @Test
    fun `should fail with a readable message on invalid json`() {
        val env = MockEnvironment().withProperty(OAUTH_GOOGLE_PROPERTY, "not json")

        val error = assertThrows<IllegalStateException> { GoogleClientSecretsLoader(env).load(jsonFactory) }

        assertThat(error.message!!).contains("Invalid Google OAuth client secrets")
    }

    @Test
    fun `should list every supported source when nothing is configured`() {
        val error = assertThrows<IllegalStateException> {
            GoogleClientSecretsLoader(MockEnvironment()).load(jsonFactory)
        }

        assertThat(error.message!!).contains("--oauth-google-path")
        assertThat(error.message!!).contains("OAUTH_GOOGLE_PATH")
    }

    private fun secretsJson(clientId: String) =
        """{"installed":{"client_id":"$clientId","client_secret":"secret"}}"""

    private fun writeSecrets(dir: Path, clientId: String): File =
        dir.resolve("oauth-google.json").toFile().apply { writeText(secretsJson(clientId)) }
}
