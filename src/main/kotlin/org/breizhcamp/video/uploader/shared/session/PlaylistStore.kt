package org.breizhcamp.video.uploader.shared.session

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.api.services.youtube.model.Playlist
import io.github.oshai.kotlinlogging.KotlinLogging
import org.breizhcamp.video.uploader.file.service.FileService
import org.springframework.stereotype.Component
import java.nio.file.Files

private const val PLAYLIST_FILENAME = "playlist.json"

/** The playlist chosen by the user, as kept on disk */
data class SelectedPlaylist(
    val id: String,
    val title: String? = null,
)

/**
 * Remembers the selected playlist in the recording directory, so it outlives a restart.
 *
 * The selection is a convenience: a missing or damaged file must never keep the home page from
 * rendering, it only means nothing is selected yet.
 */
@Component
class PlaylistStore(
    private val fileService: FileService,
    private val objectMapper: ObjectMapper,
) {
    private val logger = KotlinLogging.logger { }

    fun read(): SelectedPlaylist? {
        val file = fileService.recordingDir.resolve(PLAYLIST_FILENAME)
        if (!Files.exists(file)) return null

        return try {
            objectMapper.readValue<SelectedPlaylist>(file.toFile())
        } catch (e: Exception) {
            logger.warn(e) { "Cannot read the selected playlist from [$file], starting with none" }
            null
        }
    }

    /** Write the selection, or forget it when nothing is selected any more */
    fun write(playlist: Playlist?) {
        val file = fileService.recordingDir.resolve(PLAYLIST_FILENAME)

        try {
            if (playlist == null) {
                Files.deleteIfExists(file)
                logger.info { "Selected playlist forgotten" }
                return
            }

            Files.createDirectories(file.parent)
            objectMapper.writeValue(
                file.toFile(),
                SelectedPlaylist(id = playlist.id, title = playlist.snippet?.title),
            )
            logger.info { "Selected playlist [${playlist.id}] remembered in [$file]" }
        } catch (e: Exception) {
            logger.warn(e) { "Cannot remember the selected playlist in [$file]" }
        }
    }
}
