package org.breizhcamp.video.uploader.thumb

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.breizhcamp.video.uploader.shared.batch.BatchProgressTracker
import org.breizhcamp.video.uploader.video.service.VideoService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.timeout
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.messaging.simp.SimpMessagingTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ThumbGenerationTaskTest {

    private val generator = mock(ThumbGeneratorSrv::class.java)
    private val videoService = mock(VideoService::class.java)
    private val template = mock(SimpMessagingTemplate::class.java)
    private val batchProgress = mock(BatchProgressTracker::class.java)
    private val task = ThumbGenerationTask(generator, videoService, template, batchProgress)

    @Test
    fun `should report nothing running before the first generation`() {
        assertThat(task.isRunning()).isFalse()
    }

    @Test
    fun `should open a batch sized after the schedule`() {
        generatorReporting(listOf(0 to 96, 1 to 96, 2 to 96))

        task.start()

        awaitIdle()
        verify(batchProgress, timeout(1000)).add(THUMBS_BATCH, "Génération des miniatures", 96)
    }

    @Test
    fun `should count one step per thumbnail, not the opening event`() {
        generatorReporting(listOf(0 to 3, 1 to 3, 2 to 3, 3 to 3))

        task.start()

        awaitIdle()
        verify(batchProgress, timeout(1000).times(3)).step(THUMBS_BATCH)
    }

    @Test
    fun `should close the batch once the generation is over`() {
        generatorReporting(listOf(0 to 1, 1 to 1))

        task.start()

        awaitIdle()
        verify(batchProgress, timeout(1000)).finish(THUMBS_BATCH)
        assertThat(task.isRunning()).isFalse()
    }

    @Test
    fun `should refresh the video list once the thumbnails exist`() {
        generatorReporting(listOf(0 to 1, 1 to 1))

        task.start()

        awaitIdle()
        verify(videoService, timeout(1000)).list()
    }

    @Test
    fun `should refuse to start a second generation while one runs`() {
        val blocked = CountDownLatch(1)
        doAnswer { blocked.await(1, TimeUnit.SECONDS); null }
            .`when`(generator).generateAllThumbs(anyProgress())
        task.start()

        val accepted = task.start()

        assertThat(accepted).isFalse()
        blocked.countDown()
        awaitIdle()
    }

    @Test
    fun `should close the batch when the generation blows up`() {
        doThrow(IllegalStateException("Cannot run Inkscape"))
            .`when`(generator).generateAllThumbs(anyProgress())

        task.start()

        awaitIdle()
        verify(batchProgress, timeout(1000)).finish(THUMBS_BATCH)
        assertThat(task.isRunning()).isFalse()
    }

    @Test
    fun `should accept a new generation after a failure`() {
        doThrow(IllegalStateException("Cannot run Inkscape"))
            .`when`(generator).generateAllThumbs(anyProgress())
        task.start()
        awaitIdle()

        assertThat(task.start()).isTrue()
        awaitIdle()
    }

    /** Replay the (done, total) events the real generator would emit */
    private fun generatorReporting(events: List<Pair<Int, Int>>) {
        doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val onProgress = invocation.getArgument<(Int, Int) -> Unit>(0)
            events.forEach { (done, total) -> onProgress(done, total) }
            null
        }.`when`(generator).generateAllThumbs(anyProgress())
    }

    /** Register the matcher, then hand back a non null value Kotlin will accept */
    private fun anyProgress(): (Int, Int) -> Unit {
        org.mockito.ArgumentMatchers.any<(Int, Int) -> Unit>()
        return { _, _ -> }
    }

    private fun awaitIdle() {
        repeat(100) {
            if (!task.isRunning()) return
            Thread.sleep(20)
        }
    }
}
