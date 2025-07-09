package org.breizhcamp.video.uploader.web

import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.extracting
import assertk.assertions.isNotNull
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.api.services.youtube.model.*
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import okhttp3.mockwebserver.MockWebServer
import org.apache.commons.io.FileUtils
import org.breizhcamp.video.uploader.CamaalothUploaderProps
import org.breizhcamp.video.uploader.event.domain.Event
import org.breizhcamp.video.uploader.enqueueObject
import org.breizhcamp.video.uploader.launchAndNavigateToHomePage
import org.breizhcamp.video.uploader.verifyRequest
import org.junit.Ignore
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpMethod
import org.springframework.test.context.ActiveProfiles
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.regex.Pattern

//@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class E2ETest {

    @LocalServerPort
    lateinit var port: String

    @Autowired
    lateinit var props: CamaalothUploaderProps

    private val mapper = jacksonMapperBuilder().addModule(JavaTimeModule()).build()

    private val ytServer = MockWebServer()

    @BeforeAll
    fun setUp() {
        ytServer.start(20000)
    }

    @AfterAll
    fun tearDown() {
        ytServer.shutdown()
    }

    //@Test
    fun `should authenticate youtube, load channels+playlists and show 1 video to upload`() {

        val channels = mutableListOf(Channel().apply {
            id = UUID.randomUUID().toString()
            snippet = ChannelSnippet().apply {
                country = "France"
                description =
                    "Toutes les vidéos du BreizhCamp, LA conférence informatique à Rennes qu'il ne faut pas rater."
                title = "BreizhCamp"
                thumbnails = ThumbnailDetails().apply {
                    default = Thumbnail().apply {
                        url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
                    }
                }
            }
        })

        val playlistItems = mutableListOf(Playlist().apply {
            snippet = PlaylistSnippet().apply {
                title = "test playlist snippet"
                id = "1"
                description = "test playlist description"
            }
        })

        ytServer.enqueueObject(ChannelListResponse().apply { items = channels })
        ytServer.enqueueObject(PlaylistListResponse().apply { items = playlistItems })

        clearVideosDir()

        Playwright.create().use {
            with(it.launchAndNavigateToHomePage(port)) {

                this.locator("#createDir").click()
                createVideoToUpload()

                locator("#yt-auth").click()

                ytServer.verifyRequest(
                    requestedPath = "/youtube/v3/channels?mine=true&part=id,snippet",
                    requestedMethod = HttpMethod.GET,
                )

                assertThat(locator("#yt-auth")).isVisible()
                assertThat(locator(".btn-upload-video")).isVisible()
            }
        }
    }

    //@Test
    fun `should create sessions directory`() {

        val videosDir = Paths.get(props.recordingDir).toAbsolutePath()
        val scheduleFile: File = Paths.get(props.assetsDir, "schedule.json").toFile()
        clearVideosDir()
        Playwright.create().use {
            with(it.launchAndNavigateToHomePage(port)) {
                this.locator("#createDir").click()
                assertThat(locator("#createDir")).not().isVisible()
                assertThat(locator("#reCreateDir")).isVisible()
                assert(checkAllFolderHaveBeenCreated(videosDir, scheduleFile))
            }
        }
    }

    //@Test
    fun `should fix ids on schedule`() {

        val scheduleFile: File = Paths.get(props.assetsDir, "schedule.json").toFile()
        val savedScheduleFile: File = Paths.get(props.assetsDir, "schedule_save.json").toFile()

        saveAndInitScheduleFileForTest(scheduleFile, savedScheduleFile)

        Playwright.create().use {
            with(it.launchAndNavigateToHomePage(port)) {
                locator("#fixMissingIds").click()

                assertThat(locator("#fixMissingIds")).isVisible()
                assertThat(
                    mapper.readValue<List<Event>>(scheduleFile)
                        .first { it.name == "Test" }.id
                ).isNotNull()
                assertThat(mapper.readValue<List<Event>>(scheduleFile))
                    .extracting { it.id }
                    .doesNotContain(null)

                resetScheduleFile(savedScheduleFile, scheduleFile)
            }
        }
    }


    private fun clearVideosDir() {
        val videosDir = Paths.get(props.recordingDir).toAbsolutePath()
        FileUtils.deleteDirectory(videosDir.toFile())
    }

    private fun createVideoToUpload() {
        Paths.get(props.recordingDir).toAbsolutePath().toFile().listFiles()?.first {
            File(it.absolutePath + File.separator + "1.mp4").createNewFile()
        }
    }

    private fun checkAllFolderHaveBeenCreated(videosDir: Path, scheduleFile: File): Boolean {
        val events = mapper.readValue<List<Event>>(scheduleFile)
        return Files.exists(videosDir.toAbsolutePath()) && events.all { currentEvent ->
            return videosDir.toFile().listFiles()?.any {
                Pattern.matches(".+" + currentEvent.name + ".+", Pattern.quote(it.absolutePath))
            } ?: false
        }
    }

    private fun saveAndInitScheduleFileForTest(scheduleFile: File, savedScheduleFile: File) {
        FileUtils.copyFile(scheduleFile, savedScheduleFile)
        val events = mapper.readValue<List<Event>>(scheduleFile).toMutableList()
        events.add(Event( name = "Test" ))
        mapper.writeValue(scheduleFile, events)
    }

    private fun resetScheduleFile(savedScheduleFile: File, scheduleFile: File) {
        FileUtils.copyFile(savedScheduleFile, scheduleFile)
        FileUtils.forceDelete(savedScheduleFile)
    }
}
