package com.unshoo.pixelmusic.presentation.utils

import android.content.Context
import android.content.SharedPreferences
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Two-tier card color extractor:
 *  L1 — In-memory LRU (instant, 150 entries)
 *  L2 — SharedPreferences disk cache (zero-cost on app relaunch, survives process death)
 *
 * Color extraction on a 48×48 sub-sampled bitmap via Palette API reduces pixel-scan
 * cost by 4× vs the previous 96×96. A 150 ms debounce in the Composable helper
 * cancels work for cards that are scrolled past before the delay elapses.
 */
object CardColorExtractor {

    // L1 — hot in-memory LRU: survives configuration changes, zero disk I/O
    val colorCache = LruCache<String, Int>(150)

    // SharedPreferences handle — lazily initialized per process
    @Volatile
    private var diskPrefs: SharedPreferences? = null

    /** Must be called once (e.g. from Application.onCreate) before Composables run. */
    fun init(context: Context) {
        if (diskPrefs == null) {
            val appCtx = context.applicationContext
            diskPrefs = appCtx.getSharedPreferences("card_color_disk_cache", Context.MODE_PRIVATE)
            // Warm L1 from disk off the main thread so disk I/O never blocks UI frame posting
            CoroutineScope(Dispatchers.IO).launch {
                val allEntries = diskPrefs?.all ?: return@launch
                allEntries.forEach { (key, value) ->
                    if (value is Int) colorCache.put(key, value)
                }
            }
        }
    }

    /**
     * Returns the ARGB int for [imageUrl], hitting L1 → L2 → Palette API in that order.
     * Writes back through both tiers after extraction. Must be called on a background dispatcher.
     */
    suspend fun extractColorArgb(context: Context, imageUrl: String?): Int? {
        if (imageUrl.isNullOrBlank()) return null

        // L1 hit
        colorCache.get(imageUrl)?.let { return it }

        // L2 hit (disk)
        val prefs = diskPrefs ?: run {
            init(context)
            diskPrefs
        }
        prefs?.getInt(imageUrl, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }?.let { cached ->
            colorCache.put(imageUrl, cached) // promote to L1
            return cached
        }

        // Full extraction — 48×48 sub-sample via Coil (IO) + Palette API (Default)
        return try {
            val loader = context.imageLoader
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .allowHardware(false)
                // 48×48 is sufficient for color extraction and 4× faster than 96×96
                .size(48)
                .precision(Precision.INEXACT)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build()

            val result = withContext(Dispatchers.IO) { loader.execute(request) }
            if (result is SuccessResult) {
                val bmp = (result.drawable as? BitmapDrawable)?.bitmap
                if (bmp != null && !bmp.isRecycled) {
                    val rgb = withContext(Dispatchers.Default) {
                        val palette = Palette.from(bmp)
                            .maximumColorCount(8) // fewer swatches = faster scan
                            .generate()
                        (palette.vibrantSwatch
                            ?: palette.lightVibrantSwatch
                            ?: palette.darkVibrantSwatch
                            ?: palette.dominantSwatch
                            ?: palette.mutedSwatch
                            ?: palette.lightMutedSwatch
                            ?: palette.darkMutedSwatch)?.rgb
                    }
                    if (rgb != null) {
                        // Write-through to both tiers
                        colorCache.put(imageUrl, rgb)
                        withContext(Dispatchers.IO) {
                            prefs?.edit()?.putInt(imageUrl, rgb)?.apply()
                        }
                        return rgb
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Composable helper that returns a smoothly animated dominant card color.
 *
 * Key optimisations:
 * • Reads L1 synchronously on the first frame — no flicker for cached cards.
 * • 150 ms debounce: if the card is scrolled past quickly, the coroutine is cancelled
 *   before any Coil/Palette work is triggered.
 */
@Composable
fun rememberDominantCardColor(
    imageUrl: String?,
    baseColor: Color,
    isDarkTheme: Boolean,
    darkBlendFraction: Float = 0.35f,
    lightBlendFraction: Float = 0.50f
): Color {
    val context = LocalContext.current
    val blendFraction = if (isDarkTheme) darkBlendFraction else lightBlendFraction

    // Synchronous L1 read — populated from disk on init(), so nearly always a hit
    val initialArgb = remember(imageUrl) {
        if (!imageUrl.isNullOrBlank()) CardColorExtractor.colorCache.get(imageUrl) else null
    }

    var targetColor by remember(imageUrl, baseColor, isDarkTheme) {
        mutableStateOf(
            if (initialArgb != null) lerp(baseColor, Color(initialArgb), blendFraction) else baseColor
        )
    }

    LaunchedEffect(imageUrl, baseColor, isDarkTheme) {
        if (!imageUrl.isNullOrBlank() && initialArgb == null) {
            // 150 ms debounce — cancels automatically if the card leaves the screen
            delay(150)
            val argb = CardColorExtractor.extractColorArgb(context, imageUrl)
            if (argb != null) {
                targetColor = lerp(baseColor, Color(argb), blendFraction)
            }
        }
    }

    // Fast path: if color is already in L1 memory cache, return static targetColor directly.
    // This avoids registering an active Compose animation spec for pre-cached cards, saving recomposition overhead during scroll.
    if (initialArgb != null) {
        return targetColor
    }

    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "card_dominant_color"
    )

    return animatedColor
}
