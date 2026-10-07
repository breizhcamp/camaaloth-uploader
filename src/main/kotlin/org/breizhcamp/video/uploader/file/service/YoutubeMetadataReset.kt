package org.breizhcamp.video.uploader.file.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import io.github.oshai.kotlinlogging.KotlinLogging
import org.breizhcamp.video.uploader.video.domain.VideoInfo
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.name

/**
 * Forget every upload to YouTube, so the edition can be uploaded again from scratch.
 *
 * Every .json file of the recording dir is saved first, and nothing is touched when that fails. Then
 * the youtubeId and video_url are removed wherever they are, and each metadata.json goes back to
 * NOT_STARTED. Everything else stays: the loudness, the selected playlist, the schedule itself.
 */
@Service
class YoutubeMetadataReset(
    private val metadataBackup: MetadataBackupService,
    private val objectMapper: ObjectMapper,
) {
    private val logger = KotlinLogging.logger { }

    data class Result(
        /** Null when there was no .json file at all, so nothing to save nor to clean */
        val backup: MetadataBackupService.Backup?,
        /** How many files had something removed */
        val cleaned: Int,
        /** Files that could not be read nor written, left as they were */
        val failed: List<Path>,
    )

    fun reset(): Result {
        val backup = metadataBackup.backup() ?: return Result(null, 0, emptyList())

        var cleaned = 0
        val failed = mutableListOf<Path>()
        metadataBackup.jsonFiles().forEach { file ->
            try {
                if (clean(file)) cleaned++
            } catch (e: Exception) {
                logger.warn(e) { "Cannot remove the YouTube metadata of [$file], left as it was" }
                failed.add(file)
            }
        }

        logger.info { "YouTube metadata removed from $cleaned files, saved before in [${backup.zip}]" }
        return Result(backup, cleaned, failed)
    }

    /** @return whether the file had anything to remove, and so was rewritten */
    private fun clean(file: Path): Boolean {
        val tree = objectMapper.readTree(file.toFile())
        var changed = removeEverywhere(tree)

        if (file.name == METADATA_FILENAME && tree is ObjectNode) {
            UPLOAD_FIELDS.forEach { changed = (tree.remove(it) != null) || changed }
            if (tree.path("status").asText() != VideoInfo.Status.NOT_STARTED.name) {
                tree.put("status", VideoInfo.Status.NOT_STARTED.name)
                changed = true
            }
        }

        if (changed) {
            // written aside then renamed: a failure halfway must not leave a truncated file
            val partial = file.resolveSibling(".${file.name}.partial")
            objectMapper.writeValue(partial.toFile(), tree)
            Files.move(partial, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }
        return changed
    }

    /** Drop the YouTube identifiers at any depth: the exported schedule holds them in an array of events */
    private fun removeEverywhere(node: JsonNode): Boolean {
        var changed = false
        when (node) {
            is ObjectNode -> {
                YOUTUBE_FIELDS.forEach { changed = (node.remove(it) != null) || changed }
                node.elements().forEach { changed = removeEverywhere(it) || changed }
            }
            is ArrayNode -> node.elements().forEach { changed = removeEverywhere(it) || changed }
        }
        return changed
    }

    companion object {
        private const val METADATA_FILENAME = "metadata.json"
        private val YOUTUBE_FIELDS = listOf("youtubeId", "video_url")
        /** What metadata.json keeps of an upload, beside its youtubeId and status */
        private val UPLOAD_FIELDS = listOf("progression", "descriptionStatus", "thumbnailStatus")
    }
}
