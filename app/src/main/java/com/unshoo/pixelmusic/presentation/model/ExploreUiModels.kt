package com.unshoo.pixelmusic.presentation.model

import androidx.compose.runtime.Immutable
import com.unshoo.pixelmusic.data.model.Song
import unshoo.ianshulyadav.pixelmusic.innertube.models.AlbumItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.ArtistItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.PlaylistItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem

@Immutable
sealed interface ExploreItemUiModel {
    val id: String
    val thumbnail: String?

    @Immutable
    data class SongModel(
        val song: Song,
        val rawItem: SongItem
    ) : ExploreItemUiModel {
        override val id: String get() = song.id
        override val thumbnail: String? get() = song.albumArtUriString
    }

    @Immutable
    data class AlbumModel(
        val album: AlbumItem
    ) : ExploreItemUiModel {
        override val id: String get() = album.browseId
        override val thumbnail: String? get() = album.thumbnail
    }

    @Immutable
    data class PlaylistModel(
        val playlist: PlaylistItem
    ) : ExploreItemUiModel {
        override val id: String get() = playlist.id
        override val thumbnail: String? get() = playlist.thumbnail
    }

    @Immutable
    data class ArtistModel(
        val artist: ArtistItem
    ) : ExploreItemUiModel {
        override val id: String get() = artist.id
        override val thumbnail: String? get() = artist.thumbnail
    }
}

@Immutable
data class ExploreSectionUiModel(
    val id: String,
    val title: String,
    val label: String? = null,
    val items: List<ExploreItemUiModel>
)

@Immutable
data class ExploreChipUiModel(
    val title: String,
    val browseId: String? = null,
    val params: String? = null
)
