package com.unshoo.pixelmusic.presentation.screens.youtube

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.unshoo.pixelmusic.presentation.components.ShimmerBox
import com.unshoo.pixelmusic.presentation.navigation.navigateSafely
import com.unshoo.pixelmusic.presentation.screens.MoodAndGenresButton
import com.unshoo.pixelmusic.presentation.screens.MoodAndGenresButtonHeight
import com.unshoo.pixelmusic.presentation.screens.MoodPalette
import com.unshoo.pixelmusic.presentation.viewmodel.MoodAndGenresViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodAndGenresScreen(
    navController: NavController,
    viewModel: MoodAndGenresViewModel = hiltViewModel(),
) {
    val moodAndGenres by viewModel.moodAndGenres.collectAsState()
    val gridState = rememberLazyGridState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mood & Genres") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 180.dp),
            state = gridState,
            contentPadding = PaddingValues(
                start = 6.dp,
                top = paddingValues.calculateTopPadding(),
                end = 6.dp,
                bottom = paddingValues.calculateBottomPadding() + 120.dp,
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            if (moodAndGenres == null) {
                items(12) {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(MoodAndGenresButtonHeight)
                            .padding(6.dp)
                    )
                }
            } else {
                items(
                    items = moodAndGenres.orEmpty(),
                    key = { item -> "${item.title}:${item.endpoint.browseId}:${item.endpoint.params}" },
                    contentType = { "mood_genres_item" },
                ) { item ->
                    val colorIndex = moodAndGenres.orEmpty().indexOf(item)
                    val color = MoodPalette.getOrElse(colorIndex % MoodPalette.size) { 0xFF6650A4L }
                    MoodAndGenresButton(
                        title = item.title,
                        stripeColor = color,
                        endpoint = item.endpoint,
                        onClick = {
                            val browseId = item.endpoint.browseId
                            val params = item.endpoint.params.orEmpty()
                            navController.navigateSafely("youtube_browse/$browseId?params=$params")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(6.dp)
                    )
                }
            }
        }
    }
}
