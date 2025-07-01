package org.breizhcamp.video.uploader.video.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.breizhcamp.video.uploader.event.service.EventService
import org.breizhcamp.video.uploader.file.service.FileService
import org.breizhcamp.video.uploader.shared.PathUtils
import org.breizhcamp.video.uploader.video.domain.VideoInfo
import org.breizhcamp.video.uploader.video.domain.VideoMetadata
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import kotlin.streams.asSequence

private const val METADATA_FILENAME = "metadata.json"

@Service
class VideoService(
    private val fileService: FileService,
    private val objectMapper: ObjectMapper,
    private val eventService: EventService
) {

    fun list(): List<VideoInfo> = if (!Files.isDirectory(fileService.recordingDir)) emptyList() else
        Files.list(fileService.recordingDir).asSequence()
            .filter { Files.isDirectory(it) }
            .mapNotNull { getInformationsFrom(it) }
            .sortedBy { it.dirName }
            .toList()

    fun generateUpdatedSchedule() {
        val completedUploadsUrls = list()
            .filter { it.status == VideoInfo.Status.DONE }
            .associate { it.eventId to it.youtubeId }

        val updatedEvents = eventService.getEvents()
            .onEach { event ->
                completedUploadsUrls[event.id]?.let {
                    event.videoUrl = "https://www.youtube.com/watch?v=$it"
                }
            }
            .sortedBy { it.id }

        objectMapper.writeValue(fileService.recordingDir.resolve("schedule.json").toFile(), updatedEvents)
    }

    fun getInformationsFrom(baseDirectory: Path): VideoInfo? {
        val videoInfo = VideoInfo(
            path = (getFirstFileFromExt(baseDirectory, "mp4") ?: return null),
            status = VideoInfo.Status.NOT_STARTED,
            thumbnail = baseDirectory.resolve("thumb.png").takeIf { it.toFile().exists() },
            eventId = PathUtils.getIdFromPath(baseDirectory.fileName.toString())
        )

        baseDirectory.resolve(METADATA_FILENAME)
            .takeIf { Files.exists(it) }
            ?.let { videoInfo.enrichWith(metadata = objectMapper.readValue<VideoMetadata>(it.toFile())) }

        return videoInfo
    }

    fun updateVideo(video: VideoInfo) {
        val statusFile =
            requireNotNull(video.path) { "Cannot update metadata of [${video.dirName}] because no path defined" }

        val metadata = VideoMetadata(
            status = video.status,
            progression = video.progression,
            youtubeId = video.youtubeId
        )

        statusFile.parent.resolve(METADATA_FILENAME).let {
            objectMapper.writeValue(it.toFile(), metadata)
        }
    }

    /**
     * List a directory to retrieve the first file with the specified extension
     * @param dir Directory to read
     * @param ext Extension to find
     * @return First file found or null if any file with specified extension exists within the directory
     */
    private fun getFirstFileFromExt(dir: Path, vararg ext: String): Path? {
        if (!Files.isDirectory(dir)) return null

        val suffixes = ext.map { ".$it" }
        return Files.list(dir).asSequence()
            .firstOrNull { f -> suffixes.any { f.toString().lowercase().endsWith(it) } }
    }
}