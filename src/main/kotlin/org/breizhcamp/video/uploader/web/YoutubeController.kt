package org.breizhcamp.video.uploader.web

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import org.breizhcamp.video.uploader.file.service.FileService
import org.breizhcamp.video.uploader.shared.PathUtils
import org.breizhcamp.video.uploader.shared.session.YoutubeSession
import org.breizhcamp.video.uploader.video.domain.VideoInfo
import org.breizhcamp.video.uploader.video.service.VideoService
import org.breizhcamp.video.uploader.video.service.YoutubeService
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.simp.annotation.SubscribeMapping
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

/**
 * Controller to handle Google authentication
 */
@Controller
@RequestMapping("/yt")
class YoutubeController(
    private val youtubeService: YoutubeService,
    private val fileService: FileService,
    private val videoService: VideoService,
    private val ytSession: YoutubeSession
) {
    private val logger = KotlinLogging.logger { }
    private var redirectUrl: String? = null

    /**
     * @param baseUrl url of the page the browser is on, used to build the OAuth redirect uri.
     * Optional: when the link did not carry it, fall back on the current request so the
     * authentication can never break on a missing parameter.
     */
    @GetMapping("/auth")
    fun auth(@RequestParam(required = false) baseUrl: String?, request: HttpServletRequest): String {
        redirectUrl = returnUrl(baseUrl, request)
        return "redirect:" + youtubeService.getAuthUrl(requireNotNull(redirectUrl))
            .also { logger.info { "Redirecting to $it" } }
    }

    private fun returnUrl(baseUrl: String?, request: HttpServletRequest): String {
        val base = baseUrl?.takeIf { it.isNotBlank() }
            ?: ServletUriComponentsBuilder.fromContextPath(request).build().toUriString()
        return base.substringBefore('?').substringBefore('#').removeSuffix("/") + "/yt/return"
    }

    @GetMapping("/return")
    fun returnAuth(@RequestParam code: String): String {
        youtubeService.handleAuth(code, requireNotNull(redirectUrl))
        reloadYtSession()
        return "redirect:/"
    }

    @PostMapping("/disconnect")
    fun disconnect(): String {
        youtubeService.disconnect()
        ytSession.clear()
        return "redirect:/"
    }

    /** Drop the stored credential and send the user back to Google in a single click */
    @PostMapping("/reconnect")
    fun reconnect(@RequestParam(required = false) baseUrl: String?, request: HttpServletRequest): String {
        youtubeService.disconnect()
        ytSession.clear()
        return auth(baseUrl, request)
    }

    @GetMapping("/reload")
    fun reloadYtSession(): String {
        youtubeService.loadSession()
        return "redirect:/"
    }

    @PostMapping("/curPlaylist")
    fun changeCurPlaylist(@RequestParam playlist: String): String {
        if ("none" == playlist) {
            ytSession.curPlaylist = null
        } else if (ytSession.playlists != null) {
            ytSession.playlists
                ?.firstOrNull { it.id == playlist }
                ?.run {
                    logger.info { "Changing current playlist $playlist" }
                    ytSession.curPlaylist = this
                }
        }
        return "redirect:/"
    }

    @PostMapping("uploadAll")
    fun uploadAll(): String {
        videoService.list()
            .filter { it.status === VideoInfo.Status.NOT_STARTED }
            .forEach { videoInfo ->
                ytSession.curPlaylist?.let { videoInfo.playlistId = it.id }
                youtubeService.upload(videoInfo)
            }
        return "redirect:/"
    }

    @SubscribeMapping(VIDEOS_TOPIC)
    fun subscribe(): Collection<VideoInfo> {
        val videosById = videoService.list()
            .filter { it.eventId != null }
            .associateBy { it.eventId }

        youtubeService.listWaiting()
            .map { it.eventId }
            .forEach { id -> videosById[id]?.status = VideoInfo.Status.WAITING }

        return videosById.values
            .sortedBy { it.dirName }
    }

    @MessageMapping("$VIDEOS_TOPIC/upload")
    fun upload(@Payload path: String) {
        PathUtils.getIdFromPath(path)?.let {
            videoService.getInformationsFrom(fileService.recordingDir.resolve(path))?.let {
                ytSession.curPlaylist?.let { currentPlaylist ->
                    it.playlistId = currentPlaylist.id
                }
                youtubeService.upload(it)
            }
        }
    }

    companion object {
        const val VIDEOS_TOPIC = "/videos"
    }
}
