package org.breizhcamp.video.uploader.video.repository

import assertk.assertThat
import assertk.assertions.endsWith
import assertk.assertions.hasLength
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.google.api.client.auth.oauth2.TokenResponse
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.http.GenericUrl
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.youtube.YouTubeScopes
import com.google.api.services.youtube.model.Video
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.breizhcamp.video.uploader.event.domain.Event
import org.breizhcamp.video.uploader.shared.config.YoutubeAuthConfig.Companion.YT_USER_ID
import org.breizhcamp.video.uploader.video.domain.VideoInfo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

private const val MAX_TITLE_LENGTH = 100

class YoutubeLibraryTest {

    private val transport = NetHttpTransport()
    private val jsonFactory = JacksonFactory.getDefaultInstance()
    private val tokenServer = MockWebServer()

    @BeforeEach
    fun setUp() {
        tokenServer.start()
    }

    @AfterEach
    fun tearDown() {
        tokenServer.shutdown()
    }

    @Test
    fun `should refresh an expired access token and stay connected`(@TempDir dir: Path) {
        val flow = newFlow(dir)
        storeCredential(flow, refreshToken = "stored-refresh", expiresInSeconds = -60)
        tokenServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"access_token":"refreshed-access","expires_in":3600,"token_type":"Bearer"}""")
        )

        assertThat(YoutubeLibrary(transport, jsonFactory, flow).isConnected()).isTrue()

        assertThat(tokenServer.takeRequest(1, TimeUnit.SECONDS)).isNotNull()
        assertThat(requireNotNull(flow.loadCredential(YT_USER_ID)).accessToken).isEqualTo("refreshed-access")
    }

    @Test
    fun `should not be connected when the token expired without a refresh token`(@TempDir dir: Path) {
        val flow = newFlow(dir)
        storeCredential(flow, refreshToken = null, expiresInSeconds = -60)

        assertThat(YoutubeLibrary(transport, jsonFactory, flow).isConnected()).isFalse()

        assertThat(tokenServer.requestCount).isEqualTo(0)
    }

    @Test
    fun `should be connected without refreshing while the access token is still valid`(@TempDir dir: Path) {
        val flow = newFlow(dir)
        storeCredential(flow, refreshToken = "stored-refresh", expiresInSeconds = 3600)

        assertThat(YoutubeLibrary(transport, jsonFactory, flow).isConnected()).isTrue()

        assertThat(tokenServer.requestCount).isEqualTo(0)
    }

    @Test
    fun `should not be connected when nothing is stored`(@TempDir dir: Path) {
        assertThat(YoutubeLibrary(transport, jsonFactory, newFlow(dir)).isConnected()).isFalse()
    }

    @Test
    fun `should tell whether a credential is stored`(@TempDir dir: Path) {
        val flow = newFlow(dir)
        val library = YoutubeLibrary(transport, jsonFactory, flow)

        assertThat(library.hasStoredCredential()).isFalse()

        storeCredential(flow, refreshToken = "stored-refresh", expiresInSeconds = 3600)

        assertThat(library.hasStoredCredential()).isTrue()
    }

    @Test
    fun `should forget the stored credential`(@TempDir dir: Path) {
        val flow = newFlow(dir)
        storeCredential(flow, refreshToken = "stored-refresh", expiresInSeconds = 3600)
        val library = YoutubeLibrary(transport, jsonFactory, flow)

        library.clearCredential()

        assertThat(library.hasStoredCredential()).isFalse()
        assertThat(library.isConnected()).isFalse()
        assertThat(flow.loadCredential(YT_USER_ID)).isNull()
    }

    @Test
    fun `should forget nothing when no credential is stored`(@TempDir dir: Path) {
        val library = YoutubeLibrary(transport, jsonFactory, newFlow(dir))

        library.clearCredential()

        assertThat(library.hasStoredCredential()).isFalse()
    }

    @Test
    fun `should keep a truncated title within the YouTube limit`(@TempDir dir: Path) {
        val title = titleOf(
            dir,
            name = "Au-delà de la hype : gérer des embeddings à l'échelle du milliard dans Elasticsearch et OpenSearch",
            speakers = "Pietro Mele, Lucian Precup",
        )

        assertThat(title).hasLength(MAX_TITLE_LENGTH)
        assertThat(title).endsWith("… - Pietro Mele, Lucian Precup")
    }

    @Test
    fun `should leave a short enough title untouched`(@TempDir dir: Path) {
        val title = titleOf(dir, name = "Kotlin en 2026", speakers = "Alice Simon")

        assertThat(title).isEqualTo("Kotlin en 2026 - Alice Simon")
    }

    @Test
    fun `should still fit when the speakers eat the whole budget`(@TempDir dir: Path) {
        val title = titleOf(dir, name = "Un titre de talk tout à fait raisonnable", speakers = "S".repeat(120))

        assertThat(title.length).isLessThanOrEqualTo(MAX_TITLE_LENGTH)
    }

    @Test
    fun `should replace the angle brackets rejected by YouTube`(@TempDir dir: Path) {
        val title = titleOf(dir, name = "Kotlin <3 vous", speakers = "Alice Simon")

        assertThat(title).isEqualTo("Kotlin 〈3 vous - Alice Simon")
    }

    /** Build the insert request the uploader would send, and read back the title it carries */
    private fun titleOf(dir: Path, name: String, speakers: String): String {
        val flow = newFlow(dir)
        storeCredential(flow, refreshToken = "stored-refresh", expiresInSeconds = 3600)
        val video = Files.createFile(dir.resolve("1080p.mp4"))
        val videoInfo = VideoInfo(
            path = video,
            thumbnail = null,
            eventId = "1183944",
            status = VideoInfo.Status.NOT_STARTED,
        )

        val insert = YoutubeLibrary(transport, jsonFactory, flow)
            .insertVideo(videoInfo, Event(id = "1183944", name = name, speakers = speakers))

        return (requireNotNull(insert).jsonContent as Video).snippet.title
    }

    private fun newFlow(dir: Path) = GoogleAuthorizationCodeFlow.Builder(
        transport,
        jsonFactory,
        GoogleClientSecrets().setInstalled(
            GoogleClientSecrets.Details()
                .setClientId("client-id")
                .setClientSecret("client-secret")
        ),
        listOf(YouTubeScopes.YOUTUBE_UPLOAD, YouTubeScopes.YOUTUBE),
    )
        .setDataStoreFactory(FileDataStoreFactory(dir.toFile()))
        .setTokenServerUrl(GenericUrl(tokenServer.url("/token").toString()))
        .build()

    private fun storeCredential(
        flow: GoogleAuthorizationCodeFlow,
        refreshToken: String?,
        expiresInSeconds: Long,
    ) {
        flow.createAndStoreCredential(
            TokenResponse().apply {
                accessToken = "stored-access"
                this.refreshToken = refreshToken
                this.expiresInSeconds = expiresInSeconds
            },
            YT_USER_ID,
        )
    }
}
