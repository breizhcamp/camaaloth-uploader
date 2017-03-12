package org.breizhcamp.video.uploader.dto;

import com.google.api.services.youtube.model.Channel;
import com.google.api.services.youtube.model.Playlist;
import lombok.Data;

import java.util.List;

/**
 * Current channels, selected channel and playlist
 */
@Data
public class YoutubeSession {

	/** List of all channel in current logged account */
	private List<Channel> channels;

	/** Selected channel */
	private Channel curChan;

	/** List of all playlist for the curChan */
	private List<Playlist> playlists;

	/** Selected playlist */
	private Playlist curPlaylist;
}
