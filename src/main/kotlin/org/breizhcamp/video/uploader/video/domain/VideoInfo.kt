package org.breizhcamp.video.uploader.video.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude

import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path

/**
 * VideoInfo file location and status
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class VideoInfo (
    /**
     * The normalized video, the only one ever uploaded. It may not exist yet, when
     * scripts/normalize.sh has not processed the talk: see [normalized].
     */
    val path: Path,
    val thumbnail: Path?,
    val eventId: String?,
    var status: Status,
    var youtubeId: String? = null,
    var progression: BigDecimal? = null,
    var playlistId: String? = null,
    var descriptionStatus: PushStatus = PushStatus.NOT_STARTED,
    var thumbnailStatus: PushStatus = PushStatus.NOT_STARTED,
    /** Kept here so that rewriting the metadata during an upload does not drop it */
    var loudness: Loudness? = null,
    /** Taken from the schedule, shown when hovering the row. Not persisted. */
    var description: String? = null,
){
    fun enrichWith(metadata: VideoMetadata) {
        status = metadata.status
        progression = metadata.progression
        youtubeId = metadata.youtubeId
        descriptionStatus = metadata.descriptionStatus
        thumbnailStatus = metadata.thumbnailStatus
        loudness = metadata.loudness
    }

    /** Whether the normalized video is on disk, and so can be uploaded */
    val normalized: Boolean
        get() = path.fileName.toString().endsWith(NORMALIZED_SUFFIX) && Files.isRegularFile(path)

    /** The recording [path] was normalized from, kept untouched beside it: 1080p.mp4 for 1080p.normalized.mp4 */
    @get:JsonIgnore
    val originalPath: Path
        get() = path.resolveSibling(path.fileName.toString().removeSuffix(NORMALIZED_SUFFIX) + ".mp4")

    /** Whether the original recording is on disk, to be played beside the normalized one */
    val hasOriginal: Boolean
        get() = Files.isRegularFile(originalPath)

    /**
     * @return The name of the directory the videos is
     */
    val dirName: String
        get() = path.parent?.fileName?.toString().orEmpty()

    enum class Status {
        NOT_STARTED,
        /** In upload queue  */
        WAITING,
        /** Initializing upload  */
        INITIALIZING,
        /** Upload in progress, progression should be populated  */
        IN_PROGRESS,
        /** Setting thumbnail in progress  */
        THUMBNAIL,
        /** Upload done, youtubeId should be set  */
        DONE,
        /** If something went wrong */
        FAILED
    }

    companion object {
        /** Written by scripts/normalize.sh beside the original: 1080p.mp4 gives 1080p.normalized.mp4 */
        const val NORMALIZED_SUFFIX = ".normalized.mp4"
    }
}
