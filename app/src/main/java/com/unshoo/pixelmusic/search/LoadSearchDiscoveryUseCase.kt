package com.unshoo.pixelmusic.search

import androidx.compose.runtime.Immutable
import com.unshoo.pixelmusic.data.repository.SearchDiscoveryRepository
import unshoo.ianshulyadav.pixelmusic.innertube.models.AlbumItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.ArtistItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem
import unshoo.ianshulyadav.pixelmusic.innertube.pages.HomePage
import javax.inject.Inject

class LoadSearchDiscoveryUseCase @Inject constructor(
    private val repository: SearchDiscoveryRepository,
) {
    suspend operator fun invoke(): Result<SearchDiscoveryUiModel> =
        repository.loadDiscovery().map { data ->
            val chartItems = data.chartSections.flatMap { section -> section.items }

            SearchDiscoveryUiModel(
                moodAndGenres = data.moodAndGenres,
                suggestedSongs = data.suggestedSongs.distinctBy { it.id }.take(MaxDiscoveryItems),
                trendingAlbums = (chartItems.filterIsInstance<AlbumItem>() + data.newReleaseAlbums + data.searchedAlbums)
                    .distinctBy { it.id }
                    .take(MaxDiscoveryItems),
                suggestedArtists = data.suggestedArtists.distinctBy { it.id }.take(MaxDiscoveryItems),
            )
        }

    private companion object {
        const val MaxDiscoveryItems = 12
    }
}

@Immutable
data class SearchDiscoveryUiModel(
    val moodAndGenres: List<unshoo.ianshulyadav.pixelmusic.innertube.pages.MoodAndGenres.Item>,
    val suggestedSongs: List<SongItem>,
    val trendingAlbums: List<AlbumItem>,
    val suggestedArtists: List<ArtistItem>,
) {
    val isEmpty: Boolean
        get() = moodAndGenres.isEmpty() && suggestedSongs.isEmpty() && trendingAlbums.isEmpty() && suggestedArtists.isEmpty()
}
