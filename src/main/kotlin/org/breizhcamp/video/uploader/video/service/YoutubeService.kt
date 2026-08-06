package org.breizhcamp.video.uploader.video.service

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse
import com.google.api.client.googleapis.media.MediaHttpUploader
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.breizhcamp.video.uploader.event.service.EventService
import org.breizhcamp.video.uploader.shared.config.YoutubeAuthConfig
import org.breizhcamp.video.uploader.shared.exception.UpdateException
import org.breizhcamp.video.uploader.shared.session.YoutubeSession
import org.breizhcamp.video.uploader.video.domain.VideoInfo
import org.breizhcamp.video.uploader.video.repository.YoutubeLibrary
import org.breizhcamp.video.uploader.web.YoutubeController
import org.springframework.messaging.MessagingException
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import java.io.IOException
import java.math.BigDecimal
import java.math.MathContext
import java.security.GeneralSecurityException
import java.util.concurrent.BlockingDeque
import java.util.concurrent.LinkedBlockingDeque

@Service
class YoutubeService(
    private val videoService: VideoService,
    private val eventService: EventService,
    private val ytAuthFlow: GoogleAuthorizationCodeFlow,
    private val template: SimpMessagingTemplate,
    private val youtubeLibrary: YoutubeLibrary,
    private val ytSession: YoutubeSession,
) {
    private val logger = KotlinLogging.logger { }
    private lateinit var uploader: YtUploader

    @PostConstruct
    fun setUp() {
        uploader = YtUploader()
        uploader.start()
    }

    @PreDestroy
    fun tearDown() {
        uploader.shutdown()
    }

    /**
     * Build the Google consent URL.
     *
     * `prompt=select_account consent` is required: without it Google silently re-approves the
     * account already granted, which prevents switching account and, more importantly, makes it
     * skip the refresh token in its answer.
     */
    fun getAuthUrl(redirectUrl: String): String =
        ytAuthFlow
            .newAuthorizationUrl()
            .setRedirectUri(redirectUrl)
            .setAccessType("offline")
            .set("prompt", "select_account consent")
            .build()

    fun handleAuth(code: String, redirectUrl: String) {
        saveToken(
            ytAuthFlow
                .newTokenRequest(code)
                .setRedirectUri(redirectUrl)
                .execute()
        )
    }

    fun getChannels() = youtubeLibrary.getChannels()

    fun getPlaylistsBy(channelId: String) = youtubeLibrary.getPlaylists(channelId = channelId)

    /**
     * Fill the session with the channels of the connected account, and with the playlists of the
     * channel when the account owns exactly one. Nothing is selected for the user otherwise, an
     * upload would end up on the wrong channel.
     */
    fun loadSession() {
        val channels = youtubeLibrary.getChannels()
        ytSession.channels = channels

        if (channels.size != 1) {
            logger.info { "Account owns ${channels.size} channels, none selected" }
            return
        }
        val channel = channels.first()
        ytSession.currentChannel = channel
        ytSession.playlists = youtubeLibrary.getPlaylists(channelId = channel.id)
    }

    /**
     * Load the session unless it already holds something. The session lives in memory only, so it
     * is empty again after every restart even though the credential is still stored.
     */
    fun loadSessionIfNeeded() {
        if (ytSession.channels != null || !isConnected()) return
        try {
            loadSession()
        } catch (e: Exception) {
            logger.warn(e) { "Unable to load the YouTube session" }
        }
    }

    fun isConnected(): Boolean = youtubeLibrary.isConnected()

    /** True when a credential is stored, even an expired or revoked one */
    fun hasStoredCredential(): Boolean = youtubeLibrary.hasStoredCredential()

    /** Delete the stored credential, the only way out when the token is no longer accepted by Google */
    fun disconnect() {
        logger.info { "Deleting the stored YouTube credential" }
        youtubeLibrary.clearCredential()
    }

    fun saveToken(token: GoogleTokenResponse) {
        if (token.refreshToken == null) {
            //Google only returns a refresh token on the first authorization: keep the stored one
            //instead of overwriting it with null, which would make the session die after an hour.
            ytAuthFlow.loadCredential(YoutubeAuthConfig.YT_USER_ID)?.refreshToken?.let {
                logger.warn { "Google returned no refresh token, keeping the stored one" }
                token.refreshToken = it
            }
        }
        ytAuthFlow.createAndStoreCredential(token, YoutubeAuthConfig.YT_USER_ID)
    }

    /**
     * Upload a video
     *
     * @param videoInfo Video to upload
     */
    fun upload(videoInfo: VideoInfo) {
        uploader.uploadVideo(videoInfo)
    }

    /**
     * @return waiting video to upload
     */
    fun listWaiting(): List<VideoInfo> {
        return uploader.listWaiting()
    }

    /**
     * Youtube uploader thread
     */
    private inner class YtUploader : Thread("YtUploader") {
        /**
         * List of video to upload
         */
        private val videoToUpload: BlockingDeque<VideoInfo> = LinkedBlockingDeque()
        private var running = true
        private val logger = KotlinLogging.logger { }

        fun uploadVideo(videoInfo: VideoInfo) {
            videoToUpload.addLast(videoInfo)
            videoInfo.status = VideoInfo.Status.WAITING
            updateVideo(videoInfo)
        }

        override fun run() {
            var lastUpload: String? = null
            var nbErrors = 0
            try {
                while (running) {
                    val videoInfo = videoToUpload.take()
                    logger.info { "Uploading video: [${videoInfo.path}]" }
                    try {
                        lastUpload = videoInfo.dirName
                        val event = requireNotNull(eventService.findEventBy(id = videoInfo.eventId!!))
                        val insert = youtubeLibrary.insertVideo(videoInfo, event)
                        val uploader = insert?.mediaHttpUploader
                        uploader?.setChunkSize(1024 * 1024 * 50) //10MB in order to have progress info often :p
                        uploader?.setProgressListener { httpUploader: MediaHttpUploader ->
                            try {
                                when (httpUploader.uploadState) {
                                    MediaHttpUploader.UploadState.NOT_STARTED -> logger.info { "[${videoInfo.eventId}] Not started" }

                                    MediaHttpUploader.UploadState.INITIATION_STARTED -> {
                                        logger.info { "[${videoInfo.eventId}] Init started" }
                                        videoInfo.status = VideoInfo.Status.INITIALIZING
                                        updateVideo(videoInfo)
                                    }

                                    MediaHttpUploader.UploadState.INITIATION_COMPLETE -> {
                                        logger.info { "[${videoInfo.eventId}] Init complete" }
                                        videoInfo.status = VideoInfo.Status.IN_PROGRESS
                                        videoInfo.progression = BigDecimal.ZERO
                                        updateVideo(videoInfo)
                                    }

                                    MediaHttpUploader.UploadState.MEDIA_IN_PROGRESS -> {
                                        val progress = httpUploader.getProgress()
                                        logger.info("[${videoInfo.eventId}] Upload in progress: [$progress]")
                                        val percent = BigDecimal(progress * 100, MathContext(3))
                                        videoInfo.status = VideoInfo.Status.IN_PROGRESS
                                        videoInfo.progression = percent
                                        updateVideo(videoInfo)
                                    }

                                    MediaHttpUploader.UploadState.MEDIA_COMPLETE -> logger.info { "[${videoInfo.eventId}] Upload video file complete" }
                                }
                            } catch (e: UpdateException) {
                                //not a critical exception, let the upload continue
                                logger.warn("Cannot send or write update for video [{}]", videoInfo.dirName, e)
                            }
                        }

                        //this call is blocking until video is completely uploaded
                        val insertedVideo = insert?.execute()
                        videoInfo.youtubeId = insertedVideo?.id
                        videoInfo.progression = null

                        //upload thumbnail if available
                        if (videoInfo.thumbnail != null) {
                            videoInfo.status = VideoInfo.Status.THUMBNAIL
                            updateVideo(videoInfo)
                            youtubeLibrary.uploadThumbnail(videoInfo)
                        }
                        youtubeLibrary.insertInPlaylist(videoInfo)
                        videoInfo.status = VideoInfo.Status.DONE
                        updateVideo(videoInfo)
                        logger.info("[{}] Video uploaded, end of process", videoInfo.eventId)
                        nbErrors = 0
                    } catch (e: UpdateException) {
                        logger.error(e) { "Error when uploading [${lastUpload}]" }
                        videoInfo.status = VideoInfo.Status.FAILED
                        try {
                            updateVideo(videoInfo)
                        } catch (ex: UpdateException) {
                            logger.error("Unable to update metadata", ex)
                        }
                        nbErrors++
                        if (nbErrors > 5) {
                            throw RuntimeException("At least 5 videos failed to upload, stopping thread")
                        }
                    } catch (e: GeneralSecurityException) {
                        logger.error("Error when uploading [{}]", lastUpload, e)
                        videoInfo.status = VideoInfo.Status.FAILED
                        try {
                            updateVideo(videoInfo)
                        } catch (ex: UpdateException) {
                            logger.error("Unable to update metadata", ex)
                        }
                        nbErrors++
                        if (nbErrors > 5) {
                            throw RuntimeException("At least 5 videos failed to upload, stopping thread")
                        }
                    } catch (e: IOException) {
                        logger.error("Error when uploading [{}]", lastUpload, e)
                        videoInfo.status = VideoInfo.Status.FAILED
                        try {
                            updateVideo(videoInfo)
                        } catch (ex: UpdateException) {
                            logger.error("Unable to update metadata", ex)
                        }
                        nbErrors++
                        if (nbErrors > 5) {
                            throw RuntimeException("At least 5 videos failed to upload, stopping thread")
                        }
                    }
                }
            } catch (e: InterruptedException) {
                running = false
            }
        }

        fun shutdown() {
            running = false
        }

        fun listWaiting(): List<VideoInfo> {
            return ArrayList(videoToUpload)
        }

        private fun updateVideo(video: VideoInfo) {
            try {
                template.convertAndSend(YoutubeController.VIDEOS_TOPIC, video)
                videoService.updateVideo(video)
            } catch (e: MessagingException) {
                throw UpdateException(e)
            }
        }
    }

}
