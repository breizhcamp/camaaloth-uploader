package org.breizhcamp.video.uploader.video.repository

import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.http.FileContent
import com.google.api.client.http.HttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.youtube.YouTube
import com.google.api.services.youtube.model.*
import io.github.oshai.kotlinlogging.KotlinLogging
import org.breizhcamp.video.uploader.event.domain.Event
import org.breizhcamp.video.uploader.shared.config.YoutubeAuthConfig.Companion.YT_USER_ID
import org.breizhcamp.video.uploader.video.domain.VideoInfo
import org.springframework.stereotype.Repository

@Repository
class YoutubeLibrary(
    private val httpTransport: HttpTransport,
    private val jacksonFactory: JacksonFactory,
    private val ytAuthFlow: GoogleAuthorizationCodeFlow,
) {
    private val logger = KotlinLogging.logger { }

    fun getChannels(): List<Channel> = createYoutubeClient()
        .channels()
        ?.list("id,snippet")
        ?.setMine(true)
        ?.execute()
        ?.items
        ?: emptyList()

    fun getPlaylists(channelId: String): List<Playlist> {
        return createYoutubeClient()
            .playlists()
            ?.list("id,snippet")
            ?.setChannelId(channelId)
            ?.setMaxResults(50L)
            ?.execute()
            ?.items
            ?: emptyList()
    }

    fun insertVideo(videoInfo: VideoInfo, event: Event): YouTube.Videos.Insert? {
        var speakers = event.speakers
        if (speakers!!.endsWith(", ")) speakers = speakers.substring(0, speakers.length - 2)

        val video = Video().apply {
            status = VideoStatus().apply {
                privacyStatus = "unlisted"
            }
            snippet = VideoSnippet().apply {
                title = makeTitle(event, speakers)
            }
        }
        val videoContent = FileContent("video/*", videoInfo.path.toFile())

        return createYoutubeClient().videos()?.insert("snippet,status", video, videoContent)
    }

    fun insertInPlaylist(videoInfo: VideoInfo) {
        logger.info { "[${videoInfo.eventId}] Setting video in playlist [${videoInfo.playlistId}]" }
        val item = PlaylistItem().apply {
            snippet = PlaylistItemSnippet().apply {
                playlistId = requireNotNull(videoInfo.playlistId)
                resourceId = ResourceId().apply {
                    kind = "youtube#video"
                    videoId = videoInfo.youtubeId
                }
            }
        }
        createYoutubeClient().playlistItems()?.insert("snippet,status", item)?.execute()
        logger.info { "[${videoInfo.eventId}] Video set in playlist" }
    }

    fun uploadThumbnail(videoInfo: VideoInfo) {
        logger.info { "[${videoInfo.eventId}] Uploading and defining thumbnail [${videoInfo.thumbnail}]" }
        val thumb = FileContent("image/png", videoInfo.thumbnail!!.toFile())
        createYoutubeClient()
            .thumbnails()
            ?.set(videoInfo.youtubeId, thumb)
            ?.execute()
        logger.info { "[${videoInfo.eventId}] Thumbnail set" }
    }

    private fun createYoutubeClient() = YouTube.Builder(
        httpTransport, jacksonFactory,
        getCurrentCred() ?: throw IllegalStateException("Not connected")
    )
        .setApplicationName("yt-uploader/1.0")
        .build()

    private fun getCurrentCred(): Credential? {
        return ytAuthFlow.loadCredential(YT_USER_ID)?.takeIf {
            it.expiresInSeconds != null && it.expiresInSeconds >= 10
        }
    }

    fun isConnected(): Boolean = getCurrentCred() != null


    companion object {
        /**
         * Make a title compatible with Youtube : 100 chars with no < or >.
         * https://developers.google.com/youtube/v3/docs/videos#snippet.title
         *
         * @param event    Event detail
         * @param speakers Speakers' name
         * @return Compatible twitter video title
         */
        private fun makeTitle(event: Event, speakers: String): String {
            var name = "[REFACTO] " + event.name
            if (name.length + speakers.length + 3 > 100) {
                name = name.substring(0, 100 - speakers.length - 4) + "…"
            }
            name = name.replace('<', '〈').replace('>', '〉')
            return "$name ($speakers)"
        }
    }
}