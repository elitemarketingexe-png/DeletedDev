package com.unshoo.pixelmusic.data.model.youtube

import androidx.compose.runtime.Immutable
import androidx.media3.common.MediaItem
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.unshoo.pixelmusic.data.remote.youtube.Constants
import kotlinx.serialization.Serializable

@Immutable
data class Playlist(
    @Embedded val info: PlaylistInfo,
    @Relation(
        parentColumn = "id",
        entityColumn = "youtubeId",
        associateBy = Junction(
            value = PlaylistSongCrossRef::class,
            parentColumn = "playlistId",
            entityColumn = "songId"
        )
    )
    val unsortedSongs: List<Song> = listOf(),

    @Relation(
        parentColumn = "id",
        entityColumn = "playlistId"
    )
    val crossRefs: List<PlaylistSongCrossRef> = listOf()
) {
    val songs: List<Song>
        get() = crossRefs.sortedBy { it.position }.mapNotNull { ref ->
            unsortedSongs.find { it.youtubeId == ref.songId }
        }

    val mediaItems: List<MediaItem>
        get() = songs.map { song ->
            song.mediaItem
        }

    val downloaded: Boolean
        get() = songs.all { song -> song.downloaded }
}

@Serializable
@Immutable
@Entity(tableName = Constants.Database.PLAYLISTS_TABLE)
data class PlaylistInfo(
    @PrimaryKey val id: String = "",
    val title: String = "",
    val coverHref: String = "",
    val coverPath: String? = null,
    val lastSyncSongCount: Int = 0,
    val lastSyncTimestamp: Long = 0L,
) {
    val isDownloadedPlaylist: Boolean
        get() = id == Constants.Downloads.DOWNLOADED_PLAYLIST_ID
}

@Entity(
    primaryKeys = ["playlistId", "position"],
    indices = [Index("songId")]
)
data class PlaylistSongCrossRef(
    val playlistId: String,
    val songId: String,
    val position: Int = 0
)
