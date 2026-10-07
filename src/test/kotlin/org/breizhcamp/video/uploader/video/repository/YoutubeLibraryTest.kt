package org.breizhcamp.video.uploader.video.repository

import assertk.assertThat
import assertk.assertions.contains
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
import com.google.api.services.youtube.model.VideoSnippet
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.breizhcamp.video.uploader.event.domain.Event
import org.breizhcamp.video.uploader.shared.config.YoutubeAuthConfig.Companion.YT_USER_ID
import org.breizhcamp.video.uploader.video.domain.VideoInfo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlin.text.Charsets.UTF_8

private const val MAX_TITLE_LENGTH = 100
private const val MAX_DESCRIPTION_LENGTH = 5000
private const val GZIP_MAGIC_FIRST: Byte = 0x1f
private const val GZIP_MAGIC_SECOND: Byte = 0x8b.toByte()

class YoutubeLibraryTest {

    private val transport = NetHttpTransport()
    private val jsonFactory = JacksonFactory.getDefaultInstance()
    private val tokenServer = MockWebServer()
    private val apiServer = MockWebServer()

    @BeforeEach
    fun setUp() {
        tokenServer.start()
        apiServer.start()
    }

    @AfterEach
    fun tearDown() {
        tokenServer.shutdown()
        apiServer.shutdown()
    }

    @Test
    fun `should only replace the description, leaving the rest of the snippet alone`(@TempDir dir: Path) {
        givenVideoOnYoutube(title = "Kotlin en 2026 - Alice Simon", categoryId = "28", description = "outdated")

        libraryTalkingToApi(dir).updateDescription("vid-1", "Un résumé tout neuf")

        apiServer.takeRequest(1, TimeUnit.SECONDS)  //the videos.list lookup
        val sent = requireNotNull(apiServer.takeRequest(1, TimeUnit.SECONDS))
        val snippet = jsonFactory.fromString(bodyOf(sent), Video::class.java).snippet
        assertThat(sent.method).isEqualTo("PUT")
        assertThat(snippet.description).isEqualTo("Un résumé tout neuf")
        assertThat(snippet.title).isEqualTo("Kotlin en 2026 - Alice Simon")
        assertThat(snippet.categoryId).isEqualTo("28")
    }

    @Test
    fun `should replace the characters YouTube rejects in a description`(@TempDir dir: Path) {
        givenVideoOnYoutube()

        libraryTalkingToApi(dir).updateDescription("vid-1", "Kotlin <3 vous > tout")

        assertThat(descriptionSent()).isEqualTo("Kotlin 〈3 vous 〉 tout")
    }

    @Test
    fun `should cut a description longer than the YouTube limit`(@TempDir dir: Path) {
        givenVideoOnYoutube()

        libraryTalkingToApi(dir).updateDescription("vid-1", "a".repeat(MAX_DESCRIPTION_LENGTH + 500))

        assertThat(descriptionSent()).hasLength(MAX_DESCRIPTION_LENGTH)
    }

    @Test
    fun `should fail clearly when YouTube does not know the video`(@TempDir dir: Path) {
        apiServer.enqueue(jsonResponse("""{"items":[]}"""))

        val failure = assertThrows<IllegalStateException> {
            libraryTalkingToApi(dir).updateDescription("vid-1", "Un résumé")
        }

        assertThat(requireNotNull(failure.message)).contains("vid-1")
    }

    /** Answer the videos.list lookup, then the videos.update call */
    private fun givenVideoOnYoutube(
        title: String = "Un titre",
        categoryId: String = "22",
        description: String = "outdated",
    ) {
        apiServer.enqueue(
            jsonResponse(
                """{"items":[{"id":"vid-1","snippet":{"title":"$title","categoryId":"$categoryId","description":"$description"}}]}"""
            )
        )
        apiServer.enqueue(jsonResponse("""{"id":"vid-1"}"""))
    }

    private fun descriptionSent(): String {
        apiServer.takeRequest(1, TimeUnit.SECONDS)  //the videos.list lookup
        val sent = requireNotNull(apiServer.takeRequest(1, TimeUnit.SECONDS))
        return jsonFactory.fromString(bodyOf(sent), Video::class.java).snippet.description
    }

    /** google-http-client gzips a request body past a few hundred bytes */
    private fun bodyOf(request: RecordedRequest): String {
        val bytes = request.body.readByteArray()
        val gzipped = bytes.size > 1 && bytes[0] == GZIP_MAGIC_FIRST && bytes[1] == GZIP_MAGIC_SECOND
        return if (gzipped) GZIPInputStream(bytes.inputStream()).readBytes().toString(UTF_8)
        else bytes.toString(UTF_8)
    }

    private fun jsonResponse(body: String) = MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(body)

    private fun libraryTalkingToApi(dir: Path): YoutubeLibrary {
        val flow = newFlow(dir)
        storeCredential(flow, refreshToken = "stored-refresh", expiresInSeconds = 3600)
        return YoutubeLibrary(transport, jsonFactory, flow, apiServer.url("/").toString())
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

    @Test
    fun `should send the talk description along with a new video`(@TempDir dir: Path) {
        val snippet = snippetOf(dir, name = "Kotlin en 2026", speakers = "Alice Simon", description = "Le résumé")

        assertThat(snippet.description).isEqualTo("Le résumé")
    }

    @Test
    fun `should send an empty description when the talk has none`(@TempDir dir: Path) {
        val snippet = snippetOf(dir, name = "Kotlin en 2026", speakers = "Alice Simon", description = null)

        assertThat(snippet.description).isEqualTo("")
    }

    @Test
    fun `should refuse to upload a video that is not normalized`(@TempDir dir: Path) {
        val original = Files.createFile(dir.resolve("1080p.mp4"))
        val videoInfo = VideoInfo(path = original, thumbnail = null, eventId = "1183944", status = VideoInfo.Status.NOT_STARTED)

        assertThrows<IllegalStateException> {
            YoutubeLibrary(transport, jsonFactory, newFlow(dir)).insertVideo(videoInfo, Event(id = "1183944"))
        }
    }

    /** Build the insert request the uploader would send, and read back the title it carries */
    private fun titleOf(dir: Path, name: String, speakers: String): String =
        snippetOf(dir, name, speakers).title

    private fun snippetOf(dir: Path, name: String, speakers: String, description: String? = null): VideoSnippet {
        val flow = newFlow(dir)
        storeCredential(flow, refreshToken = "stored-refresh", expiresInSeconds = 3600)
        val video = Files.createFile(dir.resolve("1080p.normalized.mp4"))
        val videoInfo = VideoInfo(
            path = video,
            thumbnail = null,
            eventId = "1183944",
            status = VideoInfo.Status.NOT_STARTED,
        )

        val insert = YoutubeLibrary(transport, jsonFactory, flow)
            .insertVideo(videoInfo, Event(id = "1183944", name = name, speakers = speakers, description = description))

        return (requireNotNull(insert).jsonContent as Video).snippet
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
