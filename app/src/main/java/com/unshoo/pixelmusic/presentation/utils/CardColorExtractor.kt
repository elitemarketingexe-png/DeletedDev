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
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Stable, high-performance, in-memory LRU cached card color extractor ("Card Sleeves").
 * Evaluates album art bitmaps on background IO thread without blocking UI thread
 * or allocating new OkHttp/Coil ImageLoader instances per card.
 */
object CardColorExtractor {
    // Cache up to 150 extracted ARGB colors by image URL to ensure zero lag or stutter during carousel scrolling
    val colorCache = LruCache<String, Int>(150)

    suspend fun extractColorArgb(context: Context, imageUrl: String?): Int? {
        if (imageUrl.isNullOrBlank()) return null
        colorCache.get(imageUrl)?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                val loader = context.imageLoader
                val request = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .allowHardware(false)
                    .size(96)
                    .build()
                val result = loader.execute(request)
                if (result is SuccessResult) {
                    val bmp = (result.drawable as? BitmapDrawable)?.bitmap
                    if (bmp != null && !bmp.isRecycled) {
                        val palette = Palette.from(bmp).generate()
                        val swatch = palette.vibrantSwatch
                            ?: palette.lightVibrantSwatch
                            ?: palette.darkVibrantSwatch
                            ?: palette.dominantSwatch
                            ?: palette.mutedSwatch
                            ?: palette.lightMutedSwatch
                            ?: palette.darkMutedSwatch
                        if (swatch != null) {
                            val rgb = swatch.rgb
                            colorCache.put(imageUrl, rgb)
                            return@withContext rgb
                        }
                    }
                }
                null
            } catch (e: Exception) {
                null
            }
        }
    }
}

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
            val argb = CardColorExtractor.extractColorArgb(context, imageUrl)
            if (argb != null) {
                targetColor = lerp(baseColor, Color(argb), blendFraction)
            }
        }
    }

    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "card_dominant_color_${imageUrl}"
    )

    return animatedColor
}
