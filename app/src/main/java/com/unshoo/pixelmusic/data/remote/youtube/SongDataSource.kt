package com.unshoo.pixelmusic.data.remote.youtube

import com.unshoo.pixelmusic.data.model.youtube.Song

class SongDataSource {
    fun getSongInfo(songId: String): Song {
        return YoutubeHelper.extractSongInfo(
            YoutubeRequestHelper.getPlayerInfo(songId)
        )
    }

    fun search(query: String): List<Song> {
        return YoutubeHelper.extractSearchResults(
            YoutubeRequestHelper.search(query)
        )
    }

    fun getRelatedSongs(videoId: String): List<Song> {
        return YoutubeHelper.extractRelatedSongs(
            YoutubeRequestHelper.nextUp(videoId)
        )
    }
}
