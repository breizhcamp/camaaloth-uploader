package org.breizhcamp.video.uploader.web

import org.breizhcamp.video.uploader.file.service.FileService
import org.breizhcamp.video.uploader.video.service.VideoService
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import java.nio.file.Files

/**
 * Serves the videos of a talk to the player of the page, to listen to the original and the
 * normalized one side by side
 */
@Controller
class VideoFileController(
    private val fileService: FileService,
    private val videoService: VideoService,
) {

    /**
     * Serve the normalized video of one talk, or its original recording.
     *
     * Returned as a resource so that Spring answers the range requests a player makes to seek,
     * instead of sending gigabytes before the first frame.
     *
     * @param dir name of the talk directory. Only a direct child of the recording directory is
     * served: anything else would turn this into a way of reading any file on the machine.
     * @param original true for the recording as it came out of the camera, before normalization
     */
    @GetMapping("/video")
    fun video(@RequestParam dir: String, @RequestParam(defaultValue = "false") original: Boolean): ResponseEntity<Resource> {
        val recordingDir = fileService.recordingDir.toAbsolutePath().normalize()
        val talkDir = recordingDir.resolve(dir).normalize()

        if (talkDir.parent != recordingDir) return ResponseEntity.notFound().build()

        val video = videoService.getInformationsFrom(talkDir)
            ?.let { if (original) it.originalPath else it.path }
            ?.takeIf { Files.isRegularFile(it) }
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("video/mp4"))
            .body(FileSystemResource(video))
    }
}
