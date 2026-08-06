package org.breizhcamp.video.uploader.web

import io.github.oshai.kotlinlogging.KotlinLogging
import org.breizhcamp.video.uploader.event.service.EventService
import org.breizhcamp.video.uploader.file.service.FileService
import org.breizhcamp.video.uploader.shared.session.YoutubeSession
import org.breizhcamp.video.uploader.shared.batch.BatchProgressTracker
import org.breizhcamp.video.uploader.video.service.VideoService
import org.breizhcamp.video.uploader.video.service.YoutubeService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.ui.set
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import java.nio.file.Files

@Controller
class HomeController(
    private val eventService: EventService,
    private val fileService: FileService,
    private val videoService: VideoService,
    private val youtubeService: YoutubeService,
    private val youtubeSession: YoutubeSession,
    private val batchProgress: BatchProgressTracker,
) {

    private val logger = KotlinLogging.logger {}

    @GetMapping
    fun home(model: Model): String {
        val videosDir = fileService.recordingDir

        youtubeService.loadSessionIfNeeded()
        val connected = youtubeService.isConnected()

        model["videosDir"] = videosDir
        model["dirExists"] = Files.isDirectory(videosDir)
        model["connected"] = connected
        //a credential is stored but unusable: only deleting it can unblock the authentication
        model["staleCredential"] = !connected && youtubeService.hasStoredCredential()
        model["ytProblem"] = ytProblem(connected)
        //so a page loaded mid batch shows the bars right away, without waiting for the next event
        model["batches"] = batchProgress.running()
        model["ytSession"] = youtubeSession

        return "index"
    }

    /**
     * Why the YouTube panel cannot be used even though we are connected, so the page can explain it
     * and offer a way out instead of showing an empty card.
     */
    private fun ytProblem(connected: Boolean): String? = when {
        !connected -> null
        youtubeSession.channels.isNullOrEmpty() -> "NO_CHANNEL"
        youtubeSession.currentChannel == null -> "NO_CHANNEL_SELECTED"
        youtubeSession.playlists.isNullOrEmpty() -> "NO_PLAYLIST"
        else -> null
    }

    @PostMapping("/createDir")
    fun createDir(): String {
        logger.info { "Creating dir" }
        fileService.createDirs()
        return "redirect:./"
    }

    @PostMapping("/generateSchedule")
    fun generateSchedule(): String {
        logger.info { "Generate schedule" }
        videoService.generateUpdatedSchedule()
        return "redirect:/"
    }

    @PostMapping("/fixMissingIdsInSchedule")
    fun fixMissingIdsInSchedule(): String {
        logger.info { "Fix missing ids in schedule" }
        eventService.generateMissingIdsAndWrite()
        return "redirect:./"
    }

}