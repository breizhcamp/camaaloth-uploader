package org.breizhcamp.video.uploader.video.domain

/**
 * Loudness of the normalized video, measured by scripts/normalize.sh: the application only reads it.
 *
 * Targets are -23 LUFS integrated and -3 dBFS true peak.
 */
data class Loudness(
    /** Integrated loudness, in LUFS */
    val integrated: Double,
    /** True peak, in dBFS */
    val truePeak: Double,
)
