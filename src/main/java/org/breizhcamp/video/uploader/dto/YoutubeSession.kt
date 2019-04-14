package org.breizhcamp.video.uploader.dto

import com.google.api.services.youtube.model.Channel
import com.google.api.services.youtube.model.Playlist

/**
 * Current channels, selected channel and playlist
 */
class YoutubeSession {

    /** List of all channel in current logged account  */
    var channels: List<Channel>? = null

    /** Selected channel  */
    var curChan: Channel? = null

    /** List of all playlist for the curChan  */
    var playlists: List<Playlist>? = null

    /** Selected playlist  */
    var curPlaylist: Playlist? = null
}
