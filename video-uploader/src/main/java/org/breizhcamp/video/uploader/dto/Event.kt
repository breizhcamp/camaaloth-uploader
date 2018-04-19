package org.breizhcamp.video.uploader.dto

import com.fasterxml.jackson.annotation.JsonProperty

import java.time.LocalDateTime

/**
 * JSON deserialization of an Event
 */
class Event {
    var id: Integer? = null
    var name: String? = null
    var description: String? = null
    var speakers: String? = null
    var language: String? = null
    @JsonProperty("event_start")
    var eventStart: LocalDateTime? = null
    @JsonProperty("event_end")
    var eventEnd: LocalDateTime? = null
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
}
