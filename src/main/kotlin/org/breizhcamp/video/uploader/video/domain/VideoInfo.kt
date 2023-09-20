package org.breizhcamp.video.uploader.video.domain

import com.fasterxml.jackson.annotation.JsonInclude

import java.math.BigDecimal
import java.nio.file.Path

/**
 * VideoInfo file location and status
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class VideoInfo (
    val path: Path,
    val thumbnail: Path?,
    val eventId: String?,
    var status: Status,
    var youtubeId: String? = null,
    var progression: BigDecimal? = null,
    var playlistId: String? = null
){
    fun enrichWith(metadata: VideoMetadata) {
        status = metadata.status
        progression = metadata.progression
        youtubeId = metadata.youtubeId
    }

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
}
