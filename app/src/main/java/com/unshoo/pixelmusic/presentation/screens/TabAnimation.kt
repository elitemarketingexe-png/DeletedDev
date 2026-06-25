package com.unshoo.pixelmusic.presentation.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun TabAnimation(
    modifier: Modifier = Modifier,
    index: Int,
    selectedColor: Color = MaterialTheme.colorScheme.primary,
    onSelectedColor: Color = MaterialTheme.colorScheme.onPrimary,
    unselectedColor: Color = MaterialTheme.colorScheme.surface,
    onUnselectedColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
    title: String,
    selectedIndex: Int,
    onClick: () -> Unit,
    transformOrigin: TransformOrigin = TransformOrigin.Center,
    content: @Composable () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    val isSelected = index == selectedIndex
    val scale = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    var hasAnimatedSelectionChange by remember { mutableStateOf(false) }

    val springSpec = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)

    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) selectedColor else unselectedColor,
        animationSpec = tween(durationMillis = 200),
        label = "TabBackground"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) onSelectedColor else onUnselectedColor,
        animationSpec = tween(durationMillis = 200),
        label = "TabContent"
    )

    LaunchedEffect(selectedIndex) {
        if (!hasAnimatedSelectionChange) {
            hasAnimatedSelectionChange = true
            scale.snapTo(1f)
            offsetX.snapTo(0f)
            return@LaunchedEffect
        }

        if (isSelected) {
            launch {
                scale.animateTo(1.08f, animationSpec = springSpec)
                scale.animateTo(1f, animationSpec = springSpec)
            }
        } else {
            scale.snapTo(1f)
        }

        if (!isSelected) {
            val distance = index - selectedIndex
            if (abs(distance) == 1) {
                val direction = if (distance > 0) 1 else -1
                val offsetValue = 14f * direction
                launch {
                    offsetX.animateTo(offsetValue, animationSpec = springSpec)
                    offsetX.animateTo(0f, animationSpec = springSpec)
                }
            } else {
                offsetX.snapTo(0f)
            }
        } else {
            offsetX.snapTo(0f)
        }
    }

    Tab(
        modifier = modifier
            .padding(all = 5.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                translationX = offsetX.value
                this.transformOrigin = transformOrigin
            }
            .clip(CircleShape)
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(50)
            ),
        selected = isSelected,
        text = content,
        onClick = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        selectedContentColor = contentColor,
        unselectedContentColor = contentColor
    )
}
