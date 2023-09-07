package org.breizhcamp.video.uploader.services

import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse
import com.google.api.services.youtube.model.Channel
import com.google.api.services.youtube.model.Playlist
import org.breizhcamp.video.uploader.dto.VideoInfo
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Service
@Profile("e2e")
@Primary
class YoutubeSrvE2E: YoutubeSrv() {
    private var connected: Boolean = false

    override fun getAuthUrl(redirectUrl: String): String {
        return "$redirectUrl?code=1234"
    }

    override fun handleAuth(code: String?, redirectUrl: String?) {
        connected = true
    }

    override fun isConnected(): Boolean {
        return connected
    }

    override fun saveToken(token: GoogleTokenResponse?) {
    }

    override fun getChannels(): MutableList<Channel> {
        return mutableListOf()
    }

    override fun getPlaylists(channelId: String?): MutableList<Playlist> {
        return mutableListOf()
    }

    override fun upload(videoInfo: VideoInfo?) {
    }

    override fun listWaiting(): MutableList<VideoInfo> {
        return mutableListOf()
    }
}