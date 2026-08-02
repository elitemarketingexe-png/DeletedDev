package com.unshoo.pixelmusic.data.service

import android.app.Notification
import android.os.Build

/**
 * Android 16 (API 36) "Promoted Ongoing" — aka **Live Updates** — support.
 *
 * A promoted ongoing notification is rendered by the system as an iOS-Dynamic-Island-style
 * compact pill in the status bar area, which expands on tap. It is the closest native
 * Android equivalent of Apple's Dynamic Island, and it works even while the app is
 * in the background (unlike the in-app [com.unshoo.pixelmusic.presentation.components.DynamicIslandOverlay]).
 *
 * Usage: build your [Notification], then call [promote] right before `notify(...)`.
 * Call [demote] when the notification no longer represents a live state (e.g. paused).
 *
 * All calls are no-ops below API 36 and never throw.
 */
object LiveUpdateUtil {

    /** Marks [notification] as a promoted / live-update notification (island pill). */
    fun promote(notification: Notification) {
        if (Build.VERSION.SDK_INT < 36) return
        runCatching {
            notification.extras.putBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING, true)
        }
    }

    /** Un-marks [notification] so the system reverts it to a regular notification. */
    fun demote(notification: Notification) {
        if (Build.VERSION.SDK_INT < 36) return
        runCatching {
            notification.extras.remove(Notification.EXTRA_REQUEST_PROMOTED_ONGOING)
        }
    }

    /** Convenience: promote only while the player is actively playing. */
    fun promoteIf(notification: Notification, active: Boolean) {
        if (active) promote(notification) else demote(notification)
    }
}
