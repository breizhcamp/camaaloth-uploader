package org.breizhcamp.video.uploader.services

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonMapperBuilder
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.breizhcamp.video.uploader.CamaalothUploaderProps
import org.breizhcamp.video.uploader.dto.Event
import org.springframework.stereotype.Service
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.lang.Integer.max
import java.nio.file.Paths

@Service
class EventSrv(props: CamaalothUploaderProps) {

    val mapper = jacksonMapperBuilder().addModule(JavaTimeModule()).build()

    val scheduleFile: File = Paths.get(props.assetsDir, "schedule.json").toFile()

    /**
     * Read all events in schedule file
     * @return Event list
     */
    @Throws(IOException::class)
    fun read() = mapper.readValue<List<Event>>(scheduleFile)

    /**
     * Retrieve all events group by id
     * @return Event list
     */
    @Throws(IOException::class)
    fun readAndAssociateByIds() = read().associateBy { it.id }

    /**
     * Retrieve an event with his id
     * @param id Id of the event to retrieve
     * @return Event
     * @throws IOException If we cannot read schedule json file
     * @throws FileNotFoundException If the event is not found in schedule
     */
    @Throws(IOException::class)
    fun readAndGetById(id: Int): Event? = readAndAssociateByIds()[id]

    /**
     * Generate ids for the events that don't have one
     */
    fun generateMissingIdsAndWrite() {
        val events = read()
        var nextId = events.fold(0) { acc, event -> max(acc, event.id ?: 0) } + 10000000

        val fixedEvents = events.map { event ->
            if (event.id == null) {
                event.id = nextId
                nextId++
            }

            event
        }

        mapper.writeValue(scheduleFile, fixedEvents)
    }
}