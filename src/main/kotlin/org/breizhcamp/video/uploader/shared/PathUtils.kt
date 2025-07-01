package org.breizhcamp.video.uploader.shared

object PathUtils {

    /**
     * Retrieve event id from it's path name
     * @param path Path to retrieve id from
     * @return Id of the event, null if not found
     */
    fun getIdFromPath(path: String): String? {
        val dash = path.lastIndexOf('-')
        return if (dash < 0) {
            null
        } else path.substring(dash + 2)

    }
}