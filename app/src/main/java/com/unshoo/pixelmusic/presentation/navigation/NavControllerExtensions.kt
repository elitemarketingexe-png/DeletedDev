package com.unshoo.pixelmusic.presentation.navigation

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder

private fun NavController.isReadyForNavigation(targetRoute: String? = null): Boolean {
    val lifecycle = currentBackStackEntry?.lifecycle
    if (lifecycle != null && !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return false
    if (targetRoute != null && currentDestination?.route == targetRoute) return false
    return true
}

fun NavController.popBackStackSafely(): Boolean {
    val lifecycle = currentBackStackEntry?.lifecycle
    if (lifecycle != null && !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return false
    return try {
        popBackStack()
    } catch (e: Exception) {
        android.util.Log.e("NavController", "Safe popBackStack failed", e)
        false
    }
}

fun NavController.navigateSafely(route: String): Boolean {
    if (!isReadyForNavigation(route)) return false
    try {
        navigate(route) {
            launchSingleTop = true
        }
        return true
    } catch (e: Exception) {
        android.util.Log.e("NavController", "Safe navigation failed for route: $route", e)
        return false
    }
}

fun NavController.navigateSafely(
    route: String,
    builder: NavOptionsBuilder.() -> Unit
): Boolean {
    if (!isReadyForNavigation(route)) return false
    try {
        navigate(route) {
            launchSingleTop = true
            builder()
        }
        return true
    } catch (e: Exception) {
        android.util.Log.e("NavController", "Safe navigation failed for route: $route", e)
        return false
    }
}

fun NavController.navigateSafelyReplacing(
    route: String,
    patternToPop: String,
    builder: NavOptionsBuilder.() -> Unit = {}
): Boolean {
    if (!isReadyForNavigation(route)) return false
    try {
        navigate(route) {
            launchSingleTop = false
            popUpTo(patternToPop) {
                inclusive = true
            }
            builder()
        }
        return true
    } catch (e: Exception) {
        android.util.Log.e("NavController", "Safe navigation replacing failed for route: $route", e)
        return false
    }
}

fun NavController.navigateToTopLevelSafely(route: String): Boolean {
    if (!isReadyForNavigation(route)) return false
    val startDestinationId = runCatching { graph.startDestinationId }.getOrNull() ?: return false
    try {
        navigate(route) {
            popUpTo(startDestinationId) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
        return true
    } catch (e: Exception) {
        android.util.Log.e("NavController", "Safe top-level navigation failed for route: $route", e)
        return false
    }
}

