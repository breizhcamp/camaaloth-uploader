package org.breizhcamp.video.uploader.dto

import com.fasterxml.jackson.annotation.JsonProperty

import java.time.LocalDateTime

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
    var eventStart: LocalDateTime? = null

    @JsonProperty("event_type")
    var eventType: String? = null
    var format: String? = null
    var venue: String? = null
}
