package org.breizhcamp.video.uploader.video.domain

import com.fasterxml.jackson.annotation.JsonInclude

import java.math.BigDecimal

/**
 * JSON file stored aside of the video file to keep record of the current status
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class VideoMetadata(
    val status: VideoInfo.Status,
    val progression: BigDecimal?,
    val youtubeId: String?,
)