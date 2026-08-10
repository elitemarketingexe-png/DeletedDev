package com.unshoo.pixelmusic.data.remote.youtube

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import java.util.UUID

/**
 * Receives cancel intents from download notification actions and cancels the
 * corresponding WorkManager job by its UUID tag.
 */
class DownloadCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val workId = intent.getStringExtra(EXTRA_WORK_ID) ?: return
        runCatching { WorkManager.getInstance(context).cancelWorkById(UUID.fromString(workId)) }
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
        if (notifId != -1) {
            runCatching {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                nm.cancel(notifId)
            }
        }
    }

    companion object {
        const val ACTION_CANCEL = "com.unshoo.pixelmusic.DOWNLOAD_CANCEL"
        const val EXTRA_WORK_ID = "extra_work_id"
        const val EXTRA_NOTIF_ID = "extra_notif_id"
    }
}
