package com.unshoo.pixelmusic.data

import com.unshoo.pixelmusic.shared.WearLibraryItem

data class WearLocalQueueState(
    val items: List<WearLibraryItem> = emptyList(),
    val currentIndex: Int = -1,
)
