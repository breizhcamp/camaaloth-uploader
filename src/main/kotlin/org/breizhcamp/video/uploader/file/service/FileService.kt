package org.breizhcamp.video.uploader.file.service

import org.breizhcamp.video.uploader.CamaalothUploaderProps
import org.breizhcamp.video.uploader.event.service.EventService
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Handle directory and files operations
 */
@Service
class FileService(
    private val eventService: EventService,
    private val props: CamaalothUploaderProps
) {

    val recordingDir: Path
        get() = Paths.get(props.recordingDir).toAbsolutePath()

    /**
     * Create one directory for each event in videosDir.
     */
    fun createDirs() {
        Files.createDirectories(recordingDir)
        eventService.getEvents()
            .map { recordingDir.resolve(it.buildDirName()) }
            .filterNot { Files.exists(it) }
            .forEach { Files.createDirectory(it) }
    }

}
