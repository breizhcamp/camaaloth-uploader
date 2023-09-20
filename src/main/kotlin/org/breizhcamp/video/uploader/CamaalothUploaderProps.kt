package org.breizhcamp.video.uploader

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Properties for camaalooth Uploader
 */
@ConfigurationProperties("camaaloth-uploader")
data class CamaalothUploaderProps(
    val recordingDir: String = "videos",
    /** directory containing assets, namely schedule.json, intro.svg and thumb.svg */
    val assetsDir: String = "assets",
)