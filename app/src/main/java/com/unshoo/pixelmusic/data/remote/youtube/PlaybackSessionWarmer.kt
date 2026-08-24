package com.unshoo.pixelmusic.data.remote.youtube

import android.content.Context
import androidx.core.net.toUri
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.data.service.player.DualPlayerEngine
import com.unshoo.pixelmusic.di.AppScope
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import unshoo.ianshulyadav.pixelmusic.innertube.NewPipeUtils
import unshoo.ianshulyadav.pixelmusic.innertube.YouTube
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Warms YouTube playback prerequisites off the launch critical path:
 * persisted visitorData, NewPipe player-JS / signature timestamp, and the
 * current snapshot song's stream URL. Safe to call from Application after a
 * short delay or from MusicService — work is coalesced and never blocks UI.
 */
@Singleton
class PlaybackSessionWarmer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @AppScope private val scope: CoroutineScope,
    private val youtubeDatastore: DatastoreRepository,
    private val userPreferences: UserPreferencesRepository,
    private val engine: Lazy<DualPlayerEngine>,
) {
    private val scheduled = AtomicBoolean(false)

    fun scheduleBackgroundWarmup(delayMs: Long = 350L) {
        if (!scheduled.compareAndSet(false, true)) return
        scope.launch(Dispatchers.IO) {
            if (delayMs > 0L) delay(delayMs)
            runCatching { warmSession() }
                .onFailure { Timber.w(it, "PlaybackSessionWarmer: session warm-up failed") }
            runCatching { preResolvePersistedCurrentSong() }
                .onFailure { Timber.w(it, "PlaybackSessionWarmer: snapshot pre-resolve failed") }
        }
    }

    suspend fun warmSession() = withContext(Dispatchers.IO) {
        restoreOrRefreshVisitorData()
        val warmupVideoId = currentSnapshotYoutubeId() ?: FALLBACK_WARMUP_VIDEO_ID
        runCatching { NewPipeUtils.getSignatureTimestamp(warmupVideoId) }
            .onFailure { Timber.w(it, "PlaybackSessionWarmer: NewPipe signature warm-up failed") }
    }

    suspend fun preResolvePersistedCurrentSong() = withContext(Dispatchers.IO) {
        val uri = currentSnapshotCloudUri() ?: return@withContext
        engine.get().resolveCloudUri(uri)
    }

    private suspend fun restoreOrRefreshVisitorData() {
        val persisted = runCatching {
            youtubeDatastore.visitorData.first()
        }.getOrNull().orEmpty()

        if (persisted.isNotBlank() && YouTube.visitorData.isNullOrBlank()) {
            YouTube.visitorData = persisted
        }

        if (!YouTube.visitorData.isNullOrBlank()) return

        val fresh = YouTube.visitorData().getOrNull()?.takeIf { it.isNotBlank() } ?: return
        YouTube.visitorData = fresh
        runCatching {
            youtubeDatastore.save(DatastoreRepository.PreferenceKeys.VISITOR_DATA, fresh)
        }
    }

    private suspend fun currentSnapshotYoutubeId(): String? {
        val uri = currentSnapshotCloudUri()?.toString() ?: return null
        return uri.substringAfter("youtube://").substringBefore('?').takeIf { it.isNotBlank() }
    }

    private suspend fun currentSnapshotCloudUri(): android.net.Uri? {
        val snapshot = runCatching {
            userPreferences.getPlaybackQueueSnapshotOnce()
        }.getOrNull() ?: return null

        val currentItem = if (snapshot.currentMediaId != null) {
            snapshot.items.find { it.mediaId == snapshot.currentMediaId }
        } else {
            snapshot.items.getOrNull(snapshot.currentIndex)
        } ?: return null

        val uriStr = currentItem.uri
        if (uriStr.isBlank()) return null
        val uri = uriStr.toUri()
        val scheme = uri.scheme
        if (scheme == "youtube" || scheme == "telegram" || scheme == "gdrive") return uri
        // Recover youtube:// from a previously persisted googlevideo URL + mediaId.
        if (scheme == "http" || scheme == "https") {
            val fromMediaId = currentItem.mediaId
                .removePrefix("external:")
                .let { id ->
                    when {
                        id.startsWith("youtube_") -> id.removePrefix("youtube_")
                        id.startsWith("youtube://") -> id.removePrefix("youtube://")
                        else -> null
                    }
                }
                ?.substringBefore('?')
                ?.takeIf { it.isNotBlank() }
            val videoId = fromMediaId ?: YoutubeHelper.extractYouTubeVideoId(uriStr)
            if (!videoId.isNullOrBlank()) {
                return "youtube://$videoId".toUri()
            }
        }
        return null
    }

    private companion object {
        // Used only to download/parse the player JS when no snapshot song exists.
        private const val FALLBACK_WARMUP_VIDEO_ID = "dQw4w9WgXcQ"
    }
}
