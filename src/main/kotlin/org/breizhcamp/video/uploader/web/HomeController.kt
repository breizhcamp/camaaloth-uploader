package org.breizhcamp.video.uploader.web

import io.github.oshai.kotlinlogging.KotlinLogging
import org.breizhcamp.video.uploader.event.service.EventService
import org.breizhcamp.video.uploader.file.service.FileService
import org.breizhcamp.video.uploader.shared.session.YoutubeSession
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
) {

    private val logger = KotlinLogging.logger {}

    @GetMapping
    fun home(model: Model): String {
        val videosDir = fileService.recordingDir

        model["videosDir"] = videosDir
        model["dirExists"] = Files.isDirectory(videosDir)
        model["connected"] = youtubeService.isConnected()
        model["ytSession"] = youtubeSession

        return "index"
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