package com.unshoo.pixelmusic.presentation.screens

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.presentation.components.subcomps.EnhancedSongListItem

@OptIn(UnstableApi::class)
@Composable
internal fun LibraryPlaybackAwareSongItem(
    song: Song,
    currentSongId: String?,
    isPlaying: Boolean,
    albumArtSize: Dp = 50.dp,
    isSelected: Boolean = false,
    selectionIndex: Int? = null,
    isSelectionMode: Boolean = false,
    onLongPress: () -> Unit = {},
    onMoreOptionsClick: (Song) -> Unit,
    onClick: () -> Unit
) {
    val isCurrentSong = song.id == currentSongId
    val isCurrentlyPlaying = isCurrentSong && isPlaying

    EnhancedSongListItem(
        song = song,
        isPlaying = isCurrentlyPlaying,
        isCurrentSong = isCurrentSong,
        isLoading = false,
        albumArtSize = albumArtSize,
        isSelected = isSelected,
        selectionIndex = selectionIndex,
        isSelectionMode = isSelectionMode,
        onLongPress = onLongPress,
        onMoreOptionsClick = onMoreOptionsClick,
        onClick = onClick
    )
}

