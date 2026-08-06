package org.breizhcamp.video.uploader.web

import org.breizhcamp.video.uploader.file.service.FileService
import org.breizhcamp.video.uploader.shared.batch.BATCH_TOPIC
import org.breizhcamp.video.uploader.shared.batch.BatchProgress
import org.breizhcamp.video.uploader.shared.batch.BatchProgressTracker
import org.breizhcamp.video.uploader.thumb.ThumbGenerationTask
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.messaging.simp.annotation.SubscribeMapping
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import java.nio.file.Files

/**
 * Drives the thumbnail generation, hands the progress of every batch to the page, and serves the
 * generated thumbnails for the preview shown on hover
 */
@Controller
class ThumbController(
    private val thumbGenerationTask: ThumbGenerationTask,
    private val batchProgress: BatchProgressTracker,
    private val fileService: FileService,
) {

    @PostMapping("/generateThumbs")
    fun generateThumbs(): String {
        thumbGenerationTask.start()
        return "redirect:/"
    }

    /** Give a page that just loaded the batches already running, so its bars show up right away */
    @SubscribeMapping(BATCH_TOPIC)
    fun subscribe(): List<BatchProgress> = batchProgress.running()

    /**
     * Serve the thumb.png of one talk.
     *
     * @param dir name of the talk directory. Only a direct child of the recording directory is
     * served: anything else would turn this into a way of reading any file on the machine.
     */
    @GetMapping("/thumb")
    fun thumbnail(@RequestParam dir: String): ResponseEntity<ByteArray> {
        val recordingDir = fileService.recordingDir.toAbsolutePath().normalize()
        val thumb = recordingDir.resolve(dir).normalize().resolve("thumb.png")

        if (thumb.parent?.parent != recordingDir || !Files.isRegularFile(thumb)) {
            return ResponseEntity.notFound().build()
        }

        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_PNG)
            .body(Files.readAllBytes(thumb))
    }
}
