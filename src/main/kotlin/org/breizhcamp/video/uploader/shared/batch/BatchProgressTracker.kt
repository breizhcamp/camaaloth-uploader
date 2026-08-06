package org.breizhcamp.video.uploader.shared.batch

import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

const val BATCH_TOPIC = "/batch"

/** How far a long running operation has got, as shown by the progress bar */
data class BatchProgress(
    val id: String,
    val label: String,
    val done: Int,
    val total: Int,
)

/**
 * Follows the long running operations so the page can show a progress bar for each.
 *
 * Several can run at once, each on its own thread, so they are kept apart by id: a single shared
 * state would be overwritten by whichever started last.
 */
@Service
class BatchProgressTracker(
    private val template: SimpMessagingTemplate,
) {
    private val batches = ConcurrentHashMap<String, BatchProgress>()

    /** The batches still running, in a stable order so the bars do not dance around */
    fun running(): List<BatchProgress> = batches.values.sortedBy { it.id }

    fun isRunning(id: String): Boolean = batches.containsKey(id)

    /**
     * Add items to a batch, starting it when it is not running yet.
     *
     * Adding one item at a time is what lets a queue fed video by video be followed the same way as
     * an operation that knows its size upfront.
     */
    fun add(id: String, label: String, count: Int) {
        //nothing to do means no bar at all: an empty batch would never be stepped, so it would sit
        //at 0 / 0 for ever
        if (count <= 0) return

        batches.compute(id) { _, current ->
            current?.copy(total = current.total + count) ?: BatchProgress(id, label, done = 0, total = count)
        }
        broadcast()
    }

    /** One item handled. The batch closes on its own once every item is accounted for. */
    fun step(id: String) {
        batches.computeIfPresent(id) { _, current ->
            val done = current.done + 1
            if (done >= current.total) null else current.copy(done = done)
        }
        broadcast()
    }

    /** Drop a batch that will never reach its total, after a failure for instance */
    fun finish(id: String) {
        batches.remove(id)
        broadcast()
    }

    private fun broadcast() {
        template.convertAndSend(BATCH_TOPIC, running())
    }
}
