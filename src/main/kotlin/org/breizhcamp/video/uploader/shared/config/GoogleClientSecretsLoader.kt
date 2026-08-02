package org.breizhcamp.video.uploader.shared.config

import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.json.JsonFactory
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.io.File
import java.io.IOException
import java.io.Reader
import java.io.StringReader

const val OAUTH_GOOGLE_PROPERTY = "oauth-google"
const val OAUTH_GOOGLE_PATH_PROPERTY = "oauth-google-path"

private const val USAGE = """No Google OAuth client secrets provided. Create an OAuth client on https://console.cloud.google.com and pass it with one of:
  --oauth-google-path /path/to/oauth-google.json
  --oauth-google '{"installed":{"client_id":"xxx", ...}}'
  OAUTH_GOOGLE_PATH=/path/to/oauth-google.json
  OAUTH_GOOGLE='{"installed":{"client_id":"xxx", ...}}'"""

/**
 * Loads the Google OAuth client secrets from the command line or the environment.
 *
 * Inline JSON takes precedence over the file path. Properties are read through [Binder] so that
 * relaxed binding applies, which makes the `OAUTH_GOOGLE` and `OAUTH_GOOGLE_PATH` environment
 * variables work out of the box.
 */
@Component
class GoogleClientSecretsLoader(environment: Environment) {

    private val inlineJson = bindString(environment, OAUTH_GOOGLE_PROPERTY)
    private val path = bindString(environment, OAUTH_GOOGLE_PATH_PROPERTY)

    fun load(jsonFactory: JsonFactory): GoogleClientSecrets = when {
        !inlineJson.isNullOrBlank() -> parse(jsonFactory, StringReader(inlineJson), "--$OAUTH_GOOGLE_PROPERTY value")

        !path.isNullOrBlank() -> {
            val file = File(path)
            if (!file.isFile) error("Google OAuth client secrets file not found: ${file.absolutePath}")
            file.reader().use { parse(jsonFactory, it, file.absolutePath) }
        }

        else -> error(USAGE)
    }

    private fun parse(jsonFactory: JsonFactory, reader: Reader, source: String): GoogleClientSecrets = try {
        GoogleClientSecrets.load(jsonFactory, reader)
    } catch (e: IOException) {
        throw IllegalStateException("Invalid Google OAuth client secrets read from $source: ${e.message}", e)
    }

    private fun bindString(environment: Environment, property: String): String? =
        Binder.get(environment).bind(property, String::class.java).orElse(null)
}
