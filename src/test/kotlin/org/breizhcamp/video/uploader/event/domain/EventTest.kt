package org.breizhcamp.video.uploader.event.domain

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Test

/**
 * The schedule is produced by the BreizhCamp website and gains fields over time: reading it must not
 * fail on the unknown ones, and rewriting it must not drop them.
 */
class EventTest {

    private val mapper = jacksonMapperBuilder().addModule(JavaTimeModule()).build()

    private val json = """
        {"id":"abc","name":"Mon talk","speakers":"Alice","venue":"Salle 1",
         "event_start":"2026-07-02T10:00:00","day_of_week":"jeudi","some_new_field":42}
    """.trimIndent()

    @Test
    fun `should read an event carrying unknown fields`() {
        val event = mapper.readValue<Event>(json)

        assertThat(event.id).isEqualTo("abc")
        assertThat(event.name).isEqualTo("Mon talk")
    }

    @Test
    fun `should write back the unknown fields`() {
        val event = mapper.readValue<Event>(json)

        val written = mapper.writeValueAsString(event)

        assertThat(written).contains("\"day_of_week\":\"jeudi\"")
        assertThat(written).contains("\"some_new_field\":42")
    }

    @Test
    fun `should keep the unknown fields through a copy`() {
        val event = mapper.readValue<Event>(json)

        val written = mapper.writeValueAsString(event.copy(id = "generated"))

        assertThat(written).contains("\"day_of_week\":\"jeudi\"")
        assertThat(written).contains("\"id\":\"generated\"")
    }
}
