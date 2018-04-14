package org.breizhcamp.video.uploader.dto

import com.fasterxml.jackson.annotation.JsonInclude

import java.math.BigDecimal

/**
 * JSON file stored aside of the video file to keep record of the current status
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
class VideoMetadata {

    var status: VideoInfo.Status? = null
    var progression: BigDecimal? = null
    var youtubeId: String? = null

    constructor() {}

    constructor(status: VideoInfo.Status, progression: BigDecimal, youtubeId: String) {
        this.status = status
        this.progression = progression
        this.youtubeId = youtubeId
    }
}
