package org.breizhcamp.video.uploader.event.domain

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import org.apache.commons.lang3.StringUtils
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * JSON deserialization of an Event
 */
data class Event(
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    val speakers: String? = null,
    val language: String? = null,
    val level:String? = null,

    @JsonProperty("event_start")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "UTC")
    val eventStart: ZonedDateTime? = null,

    @JsonProperty("event_end")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "UTC")
    val eventEnd: ZonedDateTime? = null,

    @JsonProperty("event_type")
    val eventType: String? = null,
    val format: String? = null,
    val venue: String? = null,
    @JsonProperty("venue_id")
    val venueId: String? = null,

    @JsonProperty("video_url")
    var videoUrl: String? = null,

    @JsonProperty("files_url")
    val filesUrl: String? = null,

    @JsonProperty("slides_url")
    val slidesUrl: String? = null,
) {
    private val dayFormat = DateTimeFormatter.ofPattern("dd")
    private val timeFormat = DateTimeFormatter.ofPattern("HH-mm")

    fun buildDirName(): String {
        val name = cleanForFilename(name)
        val speakers = cleanForFilename(speakers)
        return ("""${dayFormat.format(eventStart)}.${venue}.${timeFormat.format(eventStart)} - $name ($speakers) - ${id}""")
    }

    private fun cleanForFilename(str: String?) = str?.let {
        StringUtils.stripAccents(it)
            .replace(Regex("[\\\\/:*?\"<>|]"), "-")
            .replace(Regex("[^A-Za-z,\\-\\\\ ]"), "")
    }
}