package com.unshoo.pixelmusic.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive shape scale (May 2025 update):
 *  - Large increased 16dp → **20dp**
 *  - Extra large increased 28dp → **32dp**
 *  - New **48dp** Extra Extra Large token (previously capped at XL)
 *  - Full replaces "50% of component size"
 *
 * Components that consume `MaterialTheme.shapes` (cards, sheets, dialogs,
 * navigation containers) now resolve the full M3E radius scale automatically.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(32.dp),
    extraExtraLarge = RoundedCornerShape(48.dp)
)
