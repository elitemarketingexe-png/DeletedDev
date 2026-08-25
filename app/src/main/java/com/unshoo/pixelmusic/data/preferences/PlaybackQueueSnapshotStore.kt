package com.unshoo.pixelmusic.data.preferences

import android.content.Context
import com.unshoo.pixelmusic.data.model.PlaybackQueueSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Durable playback-queue snapshot.
 *
 * The regular settings DataStore (`datastore/settings.preferences_pb`) is excluded from
 * Android Auto Backup / device transfer because it can contain API keys. Storing the
 * last queue there meant uninstall + restore (or a clean install after backup) came
 * back with no queue and the miniplayer jumped to song 1.
 *
 * `filesDir/playback_queue_snapshot.json` is included in backup and survives process
 * death, updates, and cloud restore. DataStore is kept as a same-install fallback.
 */
@Singleton
class PlaybackQueueSnapshotStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    private val snapshotFile: File
        get() = File(context.filesDir, SNAPSHOT_FILE_NAME)

    suspend fun getSnapshot(): PlaybackQueueSnapshot? {
        val fileSnapshot = withContext(Dispatchers.IO) { readFileSnapshot() }
        val prefsSnapshot = runCatching {
            userPreferencesRepository.getPlaybackQueueSnapshotOnce()
        }.getOrNull()?.takeIf { it.items.isNotEmpty() }

        return listOfNotNull(fileSnapshot, prefsSnapshot)
            .maxByOrNull { it.savedAtEpochMs }
    }

    suspend fun saveSnapshot(snapshot: PlaybackQueueSnapshot) {
        if (snapshot.items.isEmpty()) return
        withContext(Dispatchers.IO) {
            writeFileSnapshot(snapshot)
        }
        runCatching {
            userPreferencesRepository.setPlaybackQueueSnapshot(snapshot)
        }.onFailure { error ->
            Timber.w(error, "Failed to mirror playback snapshot into preferences")
        }
    }

    suspend fun clearSnapshot() {
        withContext(Dispatchers.IO) {
            runCatching { snapshotFile.delete() }
            runCatching { File(snapshotFile.absolutePath + ".tmp").delete() }
        }
        runCatching {
            userPreferencesRepository.setPlaybackQueueSnapshot(null)
        }
    }

    private fun readFileSnapshot(): PlaybackQueueSnapshot? {
        return runCatching {
            val file = snapshotFile
            if (!file.exists() || file.length() <= 0L) return@runCatching null
            json.decodeFromString<PlaybackQueueSnapshot>(file.readText())
                .takeIf { it.items.isNotEmpty() }
        }.onFailure { error ->
            Timber.w(error, "Failed to read playback queue snapshot file")
        }.getOrNull()
    }

    private fun writeFileSnapshot(snapshot: PlaybackQueueSnapshot) {
        runCatching {
            val parent = snapshotFile.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }
            val tmp = File(snapshotFile.absolutePath + ".tmp")
            tmp.writeText(json.encodeToString(snapshot))
            if (!tmp.renameTo(snapshotFile)) {
                snapshotFile.writeText(json.encodeToString(snapshot))
                tmp.delete()
            }
        }.onFailure { error ->
            Timber.w(error, "Failed to write playback queue snapshot file")
        }
    }

    companion object {
        const val SNAPSHOT_FILE_NAME = "playback_queue_snapshot.json"
    }
}
