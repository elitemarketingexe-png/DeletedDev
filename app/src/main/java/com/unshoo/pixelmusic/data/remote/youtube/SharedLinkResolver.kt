/*
 * PixelMusic (2026)
 * © Chartreux Westia — github.com/ianshulyadav
 * GPL-3.0 License
 */

package com.unshoo.pixelmusic.data.remote.youtube

import android.net.Uri
import com.unshoo.pixelmusic.data.model.youtube.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import unshoo.ianshulyadav.pixelmusic.innertube.YouTube
import unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.YouTubeClient.Companion.WEB_REMIX

/**
 * Shared-link instant playback (LastWave LinkPlaybackResolver pattern).
 *
 * Turns a link the user tapped or shared — youtube.com / youtu.be /
 * music.youtube.com watch URLs, YouTube playlist URLs, or open.spotify.com /
 * spotify.link track links — straight into a playable [Song] (or playlist of
 * songs) with zero manual search. YouTube links are authoritative (video id
 * from the URL); Spotify links are matched by querying the track title via
 * Spotify's public oEmbed endpoint and then searching YouTube.
 */
object SharedLinkResolver {

    private val YOUTUBE_HOSTS = setOf(
        "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com",
        "youtu.be", "www.youtu.be",
    )
    private val SPOTIFY_HOSTS = setOf(
        "open.spotify.com", "spotify.link", "open.spotify.link",
    )

    sealed class Result {
        data class Single(val song: Song, val queue: List<Song> = listOf(song)) : Result()
        data class Playlist(val title: String, val songs: List<Song>) : Result()
    }

    fun isSupportedLink(uri: Uri?): Boolean {
        if (uri == null) return false
        if (uri.scheme != "https" && uri.scheme != "http") return false
        val host = uri.host?.lowercase().orEmpty()
        return host in YOUTUBE_HOSTS || host in SPOTIFY_HOSTS
    }

    /** Pulls the first http(s) URL out of shared text (ACTION_SEND). */
    fun extractUrlFromText(text: String?): Uri? {
        if (text.isNullOrBlank()) return null
        return android.util.Patterns.WEB_URL
            .matcher(text)
            .toRegex()
            ?.findAll(text)
            ?.firstOrNull()
            ?.value
            ?.let { Uri.parse(it) }
            ?.takeIf(::isSupportedLink)
    }

    fun extractYoutubeVideoId(uri: Uri): String? {
        val host = uri.host?.lowercase().orEmpty()
        if (host !in YOUTUBE_HOSTS) return null
        // youtu.be/<id>
        if (host.endsWith("youtu.be")) {
            return uri.pathSegments?.firstOrNull()?.takeIf { it.length >= 8 }
        }
        // .../watch?v=<id>
        uri.getQueryParameter("v")?.takeIf { it.isNotBlank() }?.let { return it }
        // .../shorts/<id>, /live/<id>, /embed/<id>
        val segments = uri.pathSegments.orEmpty()
        val marker = segments.indexOfFirst { it in setOf("shorts", "live", "embed", "v") }
        if (marker != -1) {
            return segments.getOrNull(marker + 1)?.takeIf { it.length >= 8 }
        }
        return null
    }

    fun extractPlaylistId(uri: Uri): String? =
        uri.getQueryParameter("list")?.takeIf { it.isNotBlank() && !it.startsWith("RD") }

    /**
     * Resolves a supported link into a playable result. Returns null when the
     * link cannot be matched — callers show a graceful failure toast.
     */
    suspend fun resolve(uri: Uri): Result? = withContext(Dispatchers.IO) {
        val host = uri.host?.lowercase().orEmpty()
        when {
            host in YOUTUBE_HOSTS -> resolveYoutube(uri)
            host in SPOTIFY_HOSTS -> resolveSpotify(uri)
            else -> null
        }
    }

    private suspend fun resolveYoutube(uri: Uri): Result? {
        val videoId = extractYoutubeVideoId(uri)
        val playlistId = extractPlaylistId(uri)

        // Watch link (possibly with &list=): play the video itself.
        if (videoId != null) {
            return resolveYoutubeVideo(videoId)
        }

        // Playlist-only link: import the playlist's loaded page as a queue.
        if (playlistId != null) {
            val page = runCatching { YouTube.playlist(playlistId).getOrNull() }.getOrNull()
                ?: return null
            val songs = page.songs
                .map { it.toYoutubeSong() }
                .filter { it.youtubeId.isNotBlank() }
            if (songs.isEmpty()) return null
            return Result.Playlist(
                title = page.playlist.title.ifBlank { "Shared Playlist" },
                songs = songs,
            )
        }

        return null
    }

    private suspend fun resolveYoutubeVideo(videoId: String): Result? {
        // Metadata comes straight from the player response's videoDetails so the
        // queue shows the real title/artist instead of a blank row.
        val details = runCatching {
            YouTube.player(videoId = videoId, client = WEB_REMIX).getOrNull()?.videoDetails
        }.getOrNull()
        val song = if (details != null && details.title.isNotBlank()) {
            val seconds = details.lengthSeconds?.toIntOrNull() ?: 0
            Song(
                youtubeId = videoId,
                title = details.title,
                artist = details.author.orEmpty(),
                duration = formatDuration(seconds),
                thumbnailHref = details.thumbnail?.thumbnails?.lastOrNull()?.url.orEmpty(),
            )
        } else {
            Song(youtubeId = videoId)
        }
        return Result.Single(song = song)
    }

    private suspend fun resolveSpotify(uri: Uri): Result? = withContext(Dispatchers.IO) {
        // 1. Spotify oEmbed gives us the canonical "<Artist> - <Title>"-ish text
        //    without any authentication.
        val oembedJson = runCatching {
            val endpoint = "https://open.spotify.com/oembed?url=${Uri.encode(uri.toString())}"
            YoutubeHelper.client.newCall(
                okhttp3.Request.Builder()
                    .url(endpoint)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36")
                    .build()
            ).execute().use { response ->
                response.body?.string()?.takeIf { response.isSuccessful }
            }
        }.getOrNull() ?: return@withContext null
        val title = runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(oembedJson)
                .let { it as? kotlinx.serialization.json.JsonObject }
                ?.get("title")
                ?.let { it as? kotlinx.serialization.json.JsonPrimitive }
                ?.content
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: return@withContext null

        // 2. Search YouTube for the track and take the top song result.
        val result = runCatching {
            YouTube.search(title, YouTube.SearchFilter.FILTER_SONG).getOrNull()
        }.getOrNull() ?: return@withContext null
        val best = result?.items
            ?.filterIsInstance<SongItem>()
            ?.firstOrNull { it.id.isNotBlank() }
            ?: return@withContext null
        Result.Single(song = best.toYoutubeSong())
    }

    private fun formatDuration(totalSeconds: Int): String {
        if (totalSeconds <= 0) return ""
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        else String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
    }
}
