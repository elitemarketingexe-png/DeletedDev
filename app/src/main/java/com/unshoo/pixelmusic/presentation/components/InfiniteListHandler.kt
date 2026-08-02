package com.unshoo.pixelmusic.presentation.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Optimized infinite-scroll / "load more" handler for [LazyListState].
 *
 * Replaces the naive "append a spacer item and detect when it becomes visible"
 * pattern with a reactive one:
 *
 *  - Observes the last **visible** item index through [snapshotFlow] (no polling,
 *    no per-frame recomposition — only invalidates when the visible window moves).
 *  - Fires [onLoadMore] once the user scrolls within [buffer] items of the end.
 *  - **Single-flight guard:** while [onLoadMore] is running, further triggers are
 *    swallowed, so concurrent page fetches (and duplicate rows) are impossible.
 *  - Automatically re-arms when [totalCount] grows, and resets cleanly when the
 *    list state, buffer or enabled flag changes (effect restarts via keys).
 *  - Skips arming entirely for short lists (`totalCount <= buffer`) so it can never
 *    hot-loop on first composition.
 *
 * Usage:
 * ```
 * val listState = rememberLazyListState()
 * LazyColumn(state = listState, ...) { items(...) }
 * InfiniteListHandler(
 *     listState = listState,
 *     totalCount = songs.size,
 *     buffer = 6,
 *     enabled = !isLoading && hasMore,
 *     onLoadMore = { viewModel.loadNextPage() }
 * )
 * ```
 */
@Composable
fun InfiniteListHandler(
    listState: LazyListState,
    totalCount: Int,
    buffer: Int = 6,
    enabled: Boolean = true,
    onLoadMore: suspend () -> Unit,
) {
    if (totalCount <= buffer) return

    var isLoadingMore by remember { mutableStateOf(false) }

    LaunchedEffect(listState, totalCount, buffer, enabled) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .map { lastVisibleIndex -> lastVisibleIndex >= totalCount - buffer - 1 }
            .distinctUntilChanged()
            .collect { shouldLoad ->
                if (shouldLoad && enabled && !isLoadingMore) {
                    isLoadingMore = true
                    try {
                        onLoadMore()
                    } finally {
                        isLoadingMore = false
                    }
                }
            }
    }
}
