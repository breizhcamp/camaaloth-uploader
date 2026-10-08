package org.breizhcamp.video.uploader

import org.springframework.boot.context.properties.ConfigurationProperties
import java.nio.file.Paths

/**
 * Properties for camaalooth Uploader
 */
@ConfigurationProperties("camaaloth-uploader")
data class CamaalothUploaderProps(
    val recordingDir: String = "videos",
    /**
     * directory containing assets, namely schedule.json, intro.svg and thumb.svg. Inside the recording
     * directory by default, so that an edition carries its own schedule and thumbnail template.
     */
    val assetsDir: String = Paths.get(recordingDir, "assets").toString(),
)