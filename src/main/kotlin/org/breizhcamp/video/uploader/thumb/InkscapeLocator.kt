package org.breizhcamp.video.uploader.thumb

import org.springframework.boot.context.properties.bind.Binder
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.io.File

const val INKSCAPE_PATH_PROPERTY = "inkscape-path"

/** Used when no candidate is executable, so that the PATH is searched as a last resort. */
private const val INKSCAPE_IN_PATH = "inkscape"

/**
 * Locates the Inkscape binary.
 *
 * The path can be forced with the `--inkscape-path` argument or the `INKSCAPE_PATH` environment
 * variable; it is otherwise guessed from the usual install locations of the current OS. The property
 * is read through [Binder] so that relaxed binding makes `INKSCAPE_PATH` work out of the box.
 */
@Component
class InkscapeLocator(environment: Environment) {

    val path: String = resolveInkscapePath(
        Binder.get(environment).bind(INKSCAPE_PATH_PROPERTY, String::class.java).orElse(null),
        osCandidates(),
    )
}

/** The configured path when set, else the first executable candidate, else [INKSCAPE_IN_PATH]. */
fun resolveInkscapePath(configured: String?, candidates: List<String>): String = when {
    !configured.isNullOrBlank() -> configured
    else -> candidates.firstOrNull { File(it).canExecute() } ?: INKSCAPE_IN_PATH
}

private fun osCandidates(): List<String> =
    if (System.getProperty("os.name").startsWith("Mac")) {
        listOf(
            "/Applications/Inkscape.app/Contents/MacOS/inkscape",
            "/opt/homebrew/bin/inkscape",
            "/usr/local/bin/inkscape",
        )
    } else {
        listOf("/usr/bin/inkscape", "/usr/local/bin/inkscape")
    }
