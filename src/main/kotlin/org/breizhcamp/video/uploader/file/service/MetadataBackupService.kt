package org.breizhcamp.video.uploader.file.service

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.name
import kotlin.streams.asSequence

/**
 * Save every .json file of the recording dir in a timestamped zip beside them: the metadata.json of
 * each talk, which hold the upload state, the selected playlist and the exported schedule.
 */
@Service
class MetadataBackupService(
    private val fileService: FileService,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val logger = KotlinLogging.logger { }

    data class Backup(val zip: Path, val files: Int)

    /** @return the backup written, or null when there was no .json file to save */
    fun backup(): Backup? {
        val root = fileService.recordingDir
        val files = jsonFiles()
        if (files.isEmpty()) return null

        val zip = root.resolve("metadata-backup-${LocalDateTime.now(clock).format(TIMESTAMP)}.zip")
        // written aside then renamed, so a failed backup never leaves a zip that looks complete
        val partial = root.resolve(".${zip.name}.partial")
        try {
            ZipOutputStream(Files.newOutputStream(partial)).use { out ->
                files.forEach { file ->
                    // always '/', whatever the platform, as the zip format expects
                    out.putNextEntry(ZipEntry(root.relativize(file).joinToString("/")))
                    Files.copy(file, out)
                    out.closeEntry()
                }
            }
            Files.move(partial, zip, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(partial)
        }

        logger.info { "Saved ${files.size} json files in [$zip]" }
        return Backup(zip, files.size)
    }

    /**
     * Hidden files and directories are left out: the ._metadata.json resource forks macOS leaves on
     * an exFAT drive, and the .datastore holding the YouTube token when it shares the directory.
     */
    fun jsonFiles(): List<Path> = fileService.recordingDir.let { root -> Files.walk(root).use { paths ->
        paths.asSequence()
            .filter { Files.isRegularFile(it) && it.name.lowercase().endsWith(".json") }
            .filter { file -> root.relativize(file).none { it.name.startsWith(".") } }
            .sorted()
            .toList()
    } }

    companion object {
        private val TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }
}
