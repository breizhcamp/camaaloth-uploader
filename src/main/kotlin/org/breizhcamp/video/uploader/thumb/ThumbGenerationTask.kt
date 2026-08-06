package org.breizhcamp.video.uploader.thumb

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PreDestroy
import org.breizhcamp.video.uploader.shared.batch.BatchProgressTracker
import org.breizhcamp.video.uploader.video.service.VideoService
import org.breizhcamp.video.uploader.web.YoutubeController
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

const val THUMBS_BATCH = "thumbs"

/**
 * Runs the thumbnail generation in the background.
 *
 * Inkscape takes a second or so per talk, which is minutes for a whole schedule: far too long for
 * a request, hence a thread of its own and the progress reported to [BatchProgressTracker].
 */
@Service
class ThumbGenerationTask(
    private val generator: ThumbGeneratorSrv,
    private val videoService: VideoService,
    private val template: SimpMessagingTemplate,
    private val batchProgress: BatchProgressTracker,
) {
    private val logger = KotlinLogging.logger { }
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "ThumbGeneration") }

    /**
     * Guards against two generations at once. Kept here rather than read off the tracker, which only
     * learns about the batch once the worker reports its first event.
     */
    private val running = AtomicBoolean(false)

    @PreDestroy
    fun tearDown() {
        worker.shutdown()
    }

    /**
     * Start a generation unless one is already running.
     *
     * @return false when a generation was already under way, so the caller can say so
     */
    fun start(): Boolean {
        if (!running.compareAndSet(false, true)) {
            logger.info { "Thumbnail generation already running, ignoring the request" }
            return false
        }

        worker.execute { generate() }
        return true
    }

    fun isRunning(): Boolean = running.get()

    private fun generate() {
        logger.info { "Generating the thumbnails" }
        var announced = false
        try {
            generator.generateAllThumbs { done, total ->
                if (!announced) {
                    batchProgress.add(THUMBS_BATCH, "Génération des miniatures", total)
                    announced = true
                }
                if (done > 0) batchProgress.step(THUMBS_BATCH)
            }
            logger.info { "Thumbnails generated" }
        } catch (e: Exception) {
            logger.error(e) { "Thumbnail generation failed" }
        } finally {
            //a generation that gave up half way would otherwise leave its bar frozen
            batchProgress.finish(THUMBS_BATCH)
            running.set(false)
            //the thumbnail flag of every row is stale until the list is read again
            template.convertAndSend(YoutubeController.VIDEOS_TOPIC, videoService.list())
        }
    }
}
