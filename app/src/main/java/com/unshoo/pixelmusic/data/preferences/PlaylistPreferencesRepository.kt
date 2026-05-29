package com.unshoo.pixelmusic.data.preferences

import android.content.Context
import com.unshoo.pixelmusic.data.database.LocalPlaylistDao
import com.unshoo.pixelmusic.data.database.MusicDao
import com.unshoo.pixelmusic.data.database.youtube.AppDatabase
import com.unshoo.pixelmusic.data.database.toEntity
import com.unshoo.pixelmusic.data.database.toPlaylist
import com.unshoo.pixelmusic.data.model.Playlist
import com.unshoo.pixelmusic.data.model.SortOption
import com.unshoo.pixelmusic.data.remote.youtube.DownloadRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue

@Singleton
class PlaylistPreferencesRepository @Inject constructor(
    private val localPlaylistDao: LocalPlaylistDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val musicDao: MusicDao,
    @ApplicationContext private val context: Context
) {
    private val coverPrefs = context.getSharedPreferences("playlist_cover_customizations", Context.MODE_PRIVATE)
    private val migrationMutex = Mutex()
    @Volatile
    private var migrationChecked = false

    val userPlaylistsFlow: Flow<List<Playlist>> = combine(
        localPlaylistDao.observePlaylistsWithSongs()
            .onStart { ensureMigratedIfNeeded() }
            .map { rows ->
                rows.map { row ->
                    row.playlist.toPlaylist(
                        songIds = row.songs.sortedBy { it.sortOrder }.map { it.songId }
                    )
                }
            },
        AppDatabase.getInstance(context).playlistRepository().observeAll()
    ) { localPlaylists, ytPlaylists ->
        val mappedYtPlaylists = ytPlaylists.map { ytPlaylist ->
            val pId = ytPlaylist.info.id
            val defaultCoverImage = ytPlaylist.info.coverPath ?: ytPlaylist.info.coverHref
            val savedCoverImageUri = coverPrefs.getString("${pId}_coverImageUri", null)
            val coverColor = if (coverPrefs.contains("${pId}_coverColorArgb")) coverPrefs.getInt("${pId}_coverColorArgb", 0) else null
            val coverIcon = coverPrefs.getString("${pId}_coverIconName", null)
            val coverShape = coverPrefs.getString("${pId}_coverShapeType", null)
            val detail1 = if (coverPrefs.contains("${pId}_coverShapeDetail1")) coverPrefs.getFloat("${pId}_coverShapeDetail1", 0f) else null
            val detail2 = if (coverPrefs.contains("${pId}_coverShapeDetail2")) coverPrefs.getFloat("${pId}_coverShapeDetail2", 0f) else null
            val detail3 = if (coverPrefs.contains("${pId}_coverShapeDetail3")) coverPrefs.getFloat("${pId}_coverShapeDetail3", 0f) else null
            val detail4 = if (coverPrefs.contains("${pId}_coverShapeDetail4")) coverPrefs.getFloat("${pId}_coverShapeDetail4", 0f) else null

            Playlist(
                id = pId,
                name = if (ytPlaylist.info.isDownloadedPlaylist) "Downloaded Songs" else ytPlaylist.info.title,
                songIds = ytPlaylist.songs.map { "youtube_${it.youtubeId}" },
                createdAt = ytPlaylist.info.lastSyncTimestamp,
                lastModified = ytPlaylist.info.lastSyncTimestamp,
                isAiGenerated = false,
                isQueueGenerated = false,
                coverImageUri = savedCoverImageUri ?: defaultCoverImage,
                coverColorArgb = coverColor,
                coverIconName = coverIcon,
                coverShapeType = coverShape,
                coverShapeDetail1 = detail1,
                coverShapeDetail2 = detail2,
                coverShapeDetail3 = detail3,
                coverShapeDetail4 = detail4,
                source = "YOUTUBE"
            )
        }
        (localPlaylists + mappedYtPlaylists).distinctBy { it.id }
    }

    val playlistSongOrderModesFlow: Flow<Map<String, String>> =
        userPreferencesRepository.playlistSongOrderModesFlow
    val playlistsSortOptionFlow: Flow<String> = userPreferencesRepository.playlistsSortOptionFlow
    val showTelegramCloudPlaylistsFlow: Flow<Boolean> =
        userPreferencesRepository.showTelegramCloudPlaylistsFlow
    val telegramTopicDisplayModeFlow: Flow<TelegramTopicDisplayMode> =
        userPreferencesRepository.telegramTopicDisplayModeFlow
    suspend fun setTelegramTopicDisplayMode(mode: TelegramTopicDisplayMode) =
        userPreferencesRepository.setTelegramTopicDisplayMode(mode)

    suspend fun createPlaylist(
        name: String,
        songIds: List<String> = emptyList(),
        isAiGenerated: Boolean = false,
        isQueueGenerated: Boolean = false,
        coverImageUri: String? = null,
        coverColorArgb: Int? = null,
        coverIconName: String? = null,
        coverShapeType: String? = null,
        coverShapeDetail1: Float? = null,
        coverShapeDetail2: Float? = null,
        coverShapeDetail3: Float? = null,
        coverShapeDetail4: Float? = null,
        customId: String? = null,
        source: String = "LOCAL"
    ): Playlist {
        ensureMigratedIfNeeded()
        val now = System.currentTimeMillis()
        val newPlaylist = Playlist(
            id = customId ?: UUID.randomUUID().toString(),
            name = name,
            songIds = songIds,
            createdAt = now,
            lastModified = now,
            isAiGenerated = isAiGenerated,
            isQueueGenerated = isQueueGenerated,
            coverImageUri = coverImageUri,
            coverColorArgb = coverColorArgb,
            coverIconName = coverIconName,
            coverShapeType = coverShapeType,
            coverShapeDetail1 = coverShapeDetail1,
            coverShapeDetail2 = coverShapeDetail2,
            coverShapeDetail3 = coverShapeDetail3,
            coverShapeDetail4 = coverShapeDetail4,
            source = source,
        )
        localPlaylistDao.upsertPlaylist(newPlaylist.toEntity())
        localPlaylistDao.replacePlaylistSongs(newPlaylist.id, newPlaylist.songIds)
        return newPlaylist
    }

    suspend fun deletePlaylist(playlistId: String) {
        ensureMigratedIfNeeded()
        val ytPlaylist = AppDatabase.getInstance(context).playlistRepository().getPlaylistById(playlistId)
        if (ytPlaylist != null) {
            DownloadRepository(context).deletePlaylist(ytPlaylist)
        } else {
            localPlaylistDao.deletePlaylist(playlistId)
            clearPlaylistSongOrderMode(playlistId)
        }
        coverPrefs.edit().apply {
            remove("${playlistId}_coverImageUri")
            remove("${playlistId}_coverColorArgb")
            remove("${playlistId}_coverIconName")
            remove("${playlistId}_coverShapeType")
            remove("${playlistId}_coverShapeDetail1")
            remove("${playlistId}_coverShapeDetail2")
            remove("${playlistId}_coverShapeDetail3")
            remove("${playlistId}_coverShapeDetail4")
        }.apply()
    }

    suspend fun renamePlaylist(playlistId: String, newName: String) {
        ensureMigratedIfNeeded()
        val ytPlaylist = AppDatabase.getInstance(context).playlistRepository().getPlaylistById(playlistId)
        if (ytPlaylist != null) {
            val updatedInfo = ytPlaylist.info.copy(title = newName)
            AppDatabase.getInstance(context).playlistRepository().insertPlaylist(updatedInfo)
        } else {
            val existing = userPlaylistsFlow.first().find { it.id == playlistId } ?: return
            val updated = existing.copy(
                name = newName,
                lastModified = System.currentTimeMillis()
            )
            localPlaylistDao.upsertPlaylist(updated.toEntity())
        }
    }

    suspend fun updatePlaylist(playlist: Playlist) {
        ensureMigratedIfNeeded()
        if (playlist.source == "YOUTUBE") {
            val playlistRepository = AppDatabase.getInstance(context).playlistRepository()
            val ytPlaylist = playlistRepository.getPlaylistById(playlist.id)
            if (ytPlaylist != null) {
                val updatedInfo = ytPlaylist.info.copy(title = playlist.name)
                playlistRepository.insertPlaylist(updatedInfo)
                playlistRepository.deleteCrossRefsByPlaylistId(playlist.id)
                val refs = playlist.songIds.map { songIdStr ->
                    val rawYtId = songIdStr.removePrefix("youtube_")
                    com.unshoo.pixelmusic.data.model.youtube.PlaylistSongCrossRef(playlist.id, rawYtId)
                }
                playlistRepository.insertCrossRefs(refs)
            }
            coverPrefs.edit().apply {
                putString("${playlist.id}_coverImageUri", playlist.coverImageUri)
                if (playlist.coverColorArgb != null) putInt("${playlist.id}_coverColorArgb", playlist.coverColorArgb) else remove("${playlist.id}_coverColorArgb")
                putString("${playlist.id}_coverIconName", playlist.coverIconName)
                putString("${playlist.id}_coverShapeType", playlist.coverShapeType)
                if (playlist.coverShapeDetail1 != null) putFloat("${playlist.id}_coverShapeDetail1", playlist.coverShapeDetail1) else remove("${playlist.id}_coverShapeDetail1")
                if (playlist.coverShapeDetail2 != null) putFloat("${playlist.id}_coverShapeDetail2", playlist.coverShapeDetail2) else remove("${playlist.id}_coverShapeDetail2")
                if (playlist.coverShapeDetail3 != null) putFloat("${playlist.id}_coverShapeDetail3", playlist.coverShapeDetail3) else remove("${playlist.id}_coverShapeDetail3")
                if (playlist.coverShapeDetail4 != null) putFloat("${playlist.id}_coverShapeDetail4", playlist.coverShapeDetail4) else remove("${playlist.id}_coverShapeDetail4")
            }.apply()
        } else {
            val updated = playlist.copy(lastModified = System.currentTimeMillis())
            localPlaylistDao.upsertPlaylist(updated.toEntity())
            localPlaylistDao.replacePlaylistSongs(updated.id, updated.songIds)
        }
    }

    suspend fun addSongsToPlaylist(playlistId: String, songIdsToAdd: List<String>) {
        ensureMigratedIfNeeded()
        val ytPlaylist = AppDatabase.getInstance(context).playlistRepository().getPlaylistById(playlistId)
        if (ytPlaylist != null) {
            val songRepository = AppDatabase.getInstance(context).songRepository()
            val playlistRepository = AppDatabase.getInstance(context).playlistRepository()
            val songEntities = songIdsToAdd.mapNotNull { songIdStr ->
                val songIdLong = songIdStr.toLongOrNull()
                if (songIdLong != null) {
                    musicDao.getSongByIdOnce(songIdLong)
                } else if (songIdStr.startsWith("youtube_")) {
                    val yId = songIdStr.removePrefix("youtube_")
                    val expectedLongId = -(15_000_000_000_000L + yId.hashCode().toLong().absoluteValue)
                    musicDao.getSongByIdOnce(expectedLongId)
                } else {
                    null
                }
            }
            val ytSongs = songEntities.map { entity ->
                val yId = entity.contentUriString.removePrefix("youtube://")
                    .takeIf { it != entity.contentUriString }
                    ?: if (entity.id < 0) {
                        entity.contentUriString.removePrefix("youtube://")
                    } else {
                        entity.id.toString()
                    }
                com.unshoo.pixelmusic.data.model.youtube.Song(
                    youtubeId = yId,
                    title = entity.title,
                    artist = entity.artistName,
                    duration = entity.duration.toString(),
                    thumbnailHref = entity.albumArtUriString ?: "",
                    thumbnailPath = entity.albumArtUriString,
                    audioFilePath = entity.filePath
                )
            }
            if (ytSongs.isNotEmpty()) {
                songRepository.createAll(ytSongs)
                val refs = ytSongs.map { song ->
                    com.unshoo.pixelmusic.data.model.youtube.PlaylistSongCrossRef(playlistId, song.youtubeId)
                }
                playlistRepository.insertCrossRefs(refs)
            }
        } else {
            val existing = userPlaylistsFlow.first().find { it.id == playlistId } ?: return
            val merged = (existing.songIds + songIdsToAdd).distinct()
            updatePlaylist(existing.copy(songIds = merged))
        }
    }

    suspend fun addOrRemoveSongFromPlaylists(songId: String, playlistIds: List<String>): MutableList<String> {
        ensureMigratedIfNeeded()
        val currentPlaylists = userPlaylistsFlow.first()
        val removedPlaylistIds = mutableListOf<String>()

        currentPlaylists.forEach { playlist ->
            val shouldContain = playlist.id in playlistIds
            val hasSong = songId in playlist.songIds
            when {
                shouldContain && !hasSong -> {
                    addSongsToPlaylist(playlist.id, listOf(songId))
                }
                !shouldContain && hasSong -> {
                    removeSongFromPlaylist(playlist.id, songId)
                    removedPlaylistIds.add(playlist.id)
                }
            }
        }
        return removedPlaylistIds
    }

    suspend fun removeSongFromPlaylist(playlistId: String, songIdToRemove: String) {
        ensureMigratedIfNeeded()
        val ytPlaylist = AppDatabase.getInstance(context).playlistRepository().getPlaylistById(playlistId)
        if (ytPlaylist != null) {
            if (ytPlaylist.info.isDownloadedPlaylist) {
                val rawYtId = songIdToRemove.removePrefix("youtube_")
                DownloadRepository(context).deleteSong(rawYtId)
            } else {
                val rawYtId = songIdToRemove.removePrefix("youtube_")
                AppDatabase.getInstance(context).playlistRepository().deleteCrossRef(playlistId, rawYtId)
            }
        } else {
            val existing = userPlaylistsFlow.first().find { it.id == playlistId } ?: return
            updatePlaylist(existing.copy(songIds = existing.songIds.filterNot { it == songIdToRemove }))
        }
    }

    suspend fun reorderSongsInPlaylist(playlistId: String, newSongOrderIds: List<String>) {
        ensureMigratedIfNeeded()
        val existing = userPlaylistsFlow.first().find { it.id == playlistId } ?: return
        updatePlaylist(existing.copy(songIds = newSongOrderIds))
    }

    suspend fun setPlaylistSongOrderMode(playlistId: String, modeValue: String) =
        userPreferencesRepository.setPlaylistSongOrderMode(playlistId, modeValue)

    suspend fun clearPlaylistSongOrderMode(playlistId: String) =
        userPreferencesRepository.clearPlaylistSongOrderMode(playlistId)

    suspend fun setPlaylistSongOrderModes(modes: Map<String, String>) =
        userPreferencesRepository.setPlaylistSongOrderModes(modes)

    suspend fun setPlaylistsSortOption(optionKey: String) =
        userPreferencesRepository.setPlaylistsSortOption(optionKey)

    suspend fun setShowTelegramCloudPlaylists(show: Boolean) =
        userPreferencesRepository.setShowTelegramCloudPlaylists(show)

    suspend fun getPlaylistsOnce(): List<Playlist> {
        ensureMigratedIfNeeded()
        return userPlaylistsFlow.first()
    }

    suspend fun replaceAllPlaylists(playlists: List<Playlist>) {
        ensureMigratedIfNeeded()
        localPlaylistDao.replaceAllPlaylistsTransactional(
            playlists.map { playlist -> playlist.toEntity() to playlist.songIds }
        )
        userPreferencesRepository.clearLegacyUserPlaylists()
    }

    suspend fun removeSongFromAllPlaylists(songId: String) {
        ensureMigratedIfNeeded()
        val playlists = userPlaylistsFlow.first()
        playlists.forEach { playlist ->
            if (songId in playlist.songIds) {
                if (playlist.source == "YOUTUBE") {
                    removeSongFromPlaylist(playlist.id, songId)
                } else {
                    updatePlaylist(
                        playlist.copy(
                            songIds = playlist.songIds.filterNot { it == songId }
                        )
                    )
                }
            }
        }
    }

    suspend fun resetPlaylistPreferencesToDefaults() {
        setPlaylistSongOrderModes(emptyMap())
        setPlaylistsSortOption(SortOption.PlaylistNameAZ.storageKey)
    }

    private suspend fun ensureMigratedIfNeeded() {
        if (migrationChecked) return
        migrationMutex.withLock {
            if (migrationChecked) return
            val roomCount = localPlaylistDao.getPlaylistCount()
            if (roomCount == 0) {
                val legacy = userPreferencesRepository.getLegacyUserPlaylistsOnce()
                legacy.forEach { playlist ->
                    localPlaylistDao.upsertPlaylist(playlist.toEntity())
                    localPlaylistDao.replacePlaylistSongs(playlist.id, playlist.songIds)
                }
                if (legacy.isNotEmpty()) {
                    userPreferencesRepository.clearLegacyUserPlaylists()
                }
            }
            migrationChecked = true
        }
    }
}
