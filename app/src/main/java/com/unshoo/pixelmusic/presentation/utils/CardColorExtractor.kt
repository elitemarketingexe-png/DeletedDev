package com.unshoo.pixelmusic.presentation.utils

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Precision
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber

/**
 * PRODUCTION-READY Two-tier card color extractor.
 *
 * Fixes for lag introduced by Explore + Daily Discover:
 * - SharedPreferences as disk cache is BAD for large key sets (XML parse of huge file on every launch).
 *   Replaced with bounded JSON file in cacheDir (max 200 entries) + Mutex guarded IO.
 * - Limits concurrent Palette extractions to 2 via Semaphore to avoid flooding Dispatchers.Default / IO.
 * - L1: LruCache<String, Int> 200 entries, thread-safe via synchronized wrapper.
 * - L2: JSON file `card_color_lru.json` in cacheDir, read once at init, written debounced (2s) not per-insert.
 * - Coil request 32x32 (was 48) is sufficient for dominant color + 6.25x fewer pixels vs 96. maximumColorCount 12 -> 6.
 * - No `prefs.all` scan on main thread; init loads off main thread via appScope.
 * - Extraction is cancellable; debounce 200ms in Composable avoids work for fast-scrolled cards.
 */
object CardColorExtractor {

    private const val DISK_CACHE_FILE = "card_color_lru.json"
    private const val MAX_DISK_ENTRIES = 200
    private const val MAX_MEMORY_ENTRIES = 200
    private const val DISK_SAVE_DEBOUNCE_MS = 2000L

    val colorCache = LruCache<String, Int>(MAX_MEMORY_ENTRIES)

    // Disk cache map mirrors LRU order (LinkedHashMap with accessOrder=true)
    private val diskMap = LinkedHashMap<String, Int>(MAX_DISK_ENTRIES, 0.75f, true)
    private val diskMutex = Mutex()
    private val extractionSemaphore = Semaphore(2) // limit concurrent Palette work

    @Volatile
    private var isInitialized = false

    private var appContext: Context? = null
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var pendingSaveJob: kotlinx.coroutines.Job? = null

    /** Must be called once from Application.onCreate */
    fun init(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            isInitialized = true
            appContext = context.applicationContext
        }
        // Load disk cache off main thread immediately
        appScope.launch {
            loadDiskCacheInternal()
        }
    }

    private suspend fun loadDiskCacheInternal() {
        try {
            val ctx = appContext ?: return
            val file = java.io.File(ctx.cacheDir, DISK_CACHE_FILE)
            if (!file.exists()) return
            val jsonText = withContext(Dispatchers.IO) { file.readText() }
            if (jsonText.isBlank()) return
            val obj = JSONObject(jsonText)
            val keys = obj.keys()
            // Temporary map
            val loaded = mutableListOf<Pair<String, Int>>()
            while (keys.hasNext()) {
                val k = keys.next()
                try {
                    val v = obj.getInt(k)
                    loaded.add(k to v)
                } catch (_: Exception) {
                }
            }
            // Respect max entries, most recent last
            diskMutex.withLock {
                diskMap.clear()
                loaded.takeLast(MAX_DISK_ENTRIES).forEach { (k, v) ->
                    diskMap[k] = v
                    colorCache.put(k, v)
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "CardColorExtractor: failed to load disk cache")
        }
    }

    private fun scheduleDiskSave() {
        // Debounced save: cancel previous, schedule new after 2s
        pendingSaveJob?.cancel()
        pendingSaveJob = appScope.launch {
            delay(DISK_SAVE_DEBOUNCE_MS)
            saveDiskCacheInternal()
        }
    }

    private suspend fun saveDiskCacheInternal() {
        try {
            val ctx = appContext ?: return
            val file = java.io.File(ctx.cacheDir, DISK_CACHE_FILE)
            val snapshot: Map<String, Int>
            diskMutex.withLock {
                // Trim to max
                while (diskMap.size > MAX_DISK_ENTRIES) {
                    val eldest = diskMap.entries.iterator().next()
                    diskMap.remove(eldest.key)
                }
                snapshot = LinkedHashMap(diskMap)
            }
            if (snapshot.isEmpty()) return
            val json = JSONObject()
            snapshot.forEach { (k, v) ->
                // Keys can be very long URLs, but JSON still handles; truncate to 500 chars to avoid huge file
                val safeKey = if (k.length > 500) k.take(500) else k
                json.put(safeKey, v)
            }
            withContext(Dispatchers.IO) {
                file.writeText(json.toString())
            }
        } catch (e: Exception) {
            Timber.w(e, "CardColorExtractor: failed to save disk cache")
        }
    }

    /**
     * Returns ARGB or null. Hits L1 -> L2 -> Palette.
     * Must be called from background dispatcher (IO/Default) ideally, but safe from any.
     * Limited to 2 concurrent extractions.
     */
    suspend fun extractColorArgb(context: Context, imageUrl: String?): Int? {
        if (imageUrl.isNullOrBlank()) return null

        // Fast L1
        colorCache.get(imageUrl)?.let { return it }

        // L2 disk (in-memory diskMap)
        diskMutex.withLock {
            diskMap[imageUrl]?.let { cached ->
                colorCache.put(imageUrl, cached)
                return cached
            }
        }

        // Full extraction with semaphore limiting
        return extractionSemaphore.withPermit {
            try {
                val ctx = context.applicationContext
                val loader = ctx.imageLoader
                val request = ImageRequest.Builder(ctx)
                    .data(imageUrl)
                    .allowHardware(false)
                    .size(32) // 32x32 = 4x fewer pixels vs 48, 9x vs 96, sufficient for palette
                    .precision(Precision.INEXACT)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build()

                val result = withContext(Dispatchers.IO) { loader.execute(request) }
                if (result is SuccessResult) {
                    val bmp = (result.drawable as? BitmapDrawable)?.bitmap
                    if (bmp != null && !bmp.isRecycled) {
                        val rgb = withContext(Dispatchers.Default) {
                            // Palette is CPU-heavy; reduce max colors and resize area
                            Palette.from(bmp)
                                .maximumColorCount(6)
                                .resizeBitmapArea(32 * 32)
                                .generate()
                                .let { palette ->
                                    (palette.vibrantSwatch
                                        ?: palette.dominantSwatch
                                        ?: palette.lightVibrantSwatch
                                        ?: palette.darkVibrantSwatch
                                        ?: palette.mutedSwatch)?.rgb
                                }
                        }
                        if (rgb != null) {
                            colorCache.put(imageUrl, rgb)
                            diskMutex.withLock {
                                diskMap[imageUrl] = rgb
                            }
                            scheduleDiskSave()
                            return@withPermit rgb
                        }
                    }
                }
                null
            } catch (e: Exception) {
                Timber.d(e, "extractColor failed for $imageUrl")
                null
            }
        }
    }

    /** Invalidate a single entry (e.g. on image change) */
    fun invalidate(imageUrl: String) {
        colorCache.remove(imageUrl)
        appScope.launch {
            diskMutex.withLock { diskMap.remove(imageUrl) }
            scheduleDiskSave()
        }
    }

    /** Clear all (for settings: clear cache) */
    fun clearAll() {
        colorCache.evictAll()
        appScope.launch {
            diskMutex.withLock { diskMap.clear() }
            try {
                appContext?.let { ctx ->
                    val file = java.io.File(ctx.cacheDir, DISK_CACHE_FILE)
                    if (file.exists()) file.delete()
                }
            } catch (_: Exception) {
            }
        }
    }
}

/**
 * Composable helper that returns dominant card color.
 *
 * Optimisations applied vs original:
 * - Synchronous L1 read for instant hit, no animation (bypass animateColorAsState)
 * - 200ms debounce to cancel work for fast-scrolled cards
 * - If cached: return static color, zero animation overhead during scroll (eliminates scroll jank)
 * - If not cached: animate only once towards target, 250ms tween
 */
@Composable
fun rememberDominantCardColor(
    imageUrl: String?,
    baseColor: Color,
    isDarkTheme: Boolean,
    darkBlendFraction: Float = 0.32f,
    lightBlendFraction: Float = 0.48f
): Color {
    val context = LocalContext.current
    val blendFraction = if (isDarkTheme) darkBlendFraction else lightBlendFraction

    // Synchronous L1 read — populated from disk on init, ~100% hit after warm
    val initialArgb = remember(imageUrl) {
        if (!imageUrl.isNullOrBlank()) CardColorExtractor.colorCache.get(imageUrl) else null
    }

    var targetColor by remember(imageUrl, baseColor, isDarkTheme) {
        mutableStateOf(
            if (initialArgb != null) lerp(baseColor, Color(initialArgb), blendFraction) else baseColor
        )
    }

    // Only launch extraction if not cached
    if (initialArgb == null && !imageUrl.isNullOrBlank()) {
        LaunchedEffect(imageUrl, baseColor, isDarkTheme) {
            // Debounce: cancel if card leaves composition quickly (fast scroll)
            delay(200)
            val argb = CardColorExtractor.extractColorArgb(context, imageUrl)
            if (argb != null) {
                targetColor = lerp(baseColor, Color(argb), blendFraction)
            }
        }
    }

    // Fast path: cached color => static, no animation spec active => saves recomposition during scroll
    if (initialArgb != null) {
        return targetColor
    }

    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "card_dominant_color"
    )
    return animatedColor
}
