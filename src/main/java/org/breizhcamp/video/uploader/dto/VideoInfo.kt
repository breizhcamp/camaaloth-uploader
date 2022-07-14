package org.breizhcamp.video.uploader.dto

import com.fasterxml.jackson.annotation.JsonInclude

import java.math.BigDecimal
import java.nio.file.Path

/**
 * VideoInfo file location and status
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
class VideoInfo {

    var path: Path? = null
    var thumbnail: Path? = null
    var eventId: String? = null
    var status: Status? = null
    var youtubeId: String? = null
    var progression: BigDecimal? = null

    /** Id of the playlist the video is inserted into after the upload  */
    var playlistId: String? = null

    /**
     * @return The name of the directory the videos is
     */
    val dirName: String
        get() = if (path == null) "" else path!!.parent.fileName.toString()

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

    constructor() {}

    constructor(path: Path, thumbnail: Path, eventId: String, status: Status, youtubeId: String, progression: BigDecimal, playlistId: String) {
        this.path = path
        this.thumbnail = thumbnail
        this.eventId = eventId
        this.status = status
        this.youtubeId = youtubeId
        this.progression = progression
        this.playlistId = playlistId
    }
}
