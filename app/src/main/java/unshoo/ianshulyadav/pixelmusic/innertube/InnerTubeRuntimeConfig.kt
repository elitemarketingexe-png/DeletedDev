/*
 * PixelMusic (2026)
 * © Chartreux Westia — github.com/ianshulyadav
 * GPL-3.0 License
 */

package unshoo.ianshulyadav.pixelmusic.innertube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Runtime InnerTube configuration bootstrap (LastWave pattern).
 *
 * WEB_REMIX's baked-in `clientVersion` goes stale whenever YouTube ships a new
 * web build, and a stale version is a common cause of 400/403 playback
 * rejections. This object fetches the live values from the music.youtube.com
 * shell page — `INNERTUBE_CLIENT_VERSION` and `VISITOR_DATA` — and caches them
 * for [CONFIG_TTL_MS]. [invalidate] is called (throttled) when InnerTube calls
 * fail, so a rejected version self-heals on the next request instead of
 * failing until an app update.
 *
 * All values are optional: when the bootstrap has not run (or failed), every
 * consumer falls back to the compiled-in constants, so behaviour is never
 * worse than before this existed.
 */
object InnerTubeRuntimeConfig {

    private const val MUSIC_SHELL_URL = "https://music.youtube.com/"
    private const val SHELL_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    private const val CONFIG_TTL_MS = 60L * 60 * 1000          // refresh hourly
    private const val INVALIDATE_THROTTLE_MS = 10L * 60 * 1000 // at most one refetch / 10 min
    private const val FETCH_TIMEOUT_SECONDS = 8L

    @Volatile
    var currentClientVersion: String? = null
        private set

    @Volatile
    var currentVisitorData: String? = null
        private set

    @Volatile
    private var fetchedAtMs = 0L

    @Volatile
    private var lastInvalidateAtMs = 0L

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Fire-and-forget bootstrap, safe to call from Application startup.
     * Never blocks the caller and never throws.
     */
    fun warmAsync() {
        scope.launch { runCatching { ensureFresh(forceIfExpired = true) } }
    }

    /**
     * Fetches and caches the live shell-page config when the cache is empty or
     * older than [CONFIG_TTL_MS]. Pass [forceIfExpired] = false to fetch only
     * when nothing is cached at all.
     */
    suspend fun ensureFresh(forceIfExpired: Boolean = true) {
        val now = System.currentTimeMillis()
        if (currentClientVersion != null &&
            !forceIfExpired &&
            now - fetchedAtMs < CONFIG_TTL_MS
        ) return
        if (currentClientVersion != null && now - fetchedAtMs < CONFIG_TTL_MS) return

        mutex.withLock {
            val innerNow = System.currentTimeMillis()
            if (currentClientVersion != null && innerNow - fetchedAtMs < CONFIG_TTL_MS) return
            val html = withContext(Dispatchers.IO) {
                runCatching {
                    httpClient.newCall(
                        Request.Builder()
                            .url(MUSIC_SHELL_URL)
                            .header("User-Agent", SHELL_USER_AGENT)
                            .header("Accept-Language", "en-US,en;q=0.9")
                            .build()
                    ).execute().use { response ->
                        response.body?.string()?.takeIf { response.isSuccessful }
                    }
                }.getOrNull()
            } ?: return
            val version = findConfig(html, "INNERTUBE_CLIENT_VERSION")
                ?: findConfig(html, "INNERTUBE_CONTEXT_CLIENT_VERSION")
            if (version.isNullOrBlank()) return
            currentClientVersion = version
            currentVisitorData = findConfig(html, "VISITOR_DATA")
            fetchedAtMs = System.currentTimeMillis()
        }
    }

    /**
     * Drop the cached config and refetch in the background. Called after
     * InnerTube requests fail (HTTP 400/403/429 or unparseable responses) so a
     * freshly-rejected clientVersion is replaced before the next attempt.
     * Throttled so a burst of failures cannot hammer music.youtube.com.
     */
    fun invalidate() {
        val now = System.currentTimeMillis()
        if (currentClientVersion == null && currentVisitorData == null) return
        if (now - lastInvalidateAtMs < INVALIDATE_THROTTLE_MS) return
        lastInvalidateAtMs = now
        currentClientVersion = null
        currentVisitorData = null
        scope.launch { runCatching { ensureFresh(forceIfExpired = true) } }
    }

    /** Extracts "key": "value" pairs from the shell page's inline config. */
    private fun findConfig(html: String, key: String): String? {
        if (html.isBlank()) return null
        val escaped = Regex("\"$key\"\\s*:\\s*\"([^\"]+)\"")
            .find(html)?.groupValues?.getOrNull(1) ?: return null
        return escaped
            .replace("\\u003d", "=")
            .replace("\\/", "/")
    }
}
