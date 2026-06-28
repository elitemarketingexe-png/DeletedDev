package com.unshoo.pixelmusic.data.remote.youtube

import com.unshoo.pixelmusic.data.model.youtube.Playlist
import com.unshoo.pixelmusic.data.model.youtube.PlaylistInfo
import com.unshoo.pixelmusic.data.model.youtube.UmihiSettings

/**
 * Remote data source for fetching YouTube playlist metadata and their songs
 * directly from the YouTube Music API via [YoutubeRequestHelper] + [YoutubeHelper].
 *
 * - [retrieveAll] fetches the list of playlists visible in the authenticated user's library.
 * - [retrieveOne] fetches the full song list for a single playlist.
 *
 * Requires a valid authenticated [UmihiSettings] (cookies) passed from [DatastoreRepository].
 */
class YoutubePlaylistDataSource {

    /**
     * Returns all playlist info entries visible in the user's YouTube Music library.
     */
    fun retrieveAll(settings: UmihiSettings): List<PlaylistInfo> {
        return YoutubeHelper.extractPlaylists(
            YoutubeRequestHelper.browse(
                Constants.YoutubeApi.Browse.PLAYLIST_BROWSE_ID,
                settings
            ),
            settings
        )
    }

    /**
     * Fetches and returns a [Playlist] populated with its full song list from YouTube.
     * The "liked_songs" playlist id is translated to YouTube's internal "LM" browse id.
     */
    fun retrieveOne(playlist: Playlist, settings: UmihiSettings): Playlist {
        val remoteId = if (playlist.info.id == "liked_songs") "LM" else playlist.info.id
        return playlist.copy(
            songs = YoutubeHelper.extractSongList(
                YoutubeRequestHelper.browse(
                    remoteId,
                    settings
                ),
                settings
            )
        )
    }
}
