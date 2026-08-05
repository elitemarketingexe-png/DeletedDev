package com.unshoo.pixelmusic.presentation.navigation

import DelimiterConfigScreen
import com.unshoo.pixelmusic.presentation.screens.WordDelimiterConfigScreen
import android.annotation.SuppressLint
import androidx.annotation.OptIn
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.res.stringResource
import com.unshoo.pixelmusic.R
import androidx.compose.ui.unit.IntOffset
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.unshoo.pixelmusic.data.preferences.LaunchTab
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.presentation.screens.AlbumDetailScreen
import com.unshoo.pixelmusic.presentation.screens.AccountsScreen
import com.unshoo.pixelmusic.presentation.screens.ArtistDetailScreen
import com.unshoo.pixelmusic.presentation.screens.ArtistSettingsScreen
import com.unshoo.pixelmusic.presentation.screens.DailyMixScreen
import com.unshoo.pixelmusic.presentation.screens.EditTransitionScreen
import com.unshoo.pixelmusic.presentation.screens.EasterEggScreen
import com.unshoo.pixelmusic.presentation.screens.ExperimentalSettingsScreen
import com.unshoo.pixelmusic.presentation.screens.HomeScreen
import com.unshoo.pixelmusic.presentation.screens.ExploreScreen
import com.unshoo.pixelmusic.presentation.screens.LibraryScreen
import com.unshoo.pixelmusic.presentation.screens.MashupScreen
import com.unshoo.pixelmusic.presentation.screens.NavBarCornerRadiusScreen
import com.unshoo.pixelmusic.presentation.screens.PaletteStyleSettingsScreen
import com.unshoo.pixelmusic.presentation.screens.PlaylistDetailScreen
import com.unshoo.pixelmusic.presentation.screens.RecentlyPlayedScreen
import com.unshoo.pixelmusic.presentation.screens.QuickPicksAllScreen
import com.unshoo.pixelmusic.presentation.screens.SmartMixScreen

import com.unshoo.pixelmusic.presentation.screens.AboutScreen
import com.unshoo.pixelmusic.presentation.screens.ArtistAlbumsAllScreen
import com.unshoo.pixelmusic.presentation.screens.ArtistSongsAllScreen
import com.unshoo.pixelmusic.presentation.screens.SearchScreen
import com.unshoo.pixelmusic.presentation.screens.StatsScreen
import com.unshoo.pixelmusic.presentation.screens.SettingsScreen
import com.unshoo.pixelmusic.presentation.screens.SettingsCategoryScreen
import com.unshoo.pixelmusic.presentation.screens.EqualizerScreen
import com.unshoo.pixelmusic.presentation.viewmodel.PlayerViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.PlaylistViewModel
import kotlinx.coroutines.flow.first
import com.unshoo.pixelmusic.presentation.components.ScreenWrapper
import com.unshoo.pixelmusic.ui.theme.ExpressiveSprings

@OptIn(UnstableApi::class)
@SuppressLint("UnrememberedGetBackStackEntry")
@Composable
fun AppNavigation(
    playerViewModel: PlayerViewModel,
    navController: NavHostController,
    paddingValues: PaddingValues,
    userPreferencesRepository: UserPreferencesRepository,
    onSearchBarActiveChange: (Boolean) -> Unit,
    onOpenSidebar: () -> Unit
) {
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        startDestination = launchTabToRoute(userPreferencesRepository.launchTabFlow.first())
    }

    startDestination?.let { initialRoute ->
        NavHost(
            navController = navController,
            startDestination = initialRoute
        ) {
            composable(
                Screen.Home.route,
                enterTransition = {
                    mainRootEnterTransition(
                        fromRoute = initialState.destination.route,
                        toRoute = targetState.destination.route,
                        fallback = enterTransition()
                    )
                },
                exitTransition = {
                    mainRootExitTransition(
                        fromRoute = initialState.destination.route,
                        toRoute = targetState.destination.route,
                        fallback = exitTransition()
                    )
                },
                popEnterTransition = {
                    mainRootEnterTransition(
                        fromRoute = initialState.destination.route,
                        toRoute = targetState.destination.route,
                        fallback = popEnterTransition()
                    )
                },
                popExitTransition = {
                    mainRootExitTransition(
                        fromRoute = initialState.destination.route,
                        toRoute = targetState.destination.route,
                        fallback = popExitTransition()
                    )
                },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    HomeScreen(
                        navController = navController, 
                        paddingValuesParent = paddingValues, 
                        playerViewModel = playerViewModel,
                        onOpenSidebar = onOpenSidebar
                    )
                }
            }
            composable(
                Screen.Explore.route,
                enterTransition = {
                    mainRootEnterTransition(
                        fromRoute = initialState.destination.route,
                        toRoute = targetState.destination.route,
                        fallback = enterTransition()
                    )
                },
                exitTransition = {
                    mainRootExitTransition(
                        fromRoute = initialState.destination.route,
                        toRoute = targetState.destination.route,
                        fallback = exitTransition()
                    )
                },
                popEnterTransition = {
                    mainRootEnterTransition(
                        fromRoute = initialState.destination.route,
                        toRoute = targetState.destination.route,
                        fallback = popEnterTransition()
                    )
                },
                popExitTransition = {
                    mainRootExitTransition(
                        fromRoute = initialState.destination.route,
                        toRoute = targetState.destination.route,
                        fallback = popExitTransition()
                    )
                },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    ExploreScreen(
                        navController = navController,
                        playerViewModel = playerViewModel,
                        paddingValuesParent = paddingValues
                    )
                }
            }
            composable(
                Screen.Search.route,
                enterTransition = {
                    mainRootEnterTransition(
                        fromRoute = initialState.destination.route,
                        toRoute = targetState.destination.route,
                        fallback = enterTransition()
                    )
                },
                exitTransition = {
                    mainRootExitTransition(
                        fromRoute = initialState.destination.route,
                        toRoute = targetState.destination.route,
                        fallback = exitTransition()
                    )
                },
                popEnterTransition = {
                    mainRootEnterTransition(
                        fromRoute = initialState.destination.route,
                        toRoute = targetState.destination.route,
                        fallback = popEnterTransition()
                    )
                },
                popExitTransition = {
                    mainRootExitTransition(
                        fromRoute = initialState.destination.route,
                        toRoute = targetState.destination.route,
                        fallback = popExitTransition()
                    )
                },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    SearchScreen(
                        paddingValues = paddingValues,
                        playerViewModel = playerViewModel,
                        navController = navController,
                        onSearchBarActiveChange = onSearchBarActiveChange
                    )
                }
            }
            composable(
                Screen.Library.route,
                enterTransition = {
                    mainRootEnterTransition(
                        fromRoute = initialState.destination.route,
                        toRoute = targetState.destination.route,
                        fallback = enterTransition()
                    )
                },
                exitTransition = {
                    mainRootExitTransition(
                        fromRoute = initialState.destination.route,
                        toRoute = targetState.destination.route,
                        fallback = exitTransition()
                    )
                },
                popEnterTransition = {
                    mainRootEnterTransition(
                        fromRoute = initialState.destination.route,
                        toRoute = targetState.destination.route,
                        fallback = popEnterTransition()
                    )
                },
                popExitTransition = {
                    mainRootExitTransition(
                        fromRoute = initialState.destination.route,
                        toRoute = targetState.destination.route,
                        fallback = popExitTransition()
                    )
                },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    LibraryScreen(navController = navController, playerViewModel = playerViewModel)
                }
            }
            composable(
                Screen.Settings.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    SettingsScreen(
                        navController = navController,
                        playerViewModel = playerViewModel,
                        onNavigationIconClick = {
                            navController.popBackStack()
                        }
                    )
                }
            }
            composable(
                Screen.Accounts.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    AccountsScreen(
                        onBackClick = { navController.popBackStack() },
                        onOpenYoutubeAuth = {
                            navController.navigateSafely(Screen.YoutubeAuth.route)
                        },
                        onOpenLastfmSettings = {
                            navController.navigateSafely(Screen.SettingsCategory.createRoute("lastfm"))
                        }
                    )
                }
            }
            composable(
                route = Screen.SettingsCategory.route,
                arguments = listOf(navArgument("categoryId") { type = NavType.StringType }),
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) { backStackEntry ->
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    val categoryId = backStackEntry.arguments?.getString("categoryId")
                    if (categoryId != null) {
                        androidx.compose.runtime.key(categoryId) {
                            SettingsCategoryScreen(
                                categoryId = categoryId,
                                navController = navController,
                                playerViewModel = playerViewModel,
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
            composable(
                Screen.PaletteStyle.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    PaletteStyleSettingsScreen(
                        playerViewModel = playerViewModel,
                        onBackClick = { navController.popBackStack() }
                    )
                }
            }
            composable(
                Screen.Experimental.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    ExperimentalSettingsScreen(
                        navController = navController,
                        playerViewModel = playerViewModel,
                        onNavigationIconClick = { navController.popBackStack() }
                    )
                }
            }
            composable(
                Screen.DailyMixScreen.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    DailyMixScreen(
                        playerViewModel = playerViewModel,
                        navController = navController
                    )
                }
            }
            composable(
                Screen.SmartMix.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    SmartMixScreen(
                        navController = navController,
                        playerViewModel = playerViewModel
                    )
                }
            }
            composable(
                Screen.RecentlyPlayed.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    RecentlyPlayedScreen(
                        playerViewModel = playerViewModel,
                        navController = navController
                    )
                }
            }
            composable(
                Screen.QuickPicksAll.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    QuickPicksAllScreen(
                        playerViewModel = playerViewModel,
                        navController = navController
                    )
                }
            }
            composable(
                Screen.Stats.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    StatsScreen(
                        navController = navController
                    )
                }
            }
            composable(
                route = Screen.PlaylistDetail.route,
                arguments = listOf(navArgument("playlistId") { type = NavType.StringType }),
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) { backStackEntry ->
                val playlistId = backStackEntry.arguments?.getString("playlistId")
                val playlistViewModel: PlaylistViewModel = hiltViewModel()
                if (playlistId != null) {
                    ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                        PlaylistDetailScreen(
                            playlistId = playlistId,
                            playerViewModel = playerViewModel,
                            playlistViewModel = playlistViewModel,
                            onBackClick = { navController.popBackStack() },
                            onDeletePlayListClick = { navController.popBackStack() },
                            navController = navController
                        )
                    }
                }
            }

            composable(
                Screen.DJSpace.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    MashupScreen()
                }
            }

            composable(
                route = Screen.AlbumDetail.route,
                arguments = listOf(navArgument("albumId") { type = NavType.StringType }),
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) { backStackEntry ->
                val albumId = backStackEntry.arguments?.getString("albumId")
                if (albumId != null) {
                    ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                        AlbumDetailScreen(
                            albumId = albumId,
                            navController = navController,
                            playerViewModel = playerViewModel
                        )
                    }
                }
            }
            composable(
                route = Screen.ArtistDetail.route,
                arguments = listOf(navArgument("artistId") { type = NavType.StringType }),
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) { backStackEntry ->
                val artistId = backStackEntry.arguments?.getString("artistId")
                if (artistId != null) {
                    ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                        ArtistDetailScreen(
                            artistId = artistId,
                            navController = navController,
                            playerViewModel = playerViewModel
                        )
                    }
                }
            }
            composable(
                route = Screen.ArtistAlbumsAll.route,
                arguments = listOf(
                    navArgument("artistId") { type = NavType.StringType },
                    navArgument("type") { type = NavType.StringType }
                ),
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) { backStackEntry ->
                val artistId = backStackEntry.arguments?.getString("artistId") ?: return@composable
                val albumType = backStackEntry.arguments?.getString("type") ?: "albums"
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    ArtistAlbumsAllScreen(
                        artistId = artistId,
                        type = albumType,
                        navController = navController
                    )
                }
            }
            composable(
                route = Screen.ArtistSongsAll.route,
                arguments = listOf(
                    navArgument("artistId") { type = NavType.StringType }
                ),
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) { backStackEntry ->
                val artistId = backStackEntry.arguments?.getString("artistId") ?: return@composable
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    ArtistSongsAllScreen(
                        artistId = artistId,
                        navController = navController,
                        playerViewModel = playerViewModel
                    )
                }
            }
            composable(
                "nav_bar_corner_radius",
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    NavBarCornerRadiusScreen(navController)
                }
            }
            composable(
                route = Screen.EditTransition.route,
                arguments = listOf(navArgument("playlistId") {
                    type = NavType.StringType
                    nullable = true
                }),
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    EditTransitionScreen(navController = navController)
                }
            }
            composable(
                Screen.About.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    AboutScreen(
                        navController = navController,
                        viewModel = playerViewModel,
                        onNavigationIconClick = { navController.popBackStack() }
                    )
                }
            }
            composable(
                Screen.EasterEgg.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    EasterEggScreen(
                        viewModel = playerViewModel,
                        onNavigationIconClick = { navController.popBackStack() },
                    )
                }
            }
            composable(
                Screen.ArtistSettings.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    ArtistSettingsScreen(navController = navController)
                }
            }
            composable(
                Screen.DelimiterConfig.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    DelimiterConfigScreen(navController = navController)
                }
            }
            composable(
                Screen.WordDelimiterConfig.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    WordDelimiterConfigScreen(navController = navController)
                }
            }
            composable(
                Screen.Equalizer.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    EqualizerScreen(
                        navController = navController,
                        playerViewModel = playerViewModel
                    )
                }
            }
            composable(
                Screen.DeviceCapabilities.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    com.unshoo.pixelmusic.presentation.screens.DeviceCapabilitiesScreen(
                        navController = navController,
                        playerViewModel = playerViewModel
                    )
                }
            }

            composable(
                Screen.YoutubeAuth.route,
                enterTransition = { enterTransition() },
                exitTransition = { exitTransition() },
                popEnterTransition = { popEnterTransition() },
                popExitTransition = { popExitTransition() },
            ) {
                ScreenWrapper(navController = navController, playerViewModel = playerViewModel) {
                    com.unshoo.pixelmusic.presentation.screens.youtube.AuthScreen(
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

private fun launchTabToRoute(tab: String): String = when (tab) {
    LaunchTab.EXPLORE -> Screen.Explore.route
    LaunchTab.SEARCH -> Screen.Search.route
    LaunchTab.LIBRARY -> Screen.Library.route
    else -> Screen.Home.route
}

// MD3 Expressive — fade-through spring specs for switching between top-level
// (bottom-nav) destinations.
//
// Deliberately non-directional: fade-through (cross-dissolve, no slide) is the
// correct M3 pattern for peer destinations that have no spatial/hierarchical
// relationship to one another — unlike push/pop in Transitions.kt, which slides
// because the entered screen is conceptually "inside" the one that opened it.
// An earlier version of this file computed a FORWARD/BACKWARD tab direction here,
// but nothing ever consumed the value beyond a null-check — dead code, replaced
// below by a plain isMainRootTransition() boolean that does the same gating.
//
// Real spring specs, not tween()+cubic-bezier approximations — this matches the
// physics-based M3 Expressive motion system already used elsewhere in this app
// (ScreenWrapper's corner-radius/dim springs) instead of hand-picked curves that
// only resemble one. Springs have no fixed end time, but that's safe here: input
// gating in ScreenWrapper is keyed off lifecycle/navigation-target state, not
// transition duration (see the hit-testing note in Transitions.kt), and
// shouldRunDepthEffects already skips the offscreen depth layer for main-root
// switches — so there's no zombie hit-test window a longer-settling spring could
// reopen. Exit uses "fast" tokens and enter uses "default" tokens, preserving this
// file's previous exit-quicker-than-enter feel (180ms/320ms) so the outgoing tab
// clears out of the way promptly while the incoming one settles a touch slower.
private val M3_TRANSFORM_FADE_IN_SPEC = spring<Float>(
    dampingRatio = ExpressiveSprings.DefaultEffectsDampingRatio,
    stiffness = ExpressiveSprings.DefaultEffectsStiffness
)

private val M3_TRANSFORM_FADE_OUT_SPEC = spring<Float>(
    dampingRatio = ExpressiveSprings.FastEffectsDampingRatio,
    stiffness = ExpressiveSprings.FastEffectsStiffness
)

private val M3_TRANSFORM_SCALE_IN_SPEC = spring<Float>(
    dampingRatio = ExpressiveSprings.DefaultSpatialDampingRatio,
    stiffness = ExpressiveSprings.DefaultSpatialStiffness
)

private val M3_TRANSFORM_SCALE_OUT_SPEC = spring<Float>(
    dampingRatio = ExpressiveSprings.FastSpatialDampingRatio,
    stiffness = ExpressiveSprings.FastSpatialStiffness
)

private fun isMainRootTransition(fromRoute: String?, toRoute: String?): Boolean =
    isMainRootRoute(fromRoute) && isMainRootRoute(toRoute) && fromRoute != toRoute

private fun mainRootEnterTransition(
    fromRoute: String?,
    toRoute: String?,
    fallback: EnterTransition
): EnterTransition {
    if (!isMainRootTransition(fromRoute, toRoute)) return fallback
    return fadeIn(animationSpec = M3_TRANSFORM_FADE_IN_SPEC) +
        scaleIn(
            animationSpec = M3_TRANSFORM_SCALE_IN_SPEC,
            initialScale = 0.96f,
            transformOrigin = TransformOrigin(0.5f, 0.5f)
        )
}

private fun mainRootExitTransition(
    fromRoute: String?,
    toRoute: String?,
    fallback: ExitTransition
): ExitTransition {
    if (!isMainRootTransition(fromRoute, toRoute)) return fallback
    return fadeOut(animationSpec = M3_TRANSFORM_FADE_OUT_SPEC) +
        scaleOut(
            animationSpec = M3_TRANSFORM_SCALE_OUT_SPEC,
            targetScale = 0.98f,
            transformOrigin = TransformOrigin(0.5f, 0.5f)
        )
}
