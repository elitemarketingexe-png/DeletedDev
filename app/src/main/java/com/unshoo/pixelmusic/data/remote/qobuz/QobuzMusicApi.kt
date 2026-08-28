/*
 * PixelMusic (2026)
 * © Chartreux Westia — github.com/ianshulyadav
 * GPL-3.0 License
 *
 * Qobuz Hi-Res lossless tier, ported from LastWave-native's QobuzMusicApi
 * (GPL-3.0, Clash-Projects). Requires the self-contained Cloudflare Worker in
 * `qobuz-worker-backend/` — deploy it, then set QOBUZ_BACKEND_URL (and
 * optionally QOBUZ_API_KEY) in local.properties. See docs/qobuz-backend.md.
 */

package com.unshoo.pixelmusic.data.remote.qobuz

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit

@Serializable
data class QobuzAudioStream(
    val url: String,
    val mimeType: String = "audio/flac",
    val bitDepth: Int = 16,
    val samplingRate: Double = 44.1,
    val formatId: Int = 6,
    val bitrateKbps: Int? = null,
    val trackId: Long = 0,
)

@Serializable
private data class QobuzSearchResponse(
    val success: Boolean = false,
    val results: QobuzSearchResults? = null,
)

@Serializable
private data class QobuzSearchResults(
    val tracks: QobuzTrackList? = null,
)

@Serializable
private data class QobuzTrackList(
    val items: List<QobuzTrackItem> = emptyList(),
)

@Serializable
private data class QobuzTrackItem(
    val id: Long,
    val title: String,
    val duration: Int = 0,
    val version: String? = null,
    val performer: QobuzPerformer? = null,
    val performers: String? = null,
    val album: QobuzAlbumInfo? = null,
    val hires: Boolean = false,
    @SerialName("maximum_bit_depth")
    val maxBitDepth: Int? = null,
    @SerialName("maximum_sampling_rate")
    val maxSamplingRate: Double? = null,
)

@Serializable
private data class QobuzPerformer(
    val name: String,
    val id: Long = 0,
)

@Serializable
private data class QobuzAlbumInfo(
    val title: String? = null,
    val artist: QobuzPerformer? = null,
)

@Serializable
private data class QobuzTrackUrlResponse(
    val success: Boolean = false,
    val data: QobuzTrackUrlData? = null,
    val error: String? = null,
)

@Serializable
private data class QobuzTrackUrlData(
    val url: String? = null,
    @SerialName("format_id")
    val formatId: Int = 6,
    @SerialName("mime_type")
    val mimeType: String = "audio/flac",
    @SerialName("sampling_rate")
    val samplingRate: Double = 44.1,
    @SerialName("bit_depth")
    val bitDepth: Int = 16,
    val duration: Int = 0,
)

/**
 * Strict client for the Qobuz worker backend. Every stream handed back has
 * passed title-, identity-variant-, artist- and duration-level verification;
 * anything less returns null so playback safely falls back to YouTube Music.
 */
object QobuzMusicApi {

    const val QUALITY_MAX_HI_RES = 27 // Up to 24-bit / 192 kHz
    const val QUALITY_HI_RES_96 = 7   // Up to 24-bit / 96 kHz
    const val QUALITY_CD_LOSSLESS = 6 // 16-bit / 44.1 kHz FLAC
    const val QUALITY_MP3_320 = 5     // 320 kbps MP3

    private const val TAG = "QobuzMusicApi"
    private const val MAX_DURATION_DIFFERENCE_SECONDS = 8
    private const val SEARCH_LIMIT = 15

    /** Deployed worker endpoint (empty = Qobuz tier disabled). */
    val backendBaseUrl: String
        get() = com.unshoo.pixelmusic.BuildConfig.QOBUZ_BACKEND_URL.trimEnd('/')

    private val backendApiKey: String
        get() = com.unshoo.pixelmusic.BuildConfig.QOBUZ_API_KEY

    /** The tier only activates when a worker endpoint has been configured. */
    val isConfigured: Boolean
        get() = backendBaseUrl.startsWith("http")

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // ── matching regexes (LastWave anti-mismatch suite) ─────────────────────
    private val DIACRITICS = Regex("\\p{M}+")
    private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")
    private val MULTI_SPACE = Regex("\\s+")
    private val FEATURING_CLAUSE = Regex("""(?i)(?:\s*[\[(])?\s*(feat\.?|ft\.?|featuring)\s+.*$""")
    private val BRACKETED_DISPLAY_NOISE = Regex(
        """(?i)[\[(]\s*(?:official\s+)?(?:music\s+)?(?:audio|video|lyrics?|lyric\s+video|visualizer|hd|4k)\s*(?:\]|\))""",
    )
    private val TRAILING_DISPLAY_NOISE = Regex(
        """(?i)\s*[-–—]\s*(?:official\s+)?(?:music\s+)?(?:audio|video|lyrics?|visualizer)\s*$""",
    )
    private val ARTIST_NOISE_WORDS = setOf("the", "and", "feat", "ft", "featuring", "with", "x")
    private val PERFORMING_ROLE_WORDS = setOf(
        "mainartist", "featuredartist", "performer", "vocal", "vocals", "vocalist", "singer",
    )
    private val IDENTITY_VARIANT_PATTERNS = listOf(
        "live" to Regex("\\blive\\b"),
        "acoustic" to Regex("\\bacoustic\\b"),
        "karaoke" to Regex("\\bkaraoke\\b"),
        "instrumental" to Regex("\\binstrumental\\b"),
        "tribute" to Regex("\\btribute\\b"),
        "cover" to Regex("\\bcover\\b"),
        "remix" to Regex("\\bremix(?:ed)?\\b"),
        "mashup" to Regex("""\bmash[ -]?up\b|\b[a-z0-9]+\s+x\s+[a-z0-9]+\b"""),
        "demo" to Regex("\\bdemo\\b"),
        "slowed" to Regex("\\bslowed\\b"),
        "reverb" to Regex("\\breverb\\b"),
        "sped-up" to Regex("\\bsped up\\b"),
        "nightcore" to Regex("\\bnightcore\\b"),
        "radio-edit" to Regex("\\bradio edit\\b"),
        "extended" to Regex("\\bextended(?: version| mix)?\\b"),
    )

    /**
     * Resolves a high-confidence, verified Qobuz direct CDN audio stream URL.
     * Returns null whenever no high-confidence exact match is found so the
     * caller falls back to YouTube Music without a visible delay.
     */
    suspend fun resolveStream(
        title: String,
        artist: String,
        expectedDurationSeconds: Int? = null,
        expectedAlbum: String? = null,
        preferredQuality: Int = QUALITY_MAX_HI_RES,
    ): QobuzAudioStream? = withContext(Dispatchers.IO) {
        if (!isConfigured || title.isBlank() || artist.isBlank()) return@withContext null

        try {
            val candidate = findBestVerifiedMatch(
                title = title,
                artist = artist,
                expectedDurationSeconds = expectedDurationSeconds,
                expectedAlbum = expectedAlbum,
            ) ?: return@withContext null

            val urlBuilder = "$backendBaseUrl/api/track/${candidate.id}/url"
                .toHttpUrlOrNull()
                ?.newBuilder()
                ?: return@withContext null
            urlBuilder.addQueryParameter("quality", preferredQuality.toString())
            urlBuilder.addQueryParameter("fallback", "true")

            val requestBuilder = Request.Builder().url(urlBuilder.build()).get()
            if (backendApiKey.isNotBlank()) requestBuilder.addHeader("X-API-Key", backendApiKey)

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val parsed = json.decodeFromString<QobuzTrackUrlResponse>(body)
                if (!parsed.success) return@withContext null
                val data = parsed.data ?: return@withContext null
                val streamUrl = data.url ?: return@withContext null

                val bitrateKbps = when (data.formatId) {
                    QUALITY_MAX_HI_RES, QUALITY_HI_RES_96 ->
                        ((data.bitDepth * data.samplingRate * 2 * 1000) / 1000).toInt()
                    QUALITY_CD_LOSSLESS -> 1411
                    QUALITY_MP3_320 -> 320
                    else -> null
                }

                QobuzAudioStream(
                    url = streamUrl,
                    mimeType = data.mimeType.ifBlank { "audio/flac" },
                    bitDepth = data.bitDepth.takeIf { it > 0 } ?: 16,
                    samplingRate = data.samplingRate.takeIf { it > 0 } ?: 44.1,
                    formatId = data.formatId,
                    bitrateKbps = bitrateKbps,
                    trackId = candidate.id,
                )
            }
        } catch (e: Exception) {
            android.util.Log.d(TAG, "Qobuz resolution failed gracefully: ${e.message}")
            null
        }
    }

    // ── search + strict verification ────────────────────────────────────────

    private fun findBestVerifiedMatch(
        title: String,
        artist: String,
        expectedDurationSeconds: Int?,
        expectedAlbum: String?,
    ): QobuzTrackItem? {
        val cleanTitle = cleanForSearch(title)
        val cleanArtist = cleanForSearch(artist)

        // Progressive queries, but never relaxed identity verification.
        val queries = listOfNotNull(
            "$cleanTitle $cleanArtist".trim().takeIf { it.isNotBlank() },
            "$cleanArtist $cleanTitle".trim().takeIf { it.isNotBlank() },
            cleanTitle.takeIf { it.isNotBlank() },
            title.trim().takeIf { it.isNotBlank() },
        ).distinct()

        for (query in queries) {
            val urlBuilder = "$backendBaseUrl/api/search"
                .toHttpUrlOrNull()
                ?.newBuilder() ?: continue
            urlBuilder.addQueryParameter("q", query)
            urlBuilder.addQueryParameter("type", "track")
            urlBuilder.addQueryParameter("limit", SEARCH_LIMIT.toString())

            val reqBuilder = Request.Builder().url(urlBuilder.build()).get()
            if (backendApiKey.isNotBlank()) reqBuilder.addHeader("X-API-Key", backendApiKey)

            val items = try {
                client.newCall(reqBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) return@use emptyList<QobuzTrackItem>()
                    val body = response.body?.string() ?: return@use emptyList<QobuzTrackItem>()
                    val searchRes = json.decodeFromString<QobuzSearchResponse>(body)
                    if (searchRes.success) searchRes.results?.tracks?.items.orEmpty() else emptyList()
                }
            } catch (e: Exception) {
                emptyList()
            }

            if (items.isEmpty()) continue

            items.asSequence()
                .mapNotNull { item ->
                    verifiedMatchScore(
                        item = item,
                        title = title,
                        artist = artist,
                        expectedDurationSeconds = expectedDurationSeconds,
                        expectedAlbum = expectedAlbum,
                    )?.let { score -> item to score }
                }
                .maxByOrNull { it.second }
                ?.first
                ?.let { return it }
        }

        return null
    }

    private fun verifiedMatchScore(
        item: QobuzTrackItem,
        title: String,
        artist: String,
        expectedDurationSeconds: Int?,
        expectedAlbum: String?,
    ): Int? {
        val targetTitle = normalizeTitle(title, artist)
        val candidateTitle = normalizeTitle(item.title, artist)
        if (targetTitle.isBlank() || targetTitle != candidateTitle) return null

        val targetVariants = identityVariants(title, artist)
        val candidateVariants = identityVariants("${item.title} ${item.version.orEmpty()}", artist)
        if (targetVariants != candidateVariants) return null

        val performer = item.performer?.name.orEmpty()
        val albumArtist = item.album?.artist?.name.orEmpty()
        if (!isVerifiedArtistMatch(artist, performer, albumArtist, item.performers)) return null

        val durationDifference = if (expectedDurationSeconds != null && expectedDurationSeconds > 0) {
            if (item.duration <= 0) return null
            kotlin.math.abs(item.duration - expectedDurationSeconds).also {
                if (it > MAX_DURATION_DIFFERENCE_SECONDS) return null
            }
        } else null

        var score = 1_000
        val normalizedArtist = normalizeText(artist)
        if (listOf(performer, albumArtist).any { normalizeText(it) == normalizedArtist }) score += 300
        expectedAlbum?.takeIf(String::isNotBlank)?.let { album ->
            if (normalizeTitle(album, "") == normalizeTitle(item.album?.title.orEmpty(), "")) score += 120
        }
        durationDifference?.let { score += (MAX_DURATION_DIFFERENCE_SECONDS - it) * 10 }
        return score
    }

    // ── normalization helpers ───────────────────────────────────────────────

    private fun cleanForSearch(raw: String): String {
        return raw
            .replace(FEATURING_CLAUSE, " ")
            .replace(BRACKETED_DISPLAY_NOISE, " ")
            .replace(TRAILING_DISPLAY_NOISE, " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun normalizeTitle(raw: String, artist: String): String {
        val withoutArtistPrefix = if (artist.isBlank()) raw else raw.replaceFirst(
            Regex("""^\s*${Regex.escape(artist)}\s*[-–—:]\s*""", RegexOption.IGNORE_CASE),
            "",
        )
        return normalizeText(cleanForSearch(withoutArtistPrefix))
    }

    private fun normalizeText(raw: String): String = Normalizer.normalize(raw, Normalizer.Form.NFD)
        .replace(DIACRITICS, "")
        .lowercase(Locale.ROOT)
        .replace(NON_ALPHANUMERIC, " ")
        .replace(MULTI_SPACE, " ")
        .trim()

    private fun identityVariants(raw: String, artist: String): Set<String> {
        val withoutArtistPrefix = if (artist.isBlank()) raw else raw.replaceFirst(
            Regex("""^\s*${Regex.escape(artist)}\s*[-–—:]\s*""", RegexOption.IGNORE_CASE),
            "",
        )
        val normalized = normalizeText(withoutArtistPrefix)
        return IDENTITY_VARIANT_PATTERNS.mapNotNullTo(linkedSetOf()) { (name, pattern) ->
            name.takeIf { pattern.containsMatchIn(normalized) }
        }
    }

    private fun isVerifiedArtistMatch(
        targetArtist: String,
        performer: String,
        albumArtist: String,
        performersText: String?,
    ): Boolean {
        val target = normalizeText(targetArtist)
        if (target.isBlank()) return false
        val primaryIdentities = listOf(performer, albumArtist)
            .map(::normalizeText)
            .filter(String::isNotBlank)
        if (primaryIdentities.any { it == target }) return true

        val targetTokens = target.split(' ').filter { it !in ARTIST_NOISE_WORDS }.toSet()
        if (targetTokens.isEmpty()) return false
        if (primaryIdentities.any { identity -> targetTokens.all(identity.split(' ').toSet()::contains) }) {
            return true
        }

        // Qobuz's performers string also lists writers/producers. Only credits
        // explicitly labelled as a performing role count — a songwriter's name
        // must never make a cover look like the requested artist.
        val performingCredits = performersText.orEmpty()
            .split(Regex("""\s+-\s+"""))
            .map(::normalizeText)
            .filter { credit -> PERFORMING_ROLE_WORDS.any { role -> role in credit.split(' ') } }
        val performingTokens = (primaryIdentities + performingCredits)
            .flatMap { it.split(' ') }
            .toSet()
        return targetTokens.all(performingTokens::contains)
    }
}
