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
    /** Absent from the files written before these existed, which means nothing was pushed yet */
    val descriptionStatus: PushStatus = PushStatus.NOT_STARTED,
    val thumbnailStatus: PushStatus = PushStatus.NOT_STARTED,
    /** Written by scripts/normalize.sh, absent until the video went through it */
    val loudness: Loudness? = null,
)