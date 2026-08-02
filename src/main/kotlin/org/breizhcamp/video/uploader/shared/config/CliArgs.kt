package org.breizhcamp.video.uploader.shared.config

/** Options accepting a value separated by a space, on top of the `--option=value` form. */
private val SPACED_OPTIONS = setOf("--oauth-google", "--oauth-google-path")

/**
 * Turns `--option value` into `--option=value` so Spring's command line parser can bind it,
 * as it only understands the `--option=value` form.
 */
fun normalizeSpacedOptions(args: Array<String>): Array<String> {
    val normalized = ArrayList<String>(args.size)
    var i = 0
    while (i < args.size) {
        val arg = args[i]
        val next = args.getOrNull(i + 1)
        if (arg in SPACED_OPTIONS && next != null && !next.startsWith("--")) {
            normalized.add("$arg=$next")
            i += 2
        } else {
            normalized.add(arg)
            i++
        }
    }
    return normalized.toTypedArray()
}
