package com.unshoo.pixelmusic.presentation.components

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.unshoo.pixelmusic.BottomNavItem
import com.unshoo.pixelmusic.data.preferences.NavBarStyle
import com.unshoo.pixelmusic.presentation.components.scoped.CustomNavigationBarItem
import com.unshoo.pixelmusic.presentation.navigation.Screen
import com.unshoo.pixelmusic.presentation.navigation.navigateToTopLevelSafely
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal val NavBarContentHeight = 76.dp
internal val NavBarCompactContentHeight = 64.dp
internal val NavBarContentHeightFullWidth = NavBarContentHeight
private val FloatingPillContentHeight = 76.dp
private val MainScreenBottomGradientExtraHeight = 76.dp + MiniPlayerBottomSpacer + 8.dp
internal val MaxNavigationBarBottomInset = 96.dp

internal fun sanitizeNavigationBarBottomInset(systemNavBarInset: Dp): Dp {
    if (!systemNavBarInset.value.isFinite()) return 0.dp
    return systemNavBarInset.coerceIn(0.dp, MaxNavigationBarBottomInset)
}

internal fun calculatePlayerSheetCollapsedTargetY(
    containerHeightPx: Float,
    collapsedContentHeightPx: Float,
    bottomMarginPx: Float,
    bottomSpacerPx: Float
): Float {
    val safeContainerHeightPx = containerHeightPx.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    val safeCollapsedContentHeightPx = collapsedContentHeightPx.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    val safeBottomMarginPx = bottomMarginPx.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    val safeBottomSpacerPx = bottomSpacerPx.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f
    val maxTargetY = (safeContainerHeightPx - safeCollapsedContentHeightPx).coerceAtLeast(0f)

    return (safeContainerHeightPx - safeCollapsedContentHeightPx - safeBottomMarginPx - safeBottomSpacerPx)
        .coerceIn(0f, maxTargetY)
}

internal fun resolveNavBarContentHeight(compactMode: Boolean, heightOffset: Dp = 0.dp): Dp =
    (if (compactMode) NavBarCompactContentHeight else NavBarContentHeight) + heightOffset

internal fun resolveNavBarContentHeight(navBarStyle: String, compactMode: Boolean, heightOffset: Dp = 0.dp): Dp =
    when (navBarStyle) {
        NavBarStyle.FLOATING_PILL -> FloatingPillContentHeight + heightOffset
        else -> resolveNavBarContentHeight(compactMode, heightOffset)
    }

internal fun resolveMainScreenBottomGradientHeight(compactMode: Boolean, heightOffset: Dp = 0.dp): Dp =
    resolveNavBarContentHeight(compactMode, heightOffset) + MainScreenBottomGradientExtraHeight

internal fun resolveNavBarSurfaceHeight(
    navBarStyle: String,
    systemNavBarInset: Dp,
    compactMode: Boolean,
    heightOffset: Dp = 0.dp
): Dp {
    val contentHeight = resolveNavBarContentHeight(navBarStyle, compactMode, heightOffset)
    return if (navBarStyle == NavBarStyle.FULL_WIDTH) {
        contentHeight + systemNavBarInset
    } else {
        contentHeight
    }
}

internal fun resolveNavBarOccupiedHeight(
    systemNavBarInset: Dp,
    compactMode: Boolean
): Dp = resolveNavBarOccupiedHeight(NavBarStyle.DEFAULT, systemNavBarInset, compactMode, 0.dp)

internal fun resolveNavBarOccupiedHeight(
    navBarStyle: String,
    systemNavBarInset: Dp,
    compactMode: Boolean,
    heightOffset: Dp = 0.dp
): Dp {
    val contentHeight = resolveNavBarContentHeight(navBarStyle, compactMode, heightOffset)
    return if (navBarStyle == NavBarStyle.FULL_WIDTH) {
        contentHeight + systemNavBarInset
    } else {
        val bottomMargin = if (systemNavBarInset > 0.dp) systemNavBarInset else 14.dp
        contentHeight + bottomMargin
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveFloatingPillNavigationBar(
    navController: NavHostController,
    navItems: ImmutableList<BottomNavItem>,
    currentRoute: String?,
    modifier: Modifier = Modifier,
    onSearchIconDoubleTap: () -> Unit = {}
) {
    val latestCurrentRoute by rememberUpdatedState(currentRoute)
    val latestNavigationEnabled by rememberUpdatedState(currentRoute != null)
    var lastSearchTapTimestamp by remember { mutableStateOf(0L) }

    val haptic = LocalHapticFeedback.current
    val configuration = LocalConfiguration.current
    val fontScale = LocalDensity.current.fontScale
    val screenWidth = configuration.screenWidthDp

    val searchItem = navItems.find { it.screen.route == Screen.Search.route }
    val mainItems = navItems.filter { it.screen.route != Screen.Search.route }

    val isLargeFont = fontScale > 1.25f
    val isCompactScreen = screenWidth < 400
    val shouldHideLabel = isLargeFont || (isCompactScreen && mainItems.size > 3)

    val selectedIndex = mainItems.indexOfFirst { it.screen.route == latestCurrentRoute }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Main navigation floating toolbar — M3 Expressive pill container
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(64.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                mainItems.forEachIndexed { index, item ->
                    val isSelected = selectedIndex == index

                    val itemWeight by animateFloatAsState(
                        targetValue = if (isSelected && !shouldHideLabel) 2.2f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "pill_item_weight_$index"
                    )

                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (latestNavigationEnabled && latestCurrentRoute != item.screen.route) {
                                navController.navigateToTopLevelSafely(item.screen.route)
                            }
                        },
                        modifier = Modifier
                            .weight(itemWeight)
                            .height(56.dp),
                        shape = CircleShape,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            Color.Transparent
                        },
                        contentColor = if (isSelected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp)
                        ) {
                            val iconRes = if (isSelected && item.selectedIconResId != null && item.selectedIconResId != 0) {
                                item.selectedIconResId
                            } else {
                                item.iconResId
                            }

                            Icon(
                                painter = painterResource(id = iconRes),
                                contentDescription = item.label,
                                modifier = Modifier.size(24.dp)
                            )

                            AnimatedVisibility(
                                visible = isSelected && !shouldHideLabel,
                                enter = fadeIn(animationSpec = tween(200)) + expandHorizontally(expandFrom = Alignment.Start),
                                exit = fadeOut(animationSpec = tween(150)) + shrinkHorizontally(shrinkTowards = Alignment.Start)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = item.label,
                                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
                                        maxLines = 1,
                                        fontWeight = FontWeight.Bold,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Disconnected Search FAB — M3 Expressive floating action button
        if (searchItem != null) {
            val isSearchSelected = latestCurrentRoute == searchItem.screen.route
            val searchIconRes = if (isSearchSelected && searchItem.selectedIconResId != null && searchItem.selectedIconResId != 0) {
                searchItem.selectedIconResId
            } else {
                searchItem.iconResId
            }

            Surface(
                onClick = {
                    if (!latestNavigationEnabled) return@Surface

                    val now = SystemClock.elapsedRealtime()
                    val isDoubleTap = now - lastSearchTapTimestamp <= 350L
                    lastSearchTapTimestamp = now

                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                    if (!isSearchSelected) {
                        navController.navigateToTopLevelSafely(searchItem.screen.route)
                    } else if (isDoubleTap) {
                        lastSearchTapTimestamp = 0L
                        onSearchIconDoubleTap()
                    }
                },
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = if (isSearchSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                contentColor = if (isSearchSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                shadowElevation = 4.dp
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        painter = painterResource(id = searchIconRes),
                        contentDescription = searchItem.label,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerInternalNavigationItemsRow(
    navController: NavHostController,
    navItems: ImmutableList<BottomNavItem>,
    currentRoute: String?,
    modifier: Modifier = Modifier,
    navBarStyle: String,
    compactMode: Boolean,
    bottomBarPadding: Dp,
    onSearchIconDoubleTap: () -> Unit
) {
    val navBarInsetPadding = sanitizeNavigationBarBottomInset(
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    )
    val innerRowPadding = (navBarInsetPadding - bottomBarPadding).coerceAtLeast(0.dp)
    val latestCurrentRoute by rememberUpdatedState(currentRoute)
    val latestOnSearchIconDoubleTap by rememberUpdatedState(onSearchIconDoubleTap)
    val latestNavigationEnabled by rememberUpdatedState(currentRoute != null)

    val rowModifier = if (navBarStyle == NavBarStyle.FULL_WIDTH) {
        modifier
            .fillMaxWidth()
            .padding(top = 0.dp, bottom = innerRowPadding, start = 12.dp, end = 12.dp)
    } else {
        modifier
            .padding(start = 10.dp, end = 10.dp, bottom = innerRowPadding)
            .fillMaxWidth()
    }
    Row(
        modifier = rowModifier,
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val scope = rememberCoroutineScope()
        var lastSearchTapTimestamp by remember { mutableStateOf(0L) }
        navItems.forEach { item ->
            val isSelected = currentRoute != null && currentRoute == item.screen.route
            val selectedColor = MaterialTheme.colorScheme.primary
            val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
            val indicatorColorFromTheme = MaterialTheme.colorScheme.secondaryContainer

            val iconPainterResId = if (isSelected && item.selectedIconResId != null && item.selectedIconResId != 0) {
                item.selectedIconResId
            } else {
                item.iconResId
            }
            val iconLambda: @Composable () -> Unit = remember(iconPainterResId, item.label) {
                {
                    Icon(
                        painter = painterResource(id = iconPainterResId),
                        contentDescription = item.label
                    )
                }
            }
            val selectedIconLambda: @Composable () -> Unit = remember(iconPainterResId, item.label) {
                {
                    Icon(
                        painter = painterResource(id = iconPainterResId),
                        contentDescription = item.label
                    )
                }
            }
            val labelLambda: (@Composable () -> Unit)? = if (compactMode) {
                null
            } else {
                remember(item.label) {
                    { Text(item.label) }
                }
            }
            val onClickLambda: () -> Unit = remember(item.screen.route, navController, scope) {
                click@{
                    if (!latestNavigationEnabled) {
                        lastSearchTapTimestamp = 0L
                        return@click
                    }

                    val itemRoute = item.screen.route
                    val isSearchTab = itemRoute == Screen.Search.route
                    val isAlreadySelected = latestCurrentRoute == itemRoute

                    if (isSearchTab) {
                        val now = SystemClock.elapsedRealtime()
                        val isDoubleTap = now - lastSearchTapTimestamp <= 350L
                        lastSearchTapTimestamp = now

                        if (!isAlreadySelected) {
                            if (!navController.navigateToTopLevelSafely(itemRoute)) {
                                lastSearchTapTimestamp = 0L
                                return@click
                            }
                        }

                        if (isDoubleTap) {
                            lastSearchTapTimestamp = 0L
                            if (isAlreadySelected) {
                                latestOnSearchIconDoubleTap()
                            } else {
                                scope.launch {
                                    delay(160L)
                                    latestOnSearchIconDoubleTap()
                                }
                            }
                        }
                    } else if (!isAlreadySelected) {
                        lastSearchTapTimestamp = 0L
                        navController.navigateToTopLevelSafely(itemRoute)
                    } else {
                        lastSearchTapTimestamp = 0L
                    }
                }
            }
            CustomNavigationBarItem(
                modifier = Modifier.weight(1f),
                selected = isSelected,
                onClick = onClickLambda,
                enabled = currentRoute != null,
                compactMode = compactMode,
                icon = iconLambda,
                selectedIcon = selectedIconLambda,
                label = labelLambda,
                contentDescription = item.label,
                alwaysShowLabel = true,
                selectedIconColor = selectedColor,
                unselectedIconColor = unselectedColor,
                selectedTextColor = selectedColor,
                unselectedTextColor = unselectedColor,
                indicatorColor = indicatorColorFromTheme
            )
        }
    }
}

@Composable
fun PlayerInternalNavigationBar(
    navController: NavHostController,
    navItems: ImmutableList<BottomNavItem>,
    currentRoute: String?,
    modifier: Modifier = Modifier,
    navBarStyle: String,
    compactMode: Boolean,
    bottomBarPadding: Dp = 0.dp,
    onSearchIconDoubleTap: () -> Unit = {}
) {
    if (navBarStyle == NavBarStyle.FLOATING_PILL) {
        ExpressiveFloatingPillNavigationBar(
            navController = navController,
            navItems = navItems,
            currentRoute = currentRoute,
            modifier = modifier,
            onSearchIconDoubleTap = onSearchIconDoubleTap
        )
    } else {
        PlayerInternalNavigationItemsRow(
            navController = navController,
            navItems = navItems,
            currentRoute = currentRoute,
            navBarStyle = navBarStyle,
            compactMode = compactMode,
            bottomBarPadding = bottomBarPadding,
            onSearchIconDoubleTap = onSearchIconDoubleTap,
            modifier = modifier
        )
    }
}
