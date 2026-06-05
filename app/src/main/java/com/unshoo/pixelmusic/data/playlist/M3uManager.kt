package com.unshoo.pixelmusic.data.playlist

import android.content.Context
import android.net.Uri
import com.unshoo.pixelmusic.data.model.Playlist
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.repository.MusicRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class M3uManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository
) {

    private fun getSongExportPath(song: Song): String {
        val youtubeId = song.youtubeId 
            ?: if (song.id.startsWith("youtube_")) song.id.substringAfter("youtube_")
               else if (song.contentUriString.startsWith("youtube://")) song.contentUriString.substringAfter("youtube://")
               else null
        return if (youtubeId != null) {
            "youtube://$youtubeId"
        } else {
            song.path
        }
    }

    private fun parseYoutubeId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.startsWith("youtube://")) {
            return trimmed.substringAfter("youtube://").trim().takeIf { it.isNotEmpty() }
        }
        
        // Match YouTube URL patterns:
        // - y2u.be/ID
        // - youtu.be/ID
        // - youtube.com/watch?v=ID
        // - music.youtube.com/watch?v=ID
        // - youtube.com/embed/ID
        // - youtube.com/v/ID
        val regex = Regex(
            """(?:https?://)?(?:www\.|music\.)?(?:youtube\.com/(?:watch\?v=|embed/|v/)|youtu\.be/|y2u\.be/)([a-zA-Z0-9_-]{11})""",
            RegexOption.IGNORE_CASE
        )
        return regex.find(trimmed)?.groupValues?.getOrNull(1)
    }

    suspend fun parseM3u(uri: Uri): Pair<String, List<String>> {
        val songIds = mutableListOf<String>()
        var playlistName = "Imported Playlist"

        // Load a filtered one-shot snapshot so import respects the current library visibility rules.
        val allSongs = musicRepository.getAllSongsOnce()
        
        // Build lookup maps for fast matching
        val songsByPath = allSongs.associateBy { it.path }
        val songsByFileName = allSongs.groupBy { it.path.substringAfterLast("/") }
        val songsByContentUriFileName = allSongs.groupBy { it.contentUriString.substringAfterLast("/") }
        val songsByYoutubeId = allSongs.filter { it.youtubeId != null }.associateBy { it.youtubeId!! }

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                var line: String?
                var lastExtInf: String? = null
                while (reader.readLine().also { line = it } != null) {
                    val trimmedLine = line?.trim() ?: continue
                    if (trimmedLine.isEmpty()) {
                        continue
                    }
                    if (trimmedLine.startsWith("#EXTINF:")) {
                        lastExtInf = trimmedLine
                        continue
                    }
                    if (trimmedLine.startsWith("#")) {
                        continue
                    }
                    
                    val youtubeId = parseYoutubeId(trimmedLine)
                    if (youtubeId != null) {
                        val songId = "youtube_$youtubeId"
                        val existingSong = songsByYoutubeId[youtubeId] ?: allSongs.find { it.id == songId || it.contentUriString == "youtube://$youtubeId" }
                        if (existingSong != null) {
                            songIds.add(existingSong.id)
                        } else {
                            // Extract info from EXTINF line if present
                            var title = "YouTube Song"
                            var artist = "Unknown Artist"
                            var durationMs = 0L
                            lastExtInf?.let { extInf ->
                                val durationPart = extInf.substringAfter(":", "").substringBefore(",", "")
                                val secs = durationPart.toLongOrNull() ?: 0L
                                durationMs = secs * 1000L
                                
                                val metaPart = extInf.substringAfter(",", "")
                                if (metaPart.contains(" - ")) {
                                    artist = metaPart.substringBefore(" - ").trim()
                                    title = metaPart.substringAfter(" - ").trim()
                                } else if (metaPart.isNotBlank()) {
                                    title = metaPart.trim()
                                }
                            }
                            
                            val newSong = Song(
                                id = songId,
                                title = title,
                                artist = artist,
                                artistId = 0L,
                                album = "YouTube Music",
                                albumId = 0L,
                                path = "",
                                contentUriString = "youtube://$youtubeId",
                                albumArtUriString = null,
                                duration = durationMs,
                                genre = "YouTube",
                                mimeType = "audio/webm",
                                bitrate = 128,
                                sampleRate = 44100,
                                youtubeId = youtubeId
                            )
                            musicRepository.insertYoutubeSongs(listOf(newSong))
                            songIds.add(songId)
                        }
                        continue
                    }
                    
                    val songByPath = songsByPath[trimmedLine]
                    if (songByPath != null) {
                        songIds.add(songByPath.id)
                    } else {
                        val fileName = trimmedLine.substringAfterLast("/")
                        val matchedSong = songsByFileName[fileName]?.firstOrNull()
                            ?: songsByContentUriFileName[fileName]?.firstOrNull()
                        if (matchedSong != null) {
                            songIds.add(matchedSong.id)
                        }
                    }
                }
            }
        }

        // Try to get the filename as playlist name
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                playlistName = cursor.getString(nameIndex).removeSuffix(".m3u").removeSuffix(".m3u8")
            }
        }

        return Pair(playlistName, songIds)
    }

    fun generateM3u(playlist: Playlist, songs: List<Song>): String {
        val sb = StringBuilder()
        sb.append("#EXTM3U\n")
        for (song in songs) {
            sb.append("#EXTINF:${song.duration / 1000},${song.artist} - ${song.title}\n")
            sb.append("${getSongExportPath(song)}\n")
        }
        return sb.toString()
    }

    // ---------------------------------------------------------------------------
    // CSV support
    // ---------------------------------------------------------------------------

    private fun escapeCsv(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            '"' + value.replace("\"", "\"\"") + '"'
        } else value
    }

    fun generateCsv(songs: List<Song>): String {
        val sb = StringBuilder()
        sb.append("Title,Artist,Album,Duration (ms),Path\n")
        for (song in songs) {
            sb.append(escapeCsv(song.title)).append(',')
            sb.append(escapeCsv(song.artist)).append(',')
            sb.append(escapeCsv(song.album)).append(',')
            sb.append(song.duration).append(',')
            sb.append(escapeCsv(getSongExportPath(song))).append('\n')
        }
        return sb.toString()
    }

    suspend fun parseCsv(uri: Uri): Pair<String, List<String>> {
        val songIds = mutableListOf<String>()
        var playlistName = "Imported Playlist"

        val allSongs = musicRepository.getAllSongsOnce()
        val songsByPath = allSongs.associateBy { it.path }
        val songsByTitle = allSongs.groupBy { it.title.lowercase() }
        val songsByFileName = allSongs.groupBy { it.path.substringAfterLast("/") }
        val songsByYoutubeId = allSongs.filter { it.youtubeId != null }.associateBy { it.youtubeId!! }

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                var isFirstLine = true
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val trimmedLine = line?.trim() ?: continue
                    if (trimmedLine.isEmpty()) continue
                    // Skip CSV header
                    if (isFirstLine) {
                        isFirstLine = false
                        continue
                    }
                    // Parse CSV row – columns: Title,Artist,Album,Duration,Path
                    val cols = splitCsvRow(trimmedLine)
                    val path = cols.getOrNull(4)?.trim() ?: ""
                    val title = cols.getOrNull(0)?.trim() ?: ""
                    val artist = cols.getOrNull(1)?.trim() ?: ""
                    val album = cols.getOrNull(2)?.trim() ?: ""
                    val durationStr = cols.getOrNull(3)?.trim() ?: ""

                    // Check if path is a YouTube song
                    val youtubeId = if (path.isNotBlank()) parseYoutubeId(path) else null
                    if (youtubeId != null) {
                        val songId = "youtube_$youtubeId"
                        val existingSong = songsByYoutubeId[youtubeId] ?: allSongs.find { it.id == songId || it.contentUriString == "youtube://$youtubeId" }
                        if (existingSong != null) {
                            songIds.add(existingSong.id)
                        } else {
                            val durationMs = durationStr.toLongOrNull() ?: 0L
                            val newSong = Song(
                                id = songId,
                                title = title.ifBlank { "YouTube Song" },
                                artist = artist.ifBlank { "Unknown Artist" },
                                artistId = 0L,
                                album = album.ifBlank { "YouTube Music" },
                                albumId = 0L,
                                path = "",
                                contentUriString = "youtube://$youtubeId",
                                albumArtUriString = null,
                                duration = durationMs,
                                genre = "YouTube",
                                mimeType = "audio/webm",
                                bitrate = 128,
                                sampleRate = 44100,
                                youtubeId = youtubeId
                            )
                            musicRepository.insertYoutubeSongs(listOf(newSong))
                            songIds.add(songId)
                        }
                        continue
                    }

                    // 1) Try exact path match
                    val byPath = if (path.isNotBlank()) songsByPath[path] else null
                    if (byPath != null) {
                        songIds.add(byPath.id)
                        continue
                    }
                    // 2) Try filename match from path column
                    if (path.isNotBlank()) {
                        val fileName = path.substringAfterLast("/")
                        val byFile = songsByFileName[fileName]?.firstOrNull()
                        if (byFile != null) {
                            songIds.add(byFile.id)
                            continue
                        }
                    }
                    // 3) Fall back to title match
                    if (title.isNotBlank()) {
                        val byTitle = songsByTitle[title.lowercase()]?.firstOrNull()
                        if (byTitle != null) {
                            songIds.add(byTitle.id)
                        }
                    }
                }
            }
        }

        // Derive playlist name from file name
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                playlistName = cursor.getString(nameIndex).removeSuffix(".csv")
            }
        }

        return Pair(playlistName, songIds)
    }

    /** Minimal CSV row splitter that handles double-quoted fields. */
    private fun splitCsvRow(row: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < row.length) {
            val c = row[i]
            when {
                c == '"' && !inQuotes -> inQuotes = true
                c == '"' && inQuotes && i + 1 < row.length && row[i + 1] == '"' -> {
                    current.append('"'); i++ // escaped quote
                }
                c == '"' && inQuotes -> inQuotes = false
                c == ',' && !inQuotes -> { result.add(current.toString()); current.clear() }
                else -> current.append(c)
            }
            i++
        }
        result.add(current.toString())
        return result
    }
}
