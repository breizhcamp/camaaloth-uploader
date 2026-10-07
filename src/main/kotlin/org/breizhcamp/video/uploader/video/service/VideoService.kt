package org.breizhcamp.video.uploader.video.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.breizhcamp.video.uploader.event.service.EventService
import org.breizhcamp.video.uploader.file.service.FileService
import org.breizhcamp.video.uploader.shared.PathUtils
import org.breizhcamp.video.uploader.video.domain.VideoInfo
import org.breizhcamp.video.uploader.video.domain.VideoMetadata
import io.github.oshai.kotlinlogging.KotlinLogging
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
    private val logger = KotlinLogging.logger { }


    fun list(): List<VideoInfo> {
        if (!Files.isDirectory(fileService.recordingDir)) return emptyList()

        //read once for the whole list: findEventBy would re-read the schedule file for every video
        val descriptions = runCatching { eventService.getEvents().associate { it.id to it.description } }
            .getOrElse {
                logger.warn(it) { "Cannot read the schedule, listing without the descriptions" }
                emptyMap()
            }

        return Files.list(fileService.recordingDir).asSequence()
            .filter { Files.isDirectory(it) }
            .mapNotNull { getInformationsFrom(it) }
            .onEach { it.description = descriptions[it.eventId] }
            .sortedBy { it.dirName }
            .toList()
    }

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
            path = (normalizedVideoOf(baseDirectory) ?: return null),
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
            youtubeId = video.youtubeId,
            descriptionStatus = video.descriptionStatus,
            thumbnailStatus = video.thumbnailStatus,
            loudness = video.loudness,
        )

        statusFile.parent.resolve(METADATA_FILENAME).let {
            objectMapper.writeValue(it.toFile(), metadata)
        }
    }

    /**
     * The normalized video of a talk, the only one ever uploaded: the one on disk, or else the one
     * scripts/normalize.sh will write beside the original. Null when the directory holds no video.
     *
     * Hidden files are left out: a normalization in progress (.1080p.normalizing.mp4), and the
     * ._1080p.mp4 resource forks macOS leaves on an exFAT drive. Sorted, as Files.list has no order.
     */
    private fun normalizedVideoOf(dir: Path): Path? {
        if (!Files.isDirectory(dir)) return null

        val videos = Files.list(dir).use { files ->
            files.asSequence()
                .filter { Files.isRegularFile(it) }
                .map { it.fileName.toString() }
                .filter { !it.startsWith(".") && it.lowercase().endsWith(".mp4") }
                .sorted()
                .toList()
        }

        val normalized = videos.firstOrNull { it.endsWith(VideoInfo.NORMALIZED_SUFFIX) }
            ?: videos.firstOrNull()?.let { it.substring(0, it.length - ".mp4".length) + VideoInfo.NORMALIZED_SUFFIX }
        return normalized?.let { dir.resolve(it) }
    }
}