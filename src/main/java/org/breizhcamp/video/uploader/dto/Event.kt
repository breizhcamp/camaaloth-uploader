package org.breizhcamp.video.uploader.dto

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

import java.time.ZonedDateTime

/**
 * JSON deserialization of an Event
 */
class Event {
    var id: String? = null
    var name: String? = null
    var description: String? = null
    var speakers: String? = null
    var language: String? = null
    @JsonProperty("event_start")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "UTC")
    var eventStart: ZonedDateTime? = null
    @JsonProperty("event_end")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "UTC")
    var eventEnd: ZonedDateTime? = null
    @JsonProperty("event_type")
    var eventType: String? = null
    var format: String? = null
    var venue: String? = null
    @JsonProperty("venue_id")
    var venueId: String? = null
    @JsonProperty("video_url")
    var videoUrl:String? = null
    @JsonProperty("files_url")
    var filesUrl:String? = null
    @JsonProperty("slides_url")
    var slidesUrl: String? = null
    @JsonProperty("level")
    var level: String? = null
}
