package com.unshoo.pixelmusic.data.remote.youtube

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.unshoo.pixelmusic.data.database.youtube.AppDatabase
import com.unshoo.pixelmusic.data.model.youtube.Song
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.absoluteValue

class SongDownloadWorker(
    private val appContext: Context,
    private val params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun musicDao(): com.unshoo.pixelmusic.data.database.MusicDao
        fun lyricsDao(): com.unshoo.pixelmusic.data.database.LyricsDao
        fun lyricsRepository(): com.unshoo.pixelmusic.data.repository.LyricsRepository
        fun songMetadataEditor(): com.unshoo.pixelmusic.data.media.SongMetadataEditor
    }

    private val playlistRepository = AppDatabase.getInstance(appContext).playlistRepository()
    private val localSongRepository = AppDatabase.getInstance(appContext).songRepository()
    private val songRepository = SongRepository()
    private val musicDao = EntryPointAccessors.fromApplication(
        appContext,
        WorkerEntryPoint::class.java
    ).musicDao()

    @OptIn(UnstableApi::class)
    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            val playlistId = params.inputData.getString(PLAYLIST_KEY)
            val songId = params.inputData.getString(SONG_KEY)
                ?: return@withContext Result.failure()

            var song = localSongRepository.getSong(songId)
            if (song == null) {
                var fetchedSong: Song? = null
                songRepository.getSongInfo(songId).collect { apiResult ->
                    if (apiResult is ApiResult.Success) {
                        fetchedSong = apiResult.data
                    }
                }
                song = fetchedSong ?: return@withContext Result.failure()
                localSongRepository.create(song)
            }

            if (playlistId != null) {
                val playlist = playlistRepository.getPlaylistById(playlistId)
                if (playlist != null) {
                    val playlistImage =
                        DownloadHelper.downloadImage(
                            appContext,
                            playlist.info.coverHref,
                            playlist.info.id
                        )
                    playlistRepository.insertPlaylist(
                        playlist.info.copy(
                            coverPath = playlistImage?.path
                        )
                    )
                }
            }

            try {
                var fullSong: Song? = null
                songRepository.getSongInfo(song.youtubeId)
                    .collect { apiResult ->
                        when (apiResult) {
                            is ApiResult.Success -> {
                                fullSong = apiResult.data
                            }
                            else -> {}
                        }
                    }

                val audioPath =
                    DownloadHelper.downloadAudio(
                        appContext, song,
                    )
                val thumbnailPath =
                    DownloadHelper.downloadImage(
                        appContext,
                        fullSong?.thumbnailHref ?: song.thumbnailHref,
                        song.youtubeId
                    )

                val updatedSong = song.copy(
                    thumbnailPath = thumbnailPath?.path,
                    audioFilePath = audioPath,
                )
                localSongRepository.create(updatedSong)

                ensureYoutubeSongInLibrary(updatedSong)

                if (audioPath != null) {
                    val mainId = -(15_000_000_000_000L + song.youtubeId.hashCode().toLong().absoluteValue)
                    val parentDir = File(audioPath).parentFile?.absolutePath ?: ""
                    musicDao.updateSongFilePathAndParent(mainId, audioPath, parentDir)
                    playlistRepository.insertCrossRef(
                        com.unshoo.pixelmusic.data.model.youtube.PlaylistSongCrossRef(
                            playlistId = Constants.Downloads.DOWNLOADED_PLAYLIST_ID,
                            songId = song.youtubeId,
                            position = 0
                        )
                    )

                    // METADATA & LYRICS EMBEDDING
                    try {
                        val entryPoint = EntryPointAccessors.fromApplication(
                            appContext,
                            WorkerEntryPoint::class.java
                        )
                        val lyricsDao = entryPoint.lyricsDao()
                        val lyricsRepository = entryPoint.lyricsRepository()
                        val songMetadataEditor = entryPoint.songMetadataEditor()

                        val dbSong = musicDao.getSongById(mainId).first()
                        if (dbSong != null) {
                            val songModel = com.unshoo.pixelmusic.data.model.Song(
                                id = dbSong.id.toString(),
                                title = dbSong.title,
                                artistName = dbSong.artistName,
                                albumName = dbSong.albumName ?: "YouTube Music",
                                duration = dbSong.duration,
                                filePath = audioPath,
                                albumArtUri = dbSong.albumArtUriString
                            )

                            // Fetch lyrics from remote
                            val lyricsObj = try {
                                lyricsRepository.getLyrics(
                                    song = songModel,
                                    sourcePreference = com.unshoo.pixelmusic.data.model.LyricsSourcePreference.REMOTE,
                                    forceRefresh = true
                                )
                            } catch (e: Exception) {
                                null
                            }

                            val lyricsText = if (lyricsObj != null) {
                                com.unshoo.pixelmusic.utils.LyricsUtils.toLrcString(lyricsObj)
                            } else {
                                ""
                            }

                            val isSynced = lyricsObj?.synced?.isNotEmpty() == true

                            // Cache in local DB
                            if (lyricsText.isNotBlank()) {
                                lyricsDao.insert(
                                    com.unshoo.pixelmusic.data.database.LyricsEntity(
                                        songId = mainId,
                                        content = lyricsText,
                                        isSynced = isSynced,
                                        source = "remote"
                                    )
                                )
                            }

                            // Embed Album Art & Lyrics to file
                            val coverArtUpdate = if (thumbnailPath != null && thumbnailPath.exists()) {
                                val bytes = thumbnailPath.readBytes()
                                com.unshoo.pixelmusic.data.media.CoverArtUpdate(
                                    bytes = bytes,
                                    mimeType = "image/jpeg",
                                    width = 0,
                                    height = 0
                                )
                            } else {
                                null
                            }

                            songMetadataEditor.editSongMetadata(
                                songId = mainId,
                                newTitle = dbSong.title,
                                newArtist = dbSong.artistName,
                                newAlbum = dbSong.albumName ?: "YouTube Music",
                                newGenre = dbSong.genre ?: "YouTube Music",
                                newLyrics = lyricsText,
                                newTrackNumber = 0,
                                newDiscNumber = null,
                                coverArtUpdate = coverArtUpdate
                            )
                        }
                    } catch (e: Exception) {
                        UmihiHelper.printe("Error writing metadata for ${song.title}", exception = e)
                    }
                }

                UmihiNotificationManager.showSongDownloadSuccess(appContext, song)
                Result.success()
            } catch (_: CancellationException) {
                UmihiHelper.printd("Song download canceled ${song.title}")
                Result.failure()
            } catch (e: Exception) {
                UmihiNotificationManager.showSongDownloadFailed(
                    appContext,
                    song
                )
                UmihiHelper.printe(
                    message = "Error downloading song: ${song.youtubeId}",
                    exception = e
                )
                Result.failure()
            }
        }
    }

    private fun toUnifiedYoutubeSongId(youtubeId: String): Long {
        return -(15_000_000_000_000L + youtubeId.hashCode().toLong().absoluteValue)
    }

    private fun toUnifiedYoutubeAlbumId(albumName: String): Long {
        return -(16_000_000_000_000L + albumName.lowercase().hashCode().toLong().absoluteValue)
    }

    private fun toUnifiedYoutubeArtistId(artistName: String): Long {
        return -(17_000_000_000_000L + artistName.lowercase().hashCode().toLong().absoluteValue)
    }

    private fun parseYoutubeArtistNames(artistStr: String): List<String> {
        if (artistStr.isBlank()) return listOf("Unknown Artist")
        val parsed = artistStr
            .split(Regex("\\s*[,/&;+、•]\\s*|\\s+(?:feat\\.|ft\\.|vs)\\s+|\\s+and\\s+", RegexOption.IGNORE_CASE))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        return if (parsed.isEmpty()) listOf("Unknown Artist") else parsed
    }

    private suspend fun ensureYoutubeSongInLibrary(song: Song) {
        val songId = toUnifiedYoutubeSongId(song.youtubeId)
        val title = song.title.takeIf { it.isNotBlank() } ?: "YouTube Video"
        val artist = song.artist.takeIf { it.isNotBlank() } ?: "Unknown Artist"
        val artistNames = parseYoutubeArtistNames(artist)
        val primaryArtistName = artistNames.firstOrNull() ?: "Unknown Artist"
        val primaryArtistId = toUnifiedYoutubeArtistId(primaryArtistName)

        val artistsToInsert = artistNames.map { name ->
            com.unshoo.pixelmusic.data.database.ArtistEntity(
                id = toUnifiedYoutubeArtistId(name),
                name = name,
                trackCount = 0,
                imageUrl = null
            )
        }

        val crossRefsToInsert = artistNames.mapIndexed { index, name ->
            val artistId = toUnifiedYoutubeArtistId(name)
            com.unshoo.pixelmusic.data.database.SongArtistCrossRef(
                songId = songId,
                artistId = artistId,
                isPrimary = index == 0
            )
        }

        val albumId = toUnifiedYoutubeAlbumId("YouTube Music")
        val albumName = "YouTube Music"
        val albumToInsert = com.unshoo.pixelmusic.data.database.AlbumEntity(
            id = albumId,
            title = albumName,
            artistName = primaryArtistName,
            artistId = primaryArtistId,
            songCount = 0,
            dateAdded = System.currentTimeMillis(),
            year = 0,
            albumArtUriString = song.thumbnailPath ?: song.thumbnailHref
        )

        val artistsJson = try {
            val arr = org.json.JSONArray()
            artistNames.forEachIndexed { idx, name ->
                val obj = org.json.JSONObject()
                obj.put("id", toUnifiedYoutubeArtistId(name))
                obj.put("name", name)
                obj.put("primary", idx == 0)
                arr.put(obj)
            }
            arr.toString()
        } catch (e: Exception) {
            null
        }

        val durationMs = try {
            if (song.duration.contains(":")) {
                val parts = song.duration.split(":")
                when (parts.size) {
                    1 -> parts[0].toLong() * 1000L
                    2 -> (parts[0].toLong() * 60L + parts[1].toLong()) * 1000L
                    3 -> ((parts[0].toLong() * 3600L + parts[1].toLong() * 60L + parts[2].toLong())) * 1000L
                    else -> 0L
                }
            } else {
                song.duration.toLongOrNull() ?: 0L
            }
        } catch (e: Exception) {
            0L
        }

        val songEntity = com.unshoo.pixelmusic.data.database.SongEntity(
            id = songId,
            title = title,
            artistName = artist,
            artistId = primaryArtistId,
            albumArtist = null,
            albumName = albumName,
            albumId = albumId,
            contentUriString = "youtube://${song.youtubeId}",
            albumArtUriString = song.thumbnailPath ?: song.thumbnailHref,
            duration = durationMs,
            genre = song.genre?.takeIf { it.isNotBlank() } ?: "YouTube Music",
            filePath = "",
            parentDirectoryPath = "youtube://",
            isFavorite = false,
            lyrics = null,
            trackNumber = 0,
            year = 0,
            dateAdded = System.currentTimeMillis(),
            mimeType = "audio/webm",
            bitrate = null,
            sampleRate = null,
            telegramChatId = null,
            telegramFileId = null,
            artistsJson = artistsJson,
            sourceType = com.unshoo.pixelmusic.data.database.SourceType.YOUTUBE
        )

        musicDao.incrementalSyncMusicData(
            songs = listOf(songEntity),
            albums = listOf(albumToInsert),
            artists = artistsToInsert,
            crossRefs = crossRefsToInsert,
            deletedSongIds = emptyList()
        )
    }

    companion object {
        const val PLAYLIST_KEY = "playlist"
        const val SONG_KEY = "song"
    }
}
