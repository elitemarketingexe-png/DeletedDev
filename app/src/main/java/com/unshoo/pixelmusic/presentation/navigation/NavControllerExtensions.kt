package com.unshoo.pixelmusic.presentation.navigation

import android.app.Activity
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder
import com.unshoo.pixelmusic.data.ads.AdManager

private fun NavController.isReadyForNavigation(): Boolean {
    return runCatching {
        // We allow navigation if the current entry is at least STARTED.
        // This is safer than strictly RESUMED as transitions can sometimes delay RESUMED state.
        currentBackStackEntry?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true
    }.getOrDefault(false)
}

fun NavController.navigateSafely(route: String): Boolean {
    if (!isReadyForNavigation()) return false
    val activity = context as? Activity
    if (activity != null && (route.contains("settings_category/ai") || route.contains("smart_mix"))) {
        if (AdManager.isAdLoaded()) {
            Toast.makeText(activity, "Opening support ad...", Toast.LENGTH_SHORT).show()
            AdManager.showRewardedAd(activity) { _ ->
                navigate(route) {
                    launchSingleTop = true
                }
            }
            return true
        } else {
            AdManager.loadRewardedAd(activity.applicationContext)
        }
    }
    navigate(route) {
        launchSingleTop = true
    }
    return true
}

fun NavController.navigateSafely(
    route: String,
    builder: NavOptionsBuilder.() -> Unit
): Boolean {
    if (!isReadyForNavigation()) return false
    val activity = context as? Activity
    if (activity != null && (route.contains("settings_category/ai") || route.contains("smart_mix"))) {
        if (AdManager.isAdLoaded()) {
            Toast.makeText(activity, "Opening support ad...", Toast.LENGTH_SHORT).show()
            AdManager.showRewardedAd(activity) { _ ->
                navigate(route) {
                    launchSingleTop = true
                    builder()
                }
            }
            return true
        } else {
            AdManager.loadRewardedAd(activity.applicationContext)
        }
    }
    navigate(route) {
        launchSingleTop = true
        builder()
    }
    return true
}

fun NavController.navigateSafelyReplacing(
    route: String,
    patternToPop: String,
    builder: NavOptionsBuilder.() -> Unit = {}
): Boolean {
    if (!isReadyForNavigation()) return false
    navigate(route) {
        launchSingleTop = false
        popUpTo(patternToPop) {
            inclusive = true
        }
        builder()
    }
    return true
}

fun NavController.navigateToTopLevelSafely(route: String): Boolean {
    val startDestinationId = runCatching { graph.startDestinationId }.getOrNull() ?: return false
    val activity = context as? Activity
    if (activity != null && (route.contains("settings_category/ai") || route.contains("smart_mix"))) {
        if (AdManager.isAdLoaded()) {
            Toast.makeText(activity, "Opening support ad...", Toast.LENGTH_SHORT).show()
            AdManager.showRewardedAd(activity) { _ ->
                navigate(route) {
                    popUpTo(startDestinationId) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            return true
        } else {
            AdManager.loadRewardedAd(activity.applicationContext)
        }
    }
    navigate(route) {
        popUpTo(startDestinationId) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
    return true
}
