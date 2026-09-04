package com.unshoo.pixelmusic.data.remote.youtube

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

@UnstableApi
class ExoCache(
    private val context: Context,
    private val userPreferencesRepository: com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository? = null
) {
    private val cacheDir = File(context.cacheDir, Constants.ExoPlayer.Cache.NAME)
    val cache: SimpleCache by lazy {
        val configuredSize = try {
            userPreferencesRepository?.let { repo ->
                runBlocking {
                    withTimeoutOrNull(500L) {
                        repo.exoCacheSizeBytesFlow.first()
                    }
                }
            } ?: Constants.ExoPlayer.Cache.SIZE
        } catch (_: Throwable) {
            Constants.ExoPlayer.Cache.SIZE
        }
        val cacheEvictor = LeastRecentlyUsedCacheEvictor(configuredSize)
        SimpleCache(
            cacheDir,
            cacheEvictor,
            databaseProvider
        )
    }

    fun clear() {
        SimpleCache.delete(cacheDir, databaseProvider)
    }

    fun release() {
        cache.release()
    }

    private val databaseProvider by lazy { StandaloneDatabaseProvider(context) }
}
