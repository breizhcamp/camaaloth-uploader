package org.breizhcamp.video.uploader.shared.batch

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.messaging.simp.SimpMessagingTemplate

class BatchProgressTrackerTest {

    private val template = mock(SimpMessagingTemplate::class.java)
    private val tracker = BatchProgressTracker(template)

    @Test
    fun `should report nothing running at rest`() {
        assertThat(tracker.running()).isEmpty()
    }

    @Test
    fun `should report a batch as soon as it is added`() {
        tracker.add("thumbs", "Génération des miniatures", 25)

        assertThat(tracker.running()).isEqualTo(
            listOf(BatchProgress("thumbs", "Génération des miniatures", done = 0, total = 25))
        )
    }

    @Test
    fun `should count the items handled`() {
        tracker.add("thumbs", "Génération des miniatures", 3)

        tracker.step("thumbs")
        tracker.step("thumbs")

        assertThat(tracker.running().single().done).isEqualTo(2)
    }

    @Test
    fun `should close a batch once every item is handled`() {
        tracker.add("thumbs", "Génération des miniatures", 2)

        tracker.step("thumbs")
        tracker.step("thumbs")

        assertThat(tracker.running()).isEmpty()
    }

    @Test
    fun `should grow a batch that is fed one item at a time`() {
        tracker.add("uploads", "Envoi des vidéos", 1)
        tracker.add("uploads", "Envoi des vidéos", 1)

        assertThat(tracker.running().single().total).isEqualTo(2)

        tracker.step("uploads")
        assertThat(tracker.running().single()).isEqualTo(
            BatchProgress("uploads", "Envoi des vidéos", done = 1, total = 2)
        )
    }

    @Test
    fun `should keep concurrent batches apart`() {
        tracker.add("thumbs", "Génération des miniatures", 2)
        tracker.add("uploads", "Envoi des vidéos", 5)

        tracker.step("thumbs")

        assertThat(tracker.running()).containsExactlyInAnyOrder(
            BatchProgress("thumbs", "Génération des miniatures", done = 1, total = 2),
            BatchProgress("uploads", "Envoi des vidéos", done = 0, total = 5),
        )
    }

    @Test
    fun `should drop a batch that gave up half way`() {
        tracker.add("thumbs", "Génération des miniatures", 25)
        tracker.step("thumbs")

        tracker.finish("thumbs")

        assertThat(tracker.running()).isEmpty()
    }

    @Test
    fun `should ignore a step on a batch that is not running`() {
        tracker.step("thumbs")

        assertThat(tracker.running()).isEmpty()
    }

    @Test
    fun `should tell whether a batch is running`() {
        assertThat(tracker.isRunning("thumbs")).isEqualTo(false)

        tracker.add("thumbs", "Génération des miniatures", 1)

        assertThat(tracker.isRunning("thumbs")).isEqualTo(true)
    }

    @Test
    fun `should broadcast the batches on every change`() {
        tracker.add("thumbs", "Génération des miniatures", 1)
        tracker.step("thumbs")

        verify(template, atLeastOnce()).convertAndSend(BATCH_TOPIC, emptyList<BatchProgress>())
        verify(template).convertAndSend(
            BATCH_TOPIC,
            listOf(BatchProgress("thumbs", "Génération des miniatures", done = 0, total = 1)),
        )
    }
}
