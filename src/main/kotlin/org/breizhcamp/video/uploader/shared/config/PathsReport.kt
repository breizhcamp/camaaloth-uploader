package org.breizhcamp.video.uploader.shared.config

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.breizhcamp.video.uploader.CamaalothUploaderProps
import org.breizhcamp.video.uploader.thumb.InkscapeLocator
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** One line of the paths panel */
data class PathEntry(
    val group: String,
    val label: String,
    val path: String,
    /** null when the entry is not a file or a directory we can look for */
    val exists: Boolean? = null,
    val note: String? = null,
)

/**
 * Everything the application reads and writes, so a surprise can be traced to a file rather than
 * guessed at. The paths come from several unrelated properties, which is exactly why seeing them
 * side by side helps.
 */
@Component
class PathsReport(
    private val props: CamaalothUploaderProps,
    private val environment: Environment,
    private val inkscape: InkscapeLocator,
) {
    private val mapper = jacksonObjectMapper()

    fun entries(): List<PathEntry> {
        val recordingDir = Paths.get(props.recordingDir).toAbsolutePath()
        val assetsDir = Paths.get(props.assetsDir).toAbsolutePath()
        val schedule = assetsDir.resolve("schedule.json")
        val datastore = Paths.get(bind("videos.dir") ?: "./videos", ".datastore", "StoredCredential")
            .toAbsolutePath()

        return listOf(
            directory("Répertoires", "Vidéos", recordingDir),
            directory("Répertoires", "Assets", assetsDir),
            PathEntry("Répertoires", "Lancement", Paths.get("").toAbsolutePath().toString()),

            file("Fichiers", "schedule.json", schedule, note = eventCount(schedule)),
            file("Fichiers", "thumb.svg", assetsDir.resolve("thumb.svg")),
            file("Fichiers", "playlist.json", recordingDir.resolve("playlist.json")),
            file("Fichiers", "Token YouTube", datastore),

            oauthEntry(),
            PathEntry("Outils", "Inkscape", inkscape.path, exists = Files.isExecutable(Paths.get(inkscape.path))),
        )
    }

    private fun directory(group: String, label: String, path: Path) =
        PathEntry(group, label, path.toString(), exists = Files.isDirectory(path))

    private fun file(group: String, label: String, path: Path, note: String? = null) =
        PathEntry(group, label, path.toString(), exists = Files.isRegularFile(path), note = note)

    /** The secret itself never leaves the server, only where it was read from */
    private fun oauthEntry(): PathEntry {
        val inline = bind(OAUTH_GOOGLE_PROPERTY)
        if (!inline.isNullOrBlank()) {
            return PathEntry("Outils", "Client OAuth", "--$OAUTH_GOOGLE_PROPERTY (JSON en ligne)")
        }

        val path = bind(OAUTH_GOOGLE_PATH_PROPERTY)
        if (path.isNullOrBlank()) return PathEntry("Outils", "Client OAuth", "non fourni", exists = false)

        return file("Outils", "Client OAuth", Paths.get(path))
    }

    private fun eventCount(schedule: Path): String? = try {
        "${mapper.readValue(schedule.toFile(), List::class.java).size} events"
    } catch (e: Exception) {
        null
    }

    private fun bind(property: String): String? =
        Binder.get(environment).bind(property, String::class.java).orElse(null)
}
