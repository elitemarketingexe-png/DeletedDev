package unshoo.ianshulyadav.pixelmusic.innertube.pages

import unshoo.ianshulyadav.pixelmusic.innertube.models.AlbumItem

data class ExplorePage(
    val chips: List<HomePage.Chip>?,
    val sections: List<HomePage.Section>,
    val newReleaseAlbums: List<AlbumItem>,
    val moodAndGenres: List<MoodAndGenres.Item>,
)
