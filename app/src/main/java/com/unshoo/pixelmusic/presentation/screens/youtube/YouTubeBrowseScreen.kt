package com.unshoo.pixelmusic.presentation.screens.youtube

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.unshoo.pixelmusic.data.remote.youtube.toNativeSong
import com.unshoo.pixelmusic.presentation.navigation.Screen
import com.unshoo.pixelmusic.presentation.navigation.navigateSafely
import com.unshoo.pixelmusic.presentation.screens.AlbumCarouselItem
import com.unshoo.pixelmusic.presentation.screens.ArtistCardItem
import com.unshoo.pixelmusic.presentation.screens.PlaylistCardItem
import com.unshoo.pixelmusic.presentation.screens.SongCardItem
import com.unshoo.pixelmusic.presentation.viewmodel.PlayerViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.YouTubeBrowseViewModel
import com.unshoo.pixelmusic.presentation.components.SearchSkeletonList
import unshoo.ianshulyadav.pixelmusic.innertube.models.AlbumItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.ArtistItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.PlaylistItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeBrowseScreen(
    navController: NavController,
    playerViewModel: PlayerViewModel,
    viewModel: YouTubeBrowseViewModel = hiltViewModel(),
) {
    val browseResult by viewModel.result.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(browseResult?.title.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (browseResult == null) {
                SearchSkeletonList(modifier = Modifier.fillMaxSize())
            } else {
                val sections = browseResult?.items.orEmpty()
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    sections.forEach { section ->
                        if (section.items.isNotEmpty()) {
                            section.title?.let { title ->
                                item {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleLarge,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                    )
                                }
                            }

                            if (section.items.all { it is SongItem }) {
                                val songs = section.items.filterIsInstance<SongItem>()
                                val nativeSongs = songs.map { it.toNativeSong() }
                                item {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(songs) { song ->
                                            val songNative = song.toNativeSong()
                                            Box(modifier = Modifier.width(280.dp).padding(4.dp)) {
                                                SongCardItem(
                                                    song = songNative,
                                                    onClick = {
                                                        playerViewModel.showAndPlaySong(
                                                            song = songNative,
                                                            contextSongs = nativeSongs,
                                                            queueName = section.title ?: "Browse"
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                item {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(section.items) { item ->
                                            Box(modifier = Modifier.padding(4.dp)) {
                                                when (item) {
                                                    is AlbumItem -> {
                                                        AlbumCarouselItem(
                                                            album = item,
                                                            onClick = {
                                                                navController.navigateSafely(Screen.AlbumDetail.createRoute(item.browseId))
                                                            }
                                                        )
                                                    }
                                                    is ArtistItem -> {
                                                        ArtistCardItem(
                                                            artist = item,
                                                            onClick = {
                                                                navController.navigateSafely(Screen.ArtistDetail.createRoute(item.id))
                                                            }
                                                        )
                                                    }
                                                    is PlaylistItem -> {
                                                        PlaylistCardItem(
                                                            playlist = item,
                                                            onClick = {
                                                                navController.navigateSafely(Screen.PlaylistDetail.createRoute(item.id))
                                                            }
                                                        )
                                                    }
                                                    else -> {
                                                        // No-op for other items (like SongItem which is already handled separately)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
