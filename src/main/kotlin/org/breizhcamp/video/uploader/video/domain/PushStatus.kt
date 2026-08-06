package org.breizhcamp.video.uploader.video.domain

/**
 * Whether the description or the thumbnail of a video made it to Youtube.
 *
 * DONE only ever means something was actually sent: a talk with no description in the schedule, or
 * with no thumb.png on disk, stays NOT_STARTED so a later run picks it up.
 */
enum class PushStatus {
    NOT_STARTED,
    IN_PROGRESS,
    DONE,
    FAILED,
}
