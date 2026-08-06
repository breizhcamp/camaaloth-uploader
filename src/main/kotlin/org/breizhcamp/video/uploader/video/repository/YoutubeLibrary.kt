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
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Repository
import java.io.IOException

@Repository
class YoutubeLibrary(
    private val httpTransport: HttpTransport,
    private val jacksonFactory: JacksonFactory,
    private val ytAuthFlow: GoogleAuthorizationCodeFlow,
    /** Overridden by the tests to talk to a local server instead of Google */
    @Value("\${youtube.api.root-url:}") private val apiRootUrl: String = "",
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
                description = makeDescription(event)
            }
        }
        val videoContent = FileContent("video/*", videoInfo.path.toFile())

        return createYoutubeClient().videos()?.insert("snippet,status", video, videoContent)
    }

    /**
     * Push the talk description onto a video already online.
     *
     * `videos.update` replaces the whole snippet and demands a title and a category, so the current
     * snippet is read back first and only its description is swapped.
     */
    fun updateDescription(youtubeId: String, description: String) {
        logger.info { "[$youtubeId] Updating the video description" }

        val video = createYoutubeClient()
            .videos()
            ?.list("snippet")
            ?.setId(youtubeId)
            ?.execute()
            ?.items
            ?.firstOrNull()
            ?: throw IllegalStateException("YouTube does not know any video [$youtubeId]")

        video.snippet.description = sanitize(description).take(MAX_DESCRIPTION_LENGTH)
        createYoutubeClient().videos()?.update("snippet", video)?.execute()
        logger.info { "[$youtubeId] Description updated" }
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
        .apply { if (apiRootUrl.isNotBlank()) setRootUrl(apiRootUrl) }
        .build()

    /**
     * Load the stored credential, refreshing the access token when it is about to expire.
     *
     * The refreshed token is written back to the data store by the refresh listener the flow
     * registers on every credential it creates.
     */
    private fun getCurrentCred(): Credential? {
        val credential = ytAuthFlow.loadCredential(YT_USER_ID) ?: return null

        val expiresInSeconds = credential.expiresInSeconds
        if (expiresInSeconds != null && expiresInSeconds >= MIN_TOKEN_VALIDITY_SECONDS) return credential

        if (credential.refreshToken == null) {
            logger.info { "Access token expired and no refresh token available, a new authentication is needed" }
            return null
        }

        return try {
            if (credential.refreshToken()) credential
            else {
                logger.warn { "Google refused to refresh the access token, a new authentication is needed" }
                null
            }
        } catch (e: IOException) {
            logger.warn(e) { "Unable to refresh the access token" }
            null
        }
    }

    fun isConnected(): Boolean = getCurrentCred() != null

    /** True when a credential sits in the data store, whether or not it is still usable */
    fun hasStoredCredential(): Boolean = ytAuthFlow.credentialDataStore?.containsKey(YT_USER_ID) ?: false

    /** Drop the stored credential, so the next authentication starts from scratch */
    fun clearCredential() {
        ytAuthFlow.credentialDataStore?.delete(YT_USER_ID)
        logger.info { "Stored YouTube credential deleted" }
    }


    companion object {
        /** Refresh the access token when it has less than this left, to survive a slow upload start */
        private const val MIN_TOKEN_VALIDITY_SECONDS = 60L

        /** Longest title Youtube accepts, a longer one is rejected with `invalidTitle` */
        private const val MAX_TITLE_LENGTH = 100

        /** Longest description Youtube accepts */
        private const val MAX_DESCRIPTION_LENGTH = 5000

        /**
         * Make a title compatible with Youtube : 100 chars with no < or >.
         * https://developers.google.com/youtube/v3/docs/videos#snippet.title
         *
         * The ellipsis takes a character of its own: the talk name has to give one back, otherwise
         * the title comes out one character too long and Youtube answers `invalidTitle`.
         *
         * @param event    Event detail
         * @param speakers Speakers' name
         * @return Compatible twitter video title
         */
        private fun makeTitle(event: Event, speakers: String): String {
            val name = event.name ?: throw IllegalStateException("Event name is required to create a video title")
            val suffix = " - $speakers"
            val roomForName = MAX_TITLE_LENGTH - suffix.length

            val shortened = if (name.length <= roomForName) name
            else name.take((roomForName - 1).coerceAtLeast(0)) + "…"

            //speakers alone can already blow the budget, so cut whatever is left over
            return sanitize(shortened + suffix).take(MAX_TITLE_LENGTH)
        }

        /** The talk description, or an empty one when the schedule has none */
        private fun makeDescription(event: Event): String =
            sanitize(event.description.orEmpty()).take(MAX_DESCRIPTION_LENGTH)

        /** Youtube rejects angle brackets in both the title and the description */
        private fun sanitize(text: String): String = text.replace('<', '〈').replace('>', '〉')
    }
}