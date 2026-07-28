package unshoo.ianshulyadav.pixelmusic.innertube.pages

import androidx.compose.runtime.Immutable
import unshoo.ianshulyadav.pixelmusic.innertube.models.AlbumItem

@Immutable
data class ExplorePage(
    val chips: List<HomePage.Chip>?,
    val sections: List<HomePage.Section>,
    val newReleaseAlbums: List<AlbumItem>,
)
