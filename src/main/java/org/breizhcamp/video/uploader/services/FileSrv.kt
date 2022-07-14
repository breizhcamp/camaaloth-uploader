package org.breizhcamp.video.uploader.services

import org.breizhcamp.video.uploader.CamaalothUploaderProps
import org.breizhcamp.video.uploader.dto.Event
import org.springframework.stereotype.Service
import org.apache.commons.lang3.StringUtils.stripAccents

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.format.DateTimeFormatter

/**
 * Handle directory and files operations
 */
@Service
class FileSrv(private val eventSrv: EventSrv, private val props: CamaalothUploaderProps) {

    private val dayFormat = DateTimeFormatter.ofPattern("dd")
    private val timeFormat = DateTimeFormatter.ofPattern("HH-mm")

    val recordingDir: Path
        get() = Paths.get(props.recordingDir).toAbsolutePath()

    /**
     * Create one directory for each event in videosDir.
     */
    @Throws(IOException::class)
    fun createDirs() {
        Files.createDirectories(recordingDir)
        val events = eventSrv.read()

        for (event in events) {
            val dir = recordingDir.resolve(buildDirName(event))
            if (!Files.exists(dir)) {
                Files.createDirectory(dir)
            }
        }
    }

    /**
     * Retrieve event id from it's path name
     * @param path Path to retrieve id from
     * @return Id of the event, null if not found
     */
    fun getIdFromPath(path: String): String? {
        val dash = path.lastIndexOf('-')
        return if (dash < 0) {
            null
        } else path.substring(dash + 2)

    }

    fun buildDirName(talk: Event): String {
        val name = cleanForFilename(talk.name)
        val speakers = cleanForFilename(talk.speakers)

        return (dayFormat.format(talk.eventStart) + "." + talk.venue + "." + timeFormat.format(talk.eventStart)
                + " - " + name + " (" + speakers + ") - " + talk.id)
    }

    private fun cleanForFilename(str: String?) = str?.let { str ->
        stripAccents(str)
            .replace(Regex("[\\\\/:*?\"<>|]"), "-")
            .replace(Regex("[^A-Za-z,\\-\\\\ ]"), "")
    }
}
