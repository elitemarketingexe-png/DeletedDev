package com.unshoo.pixelmusic.presentation.viewmodel

import android.app.Activity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.unshoo.pixelmusic.R
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.preferences.PlaylistPreferencesRepository
import com.unshoo.pixelmusic.data.repository.MusicRepository
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@ViewModelScoped
class SongRemovalStateHolder @Inject constructor(
    private val musicRepository: MusicRepository,
    private val metadataEditStateHolder: MetadataEditStateHolder,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    private val libraryStateHolder: LibraryStateHolder
) {
    suspend fun showDeleteConfirmation(activity: Activity, song: Song): Boolean {
        return withContext(Dispatchers.Main) {
            try {
                if (activity.isFinishing || activity.isDestroyed) {
                    return@withContext false
                }

                val userChoice = CompletableDeferred<Boolean>()
                val dialog = MaterialAlertDialogBuilder(activity)
                    .setTitle(activity.getString(R.string.dialog_delete_song_title))
                    .setMessage(
                        activity.getString(
                            R.string.dialog_delete_song_message,
                            song.title,
                            song.displayArtist
                        )
                    )
                    .setPositiveButton(activity.getString(R.string.delete_action)) { _, _ ->
                        userChoice.complete(true)
                    }
                    .setNegativeButton(activity.getString(R.string.cancel)) { _, _ ->
                        userChoice.complete(false)
                    }
                    .setOnCancelListener {
                        userChoice.complete(false)
                    }
                    .setCancelable(true)
                    .create()

                dialog.show()
                userChoice.await()
            } catch (_: Exception) {
                false
            }
        }
    }

    suspend fun deleteSongFile(song: Song): Boolean {
        return metadataEditStateHolder.deleteSong(song)
    }

    suspend fun removeSongFromLibrary(song: Song) {
        libraryStateHolder.removeSong(song.id)
        if (song.isYouTube) {
            musicRepository.setDislikedStatus(song.id, true)
            musicRepository.setFavoriteStatus(song.id, false)
            val youtubeId = song.youtubeId ?: if (song.id.startsWith("youtube_")) {
                song.id.substringAfter("youtube_")
            } else if (song.contentUriString.startsWith("youtube://")) {
                song.contentUriString.substringAfter("youtube://")
            } else null

            if (youtubeId != null && unshoo.ianshulyadav.pixelmusic.innertube.YouTube.hasLoginCookie()) {
                kotlin.runCatching {
                    unshoo.ianshulyadav.pixelmusic.innertube.YouTube.dislikeVideo(youtubeId, true)
                }
            }
        } else {
            // Some cloud/YouTube songs use non-numeric UI IDs. Do not crash removal if the
            // backing DB row cannot be addressed by the visible id.
            song.id.toLongOrNull()?.let { musicRepository.deleteById(it) }
        }
        playlistPreferencesRepository.removeSongFromAllPlaylists(song.id)
    }
}

