package com.unshoo.pixelmusic.presentation.components.scoped

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layoutId
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Easing curves para animaciones más suaves y premium (Material 3 Emphasized)
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RowScope.CustomNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    selectedIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compactMode: Boolean = false,
    label: @Composable (() -> Unit)? = null,
    contentDescription: String? = null,
    alwaysShowLabel: Boolean = true,
    selectedIconColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    unselectedIconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedTextColor: Color = MaterialTheme.colorScheme.onSurface,
    unselectedTextColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    indicatorColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    // Colores animados con transición fluida Emphasized
    val iconColor by animateColorAsState(
        targetValue = if (selected) selectedIconColor else unselectedIconColor,
        animationSpec = tween(durationMillis = 250, easing = EmphasizedDecelerate),
        label = "iconColor"
    )

    val textColor by animateColorAsState(
        targetValue = if (selected) selectedTextColor else unselectedTextColor,
        animationSpec = tween(durationMillis = 250, easing = EmphasizedDecelerate),
        label = "textColor"
    )

    // Micro-interacción: escala responsiva y natural
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.15f else 1f,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = 350f
        ),
        label = "iconScale"
    )

    // Se mantiene centrado (sin desplazamiento vertical)
    val iconOffsetY = 0f

    val labelScale by animateFloatAsState(
        targetValue = if (selected) 1.05f else 1f,
        animationSpec = tween(durationMillis = 300, easing = EmphasizedDecelerate),
        label = "labelScale"
    )

    val indicatorProgress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = if (selected) {
            tween(durationMillis = 350, easing = EmphasizedDecelerate)
        } else {
            tween(durationMillis = 200, easing = EmphasizedAccelerate)
        },
        label = "indicatorProgress"
    )

    // Determinar si mostrar la etiqueta
    val showLabel = label != null && (alwaysShowLabel || selected)

    val labelProgress by animateFloatAsState(
        targetValue = if (showLabel) 1f else 0f,
        animationSpec = tween(durationMillis = 300, easing = EmphasizedDecelerate),
        label = "labelProgress"
    )

    val density = LocalDensity.current.density
    val indicatorWidth = 64.dp
    val indicatorHeight = 32.dp
    val iconWidth = 48.dp
    val iconHeight = 24.dp
    val indicatorPadding = 4.dp
    val indicatorShape = RoundedCornerShape(16.dp)

    // Layout principal
    Column(
        modifier = modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(
                onClick = onClick,
                enabled = enabled,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null
            )
            .semantics {
                 if (contentDescription != null) {
                     this.contentDescription = contentDescription
                 }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Container para el ícono con indicador
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(indicatorWidth, indicatorHeight)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = indicatorPadding)
                    .graphicsLayer {
                        scaleX = indicatorProgress
                        scaleY = indicatorProgress
                        alpha = indicatorProgress
                    }
                    .background(
                        color = indicatorColor,
                        shape = indicatorShape
                    )
            )

            // Área clicable del ícono
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(iconWidth, iconHeight)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                        translationY = iconOffsetY * density
                    }

            ) {
                // Ícono
                CompositionLocalProvider(LocalContentColor provides iconColor) {
                    Box(
                        modifier = Modifier.clearAndSetSemantics {
                            if (showLabel) {
                                // La semántica se maneja en el nivel superior
                            }
                        }
                    ) {
                        if (selected) selectedIcon() else icon()
                    }
                }
            }
        }

        // Etiqueta con animación
        if (label != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .graphicsLayer {
                        scaleX = labelScale * labelProgress
                        scaleY = labelScale * labelProgress
                        alpha = labelProgress
                    }
            ) {
                ProvideTextStyle(
                    value = MaterialTheme.typography.labelMedium.copy(
                        color = textColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                ) {
                    label.invoke()
                }
            }
        }
    }
}

