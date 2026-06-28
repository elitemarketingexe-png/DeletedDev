package com.unshoo.pixelmusic.data.backup.module

import kotlin.math.absoluteValue
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.unshoo.pixelmusic.data.backup.model.BackupSection
import com.unshoo.pixelmusic.data.database.FavoritesDao
import com.unshoo.pixelmusic.data.database.FavoritesEntity
import com.unshoo.pixelmusic.data.database.MusicDao
import com.unshoo.pixelmusic.di.BackupGson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoritesModuleHandler @Inject constructor(
    private val favoritesDao: FavoritesDao,
    private val musicDao: MusicDao,
    @BackupGson private val gson: Gson
) : BackupModuleHandler {

    override val section = BackupSection.FAVORITES

    override suspend fun export(): String = withContext(Dispatchers.IO) {
        val favorites = favoritesDao.getAllFavoritesOnce()
        val allSongs = musicDao.getAllSongsList()
        val songById = allSongs.associateBy { it.id }
        
        val songMetadata = mutableMapOf<String, SongMetadataEntry>()
        favorites.forEach { fav ->
            val songIdStr = fav.songId.toString()
            songById[fav.songId]?.let { summary ->
                val videoId = if (summary.sourceType == com.unshoo.pixelmusic.data.database.SourceType.YOUTUBE) {
                    summary.contentUriString.substringAfter("youtube://")
                } else {
                    null
                }
                songMetadata[songIdStr] = SongMetadataEntry(
                    title = summary.title,
                    artist = summary.artistName,
                    album = summary.albumName,
                    duration = summary.duration,
                    youtubeId = videoId,
                    contentUriString = summary.contentUriString,
                    albumArtUriString = summary.albumArtUriString,
                    path = summary.filePath,
                    sourceType = summary.sourceType
                )
            }
        }
        val payload = FavoritesBackupPayload(
            favorites = favorites,
            songMetadata = songMetadata.ifEmpty { null }
        )
        gson.toJson(payload)
    }

    override suspend fun countEntries(): Int = withContext(Dispatchers.IO) {
        favoritesDao.getAllFavoritesOnce().size
    }

    override suspend fun snapshot(): String = export()

    override suspend fun restore(payload: String) = withContext(Dispatchers.IO) {
        val element = JsonParser.parseString(payload)
        val favorites: List<FavoritesEntity>
        var songMetadata: Map<String, SongMetadataEntry>? = null
        
        if (element.isJsonArray) {
            val type = TypeToken.getParameterized(List::class.java, FavoritesEntity::class.java).type
            favorites = gson.fromJson(payload, type)
        } else {
            val parsed = runCatching {
                gson.fromJson(payload, FavoritesBackupPayload::class.java)
            }.getOrNull() ?: FavoritesBackupPayload()
            favorites = parsed.favorites.orEmpty()
            songMetadata = parsed.songMetadata
        }
        
        if (songMetadata != null && songMetadata.isNotEmpty()) {
            val localSongs = musicDao.getAllSongsList()
            val currentSongsById = localSongs.associateBy { it.id.toString() }
            
            val songsToInsert = mutableListOf<com.unshoo.pixelmusic.data.database.SongEntity>()
            val albumsToInsert = mutableListOf<com.unshoo.pixelmusic.data.database.AlbumEntity>()
            val artistsToInsert = mutableListOf<com.unshoo.pixelmusic.data.database.ArtistEntity>()
            val crossRefsToInsert = mutableListOf<com.unshoo.pixelmusic.data.database.SongArtistCrossRef>()
            
            songMetadata.forEach { (songIdStr, entry) ->
                val isYoutube = entry.sourceType == 4 || entry.youtubeId != null || entry.contentUriString?.startsWith("youtube://") == true
                if (isYoutube && !currentSongsById.containsKey(songIdStr)) {
                    val songId = songIdStr.toLongOrNull() ?: return@forEach
                    val videoId = entry.youtubeId ?: entry.contentUriString?.substringAfter("youtube://") ?: return@forEach
                    
                    val albumName = entry.album.ifBlank { "YouTube Music" }
                    val albumId = -(16_000_000_000_000L + albumName.lowercase().hashCode().toLong().absoluteValue)
                    
                    val artistNames = com.unshoo.pixelmusic.data.stream.CloudMusicUtils.parseArtistNames(entry.artist)
                    val primaryArtistName = artistNames.firstOrNull() ?: "Unknown Artist"
                    val primaryArtistId = -(17_000_000_000_000L + primaryArtistName.lowercase().hashCode().toLong().absoluteValue)
                    
                    artistNames.forEachIndexed { index, name ->
                        val artistId = -(17_000_000_000_000L + name.lowercase().hashCode().toLong().absoluteValue)
                        artistsToInsert.add(
                            com.unshoo.pixelmusic.data.database.ArtistEntity(
                                id = artistId,
                                name = name,
                                trackCount = 1,
                                imageUrl = null
                            )
                        )
                        crossRefsToInsert.add(
                            com.unshoo.pixelmusic.data.database.SongArtistCrossRef(
                                songId = songId,
                                artistId = artistId,
                                isPrimary = index == 0
                            )
                        )
                    }
                    
                    albumsToInsert.add(
                        com.unshoo.pixelmusic.data.database.AlbumEntity(
                            id = albumId,
                            title = albumName,
                            artistName = primaryArtistName,
                            artistId = primaryArtistId,
                            songCount = 1,
                            dateAdded = System.currentTimeMillis(),
                            year = 0,
                            albumArtUriString = entry.albumArtUriString
                        )
                    )
                    
                    val artistRefs = artistNames.mapIndexed { idx, name ->
                        com.unshoo.pixelmusic.data.model.ArtistRef(
                            id = -(17_000_000_000_000L + name.lowercase().hashCode().toLong().absoluteValue),
                            name = name,
                            isPrimary = idx == 0
                        )
                    }
                    val artistsJson = try {
                        val arr = org.json.JSONArray()
                        artistRefs.forEach { ref ->
                            val obj = org.json.JSONObject()
                            obj.put("id", ref.id)
                            obj.put("name", ref.name)
                            obj.put("primary", ref.isPrimary)
                            arr.put(obj)
                        }
                        arr.toString()
                    } catch (e: Exception) {
                        null
                    }
                    
                    songsToInsert.add(
                        com.unshoo.pixelmusic.data.database.SongEntity(
                            id = songId,
                            title = entry.title,
                            artistName = entry.artist,
                            artistId = primaryArtistId,
                            albumArtist = null,
                            albumName = albumName,
                            albumId = albumId,
                            contentUriString = entry.contentUriString ?: "youtube://$videoId",
                            albumArtUriString = entry.albumArtUriString,
                            duration = entry.duration,
                            genre = "YouTube",
                            filePath = entry.path ?: "",
                            parentDirectoryPath = "/Cloud/YouTube",
                            isFavorite = true,
                            lyrics = null,
                            trackNumber = 0,
                            discNumber = null,
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
                    )
                }
            }
            
            if (songsToInsert.isNotEmpty()) {
                musicDao.incrementalSyncMusicData(
                    songs = songsToInsert,
                    albums = albumsToInsert.distinctBy { it.id },
                    artists = artistsToInsert.distinctBy { it.id },
                    crossRefs = crossRefsToInsert,
                    deletedSongIds = emptyList()
                )
            }
        }
        
        favoritesDao.replaceAll(favorites)
    }

    override suspend fun rollback(snapshot: String) = restore(snapshot)

    data class SongMetadataEntry(
        val title: String,
        val artist: String,
        val album: String,
        val duration: Long,
        val youtubeId: String? = null,
        val contentUriString: String? = null,
        val albumArtUriString: String? = null,
        val path: String? = null,
        val sourceType: Int? = null
    )

    private data class FavoritesBackupPayload(
        val favorites: List<FavoritesEntity>? = null,
        val songMetadata: Map<String, SongMetadataEntry>? = null
    )
}
