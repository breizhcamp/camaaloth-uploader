package org.breizhcamp.video.uploader.dto;

import com.google.api.services.youtube.model.Channel;
import com.google.api.services.youtube.model.Playlist;

import java.util.List;

/**
 * Current channels, selected channel and playlist
 */
public class YoutubeSession {

	/** List of all channel in current logged account */
	private List<Channel> channels;

	/** Selected channel */
	private Channel curChan;

	/** List of all playlist for the curChan */
	private List<Playlist> playlists;

	/** Selected playlist */
	private Playlist curPlaylist;

	public List<Channel> getChannels() {
		return channels;
	}

	public void setChannels(List<Channel> channels) {
		this.channels = channels;
	}

	public Channel getCurChan() {
		return curChan;
	}

	public void setCurChan(Channel curChan) {
		this.curChan = curChan;
	}

	public List<Playlist> getPlaylists() {
		return playlists;
	}

	public void setPlaylists(List<Playlist> playlists) {
		this.playlists = playlists;
	}

	public Playlist getCurPlaylist() {
		return curPlaylist;
	}

	public void setCurPlaylist(Playlist curPlaylist) {
		this.curPlaylist = curPlaylist;
	}
}
