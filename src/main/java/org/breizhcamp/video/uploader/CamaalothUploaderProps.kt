package org.breizhcamp.video.uploader;

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Properties for camaalooth Uploader
 */
@ConfigurationProperties("camaaloth-uploader")
class CamaalothUploaderProps {

    /** directory containing recording, each in sub-directory */
    var recordingDir = "videos"

    /** directory containing assets, namely schedule.json, intro.svg and thumb.svg */
    var assetsDir = "assets"

}
