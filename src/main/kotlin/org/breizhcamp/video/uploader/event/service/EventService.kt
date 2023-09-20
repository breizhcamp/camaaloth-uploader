package org.breizhcamp.video.uploader.event.service

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import com.fasterxml.jackson.module.kotlin.readValue
import org.breizhcamp.video.uploader.CamaalothUploaderProps
import org.breizhcamp.video.uploader.event.domain.Event
import org.springframework.stereotype.Service
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Paths
import java.util.*

@Service
class EventService(props: CamaalothUploaderProps) {

    private val mapper = jacksonMapperBuilder().addModule(JavaTimeModule()).build()
    private val scheduleFile: File = Paths.get(props.assetsDir, "schedule.json").toFile()

    /**
     * Read all events in schedule file
     * @return Event list
     */
    fun getEvents() = mapper.readValue<List<Event>>(scheduleFile)


    /**
     * Retrieve an event with his id
     * @param id Id of the event to retrieve
     * @return Event
     * @throws IOException If we cannot read schedule json file
     * @throws FileNotFoundException If the event is not found in schedule
     */
    fun findEventBy(id: String): Event? = getEvents().firstOrNull { it.id == id }

    /**
     * Generate ids for the events that don't have one
     */
    fun generateMissingIdsAndWrite() {
        mapper.writeValue(
            scheduleFile, getEvents()
                .map { event ->
                    if (event.id == null) {
                        event.copy(
                            id = UUID.randomUUID().toString()
                        )
                    } else event
                })
    }
}