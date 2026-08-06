package org.breizhcamp.video.uploader.web

import org.breizhcamp.video.uploader.shared.batch.BATCH_TOPIC
import org.breizhcamp.video.uploader.shared.batch.BatchProgress
import org.breizhcamp.video.uploader.shared.batch.BatchProgressTracker
import org.breizhcamp.video.uploader.thumb.ThumbGenerationTask
import org.springframework.messaging.simp.annotation.SubscribeMapping
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping

/**
 * Drives the thumbnail generation, and hands the progress of every batch to the page
 */
@Controller
class ThumbController(
    private val thumbGenerationTask: ThumbGenerationTask,
    private val batchProgress: BatchProgressTracker,
) {

    @PostMapping("/generateThumbs")
    fun generateThumbs(): String {
        thumbGenerationTask.start()
        return "redirect:/"
    }

    /** Give a page that just loaded the batches already running, so its bars show up right away */
    @SubscribeMapping(BATCH_TOPIC)
    fun subscribe(): List<BatchProgress> = batchProgress.running()
}
