package com.unshoo.pixelmusic.presentation.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.unshoo.pixelmusic.R
import com.unshoo.pixelmusic.presentation.components.LocalMaterialTheme
import com.unshoo.pixelmusic.presentation.components.ToggleSegmentButton
import com.unshoo.pixelmusic.ui.theme.LocalPixelMusicDarkTheme
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@Composable
fun BottomToggleRow(
    modifier: Modifier,
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    isFavoriteProvider: () -> Boolean,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    activeColorMain: Color = MaterialTheme.colorScheme.primaryFixed,
    activeColorSecondary: Color = MaterialTheme.colorScheme.secondaryFixed,
    activeColorTertiary: Color = MaterialTheme.colorScheme.tertiaryFixed,
    onActiveColorMain: Color = MaterialTheme.colorScheme.onPrimaryFixed,
    onActiveColorSecondary: Color = MaterialTheme.colorScheme.onSecondaryFixed,
    onActiveColorTertiary: Color = MaterialTheme.colorScheme.onTertiaryFixed,
    inactiveColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    inactiveContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    containerColor: Color? = null
) {
    val isFavorite = isFavoriteProvider()
    val rowCorners = 60.dp
    val resolvedContainerColor = containerColor ?: MaterialTheme.colorScheme.surfaceContainerHigh

    Box(
        modifier = modifier.background(
            color = resolvedContainerColor,
            shape = AbsoluteSmoothCornerShape(
                cornerRadiusBL = rowCorners,
                smoothnessAsPercentTR = 60,
                cornerRadiusBR = rowCorners,
                smoothnessAsPercentBL = 60,
                cornerRadiusTL = rowCorners,
                smoothnessAsPercentBR = 60,
                cornerRadiusTR = rowCorners,
                smoothnessAsPercentTL = 60
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .clip(
                    AbsoluteSmoothCornerShape(
                        cornerRadiusBL = rowCorners,
                        smoothnessAsPercentTR = 60,
                        cornerRadiusBR = rowCorners,
                        smoothnessAsPercentBL = 60,
                        cornerRadiusTL = rowCorners,
                        smoothnessAsPercentBR = 60,
                        cornerRadiusTR = rowCorners,
                        smoothnessAsPercentTL = 60
                    )
                )
                .background(Color.Transparent),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val commonModifier = Modifier.weight(1f)

            ToggleSegmentButton(
                modifier = commonModifier,
                active = isShuffleEnabled,
                activeColor = activeColorMain,
                activeCornerRadius = rowCorners,
                activeContentColor = onActiveColorMain,
                inactiveColor = inactiveColor,
                inactiveContentColor = inactiveContentColor,
                onClick = onShuffleToggle,
                iconId = R.drawable.rounded_shuffle_24,
                contentDesc = "Shuffle"
            )
            val repeatActive = repeatMode != Player.REPEAT_MODE_OFF
            val repeatIcon = when (repeatMode) {
                Player.REPEAT_MODE_ONE -> R.drawable.rounded_repeat_one_24
                Player.REPEAT_MODE_ALL -> R.drawable.rounded_repeat_24
                else -> R.drawable.rounded_repeat_24
            }
            ToggleSegmentButton(
                modifier = commonModifier,
                active = repeatActive,
                activeColor = activeColorSecondary,
                activeCornerRadius = rowCorners,
                activeContentColor = onActiveColorSecondary,
                inactiveColor = inactiveColor,
                inactiveContentColor = inactiveContentColor,
                onClick = onRepeatToggle,
                iconId = repeatIcon,
                contentDesc = "Repeat"
            )
            ToggleSegmentButton(
                modifier = commonModifier,
                active = isFavorite,
                activeColor = activeColorTertiary,
                activeCornerRadius = rowCorners,
                activeContentColor = onActiveColorTertiary,
                inactiveColor = inactiveColor,
                inactiveContentColor = inactiveContentColor,
                onClick = onFavoriteToggle,
                iconId = if (isFavorite) R.drawable.round_favorite_24 else R.drawable.rounded_favorite_24,
                contentDesc = "Favorite"
            )
        }
    }
}
