package org.breizhcamp.video.uploader.controller

import org.breizhcamp.video.uploader.dto.YoutubeSession
import org.breizhcamp.video.uploader.services.EventSrv
import org.breizhcamp.video.uploader.services.FileSrv
import org.breizhcamp.video.uploader.services.VideoSrv
import org.breizhcamp.video.uploader.services.YoutubeSrv
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.ui.set
import org.springframework.web.bind.annotation.GetMapping
import java.nio.file.Files

@Controller
class HomeKtCtrl(
    private val eventSrv: EventSrv,
    private val fileSrv: FileSrv,
    private val videoSrv: VideoSrv,
    private val youtubeSrv: YoutubeSrv,
    private val ytSession: YoutubeSession,
) {

    @GetMapping
    fun home(model: Model): String {
        val videosDir = fileSrv.recordingDir
        val connected = youtubeSrv.isConnected

        model["videosDir"] = videosDir
        model["dirExists"] = Files.isDirectory(videosDir)
        model["connected"] = connected
        model["ytSession"] = ytSession

        return "index"
    }

}