package com.unshoo.pixelmusic.data.image

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import coil.fetch.DrawableResult
import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.unshoo.pixelmusic.data.telegram.TelegramRepository
import okio.Path.Companion.toPath
import org.drinkless.tdlib.TdApi
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

import com.unshoo.pixelmusic.data.network.deezer.DeezerApiService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.ConcurrentHashMap

/**
 * Custom Coil Fetcher for Telegram album art.
 * Handles URIs in format: telegram_art://chatId/messageId
 * 
 * Optimized to prevent blocking the UI thread for high-quality art downloads/searches.
 * Returns minithumbnail immediately and upgrades to high-quality in background.
 */
class TelegramCoilFetcher(
    private val context: Context,
    private val uri: Uri,
    private val telegramRepository: TelegramRepository,
    private val cacheDir: File,
    private val telegramCacheManager: com.unshoo.pixelmusic.data.telegram.TelegramCacheManager?,
    private val deezerApiService: DeezerApiService
) : Fetcher {

    companion object {
        private val recentlyLoggedFailures = ConcurrentHashMap<String, Long>()
        private const val LOG_FAILURE_COOLDOWN_MS = 60_000L
        private val extractionMapMutex = Mutex()
        private val extractionLocks = ConcurrentHashMap<String, Mutex>()

        private fun shouldLogFailure(key: String): Boolean {
            val now = System.currentTimeMillis()
            val lastLogged = recentlyLoggedFailures[key]
            return if (lastLogged == null || now - lastLogged > LOG_FAILURE_COOLDOWN_MS) {
                recentlyLoggedFailures[key] = now
                true
            } else false
        }
    }

    override suspend fun fetch(): FetchResult? {
        val chatId = uri.host?.toLongOrNull()
        val messageId = uri.pathSegments.firstOrNull()?.toLongOrNull()
        if (chatId == null || messageId == null) return null

        val key = "${chatId}_${messageId}"
        val cachedDeezerFile = File(cacheDir, "telegram_deezer_art_${key}.jpg")
        val cachedArtFile = File(cacheDir, "telegram_embedded_art_${key}.jpg")
        val persistentArtFile = telegramCacheManager?.getPersistentArtFile(chatId, messageId)

        // 1. FAST PATH: Return cached high-quality files immediately
        if (persistentArtFile?.exists() == true && persistentArtFile.length() > 0) {
            return SourceResult(
                source = coil.decode.ImageSource(persistentArtFile.absolutePath.toPath(), okio.FileSystem.SYSTEM),
                mimeType = "image/jpeg",
                dataSource = DataSource.DISK
            )
        }

        if (cachedDeezerFile.exists() && cachedDeezerFile.length() > 0) {
            return SourceResult(
                source = coil.decode.ImageSource(cachedDeezerFile.absolutePath.toPath(), okio.FileSystem.SYSTEM),
                mimeType = "image/jpeg",
                dataSource = DataSource.DISK
            )
        }

        if (cachedArtFile.exists() && cachedArtFile.length() > 0) {
            return SourceResult(
                source = coil.decode.ImageSource(cachedArtFile.absolutePath.toPath(), okio.FileSystem.SYSTEM),
                mimeType = "image/jpeg",
                dataSource = DataSource.DISK
            )
        }

        // 2. CHECK TDLib: If high-quality thumbnail is already downloaded
        val message = telegramRepository.getMessage(chatId, messageId)
        if (message != null) {
            val fileId = extractFileIdFromContent(message.content)
            if (fileId != null) {
                val file = telegramRepository.getFile(fileId)
                if (file?.local?.isDownloadingCompleted == true && !file.local.path.isNullOrEmpty()) {
                    return SourceResult(
                        source = coil.decode.ImageSource(file.local.path.toPath(), okio.FileSystem.SYSTEM),
                        mimeType = null,
                        dataSource = DataSource.DISK
                    )
                }
            }
        }

        // 3. FALLBACK: Return minithumbnail immediately if available
        if (message != null) {
            val minithumbnailData = extractMinithumbnail(message.content)
            if (minithumbnailData != null) {
                val bitmap = BitmapFactory.decodeByteArray(minithumbnailData, 0, minithumbnailData.size)
                if (bitmap != null) {
                    // Trigger background refinement now that we've committed to a low-res preview
                    telegramRepository.enqueueHighQualityArtFetch(chatId, messageId)
                    
                    return DrawableResult(
                        drawable = BitmapDrawable(context.resources, bitmap),
                        isSampled = true,
                        dataSource = DataSource.MEMORY
                    )
                }
            }
        }

        // 4. DEEP FETCH: Only for first load without even a minithumbnail
        // We'll allow a short 2s window for a quick Deezer or Embedded search.
        val result = withTimeoutOrNull(2000L) {
             performDeepFetch(chatId, messageId, key, cachedDeezerFile, cachedArtFile)
        }
        
        // If deep fetch failed/timed out, still ensure background task is running
        if (result == null) {
            telegramRepository.enqueueHighQualityArtFetch(chatId, messageId)
        }

        return result
    }

    private suspend fun performDeepFetch(
        chatId: Long,
        messageId: Long,
        key: String,
        cachedDeezerFile: File,
        cachedArtFile: File
    ): FetchResult? {
        val noDeezerMarker = File(cacheDir, "telegram_deezer_art_${key}_none")
        if (!noDeezerMarker.exists()) {
            val message = telegramRepository.getMessage(chatId, messageId)
            if (message != null) {
                val title = when (val content = message.content) {
                    is TdApi.MessageAudio -> content.audio.title ?: ""
                    is TdApi.MessageDocument -> content.document.fileName ?: ""
                    else -> ""
                }
                val artist = (message.content as? TdApi.MessageAudio)?.audio?.performer ?: ""
                
                val cleanedTitle = cleanTitle(title)
                if (cleanedTitle.isNotBlank()) {
                    try {
                        val query = if (artist.isNotBlank() && artist != "Unknown Artist") {
                            "track:\"$cleanedTitle\" artist:\"$artist\""
                        } else cleanedTitle
                        
                        val searchResponse = deezerApiService.searchTrack(query, limit = 1)
                        val coverUrl = searchResponse.data.firstOrNull()?.album?.let { 
                            it.coverXl ?: it.coverBig ?: it.coverMedium ?: it.cover
                        }

                        if (coverUrl != null) {
                            val upgradedUrl = upgradeToHighResDeezerUrl(coverUrl)
                            if (downloadDeezerCover(upgradedUrl, cachedDeezerFile)) {
                                return SourceResult(
                                    source = coil.decode.ImageSource(cachedDeezerFile.absolutePath.toPath(), okio.FileSystem.SYSTEM),
                                    mimeType = "image/jpeg",
                                    dataSource = DataSource.NETWORK
                                )
                            }
                        }
                        noDeezerMarker.createNewFile()
                    } catch (_: Exception) {}
                }
            }
        }

        // Try embedded art extraction
        val embeddedArtPath = tryExtractEmbeddedArtIfSafe(chatId, messageId)
        if (embeddedArtPath != null) {
            return SourceResult(
                source = coil.decode.ImageSource(embeddedArtPath.toPath(), okio.FileSystem.SYSTEM),
                mimeType = "image/jpeg",
                dataSource = DataSource.DISK
            )
        }

        return null
    }

    private fun extractMinithumbnail(content: TdApi.MessageContent): ByteArray? {
        return when (content) {
            is TdApi.MessageAudio -> content.audio.albumCoverMinithumbnail?.data
            is TdApi.MessageDocument -> content.document.minithumbnail?.data
            else -> null
        }
    }

    private suspend fun tryExtractEmbeddedArtIfSafe(chatId: Long, messageId: Long): String? {
        val key = "${chatId}_${messageId}"
        val cachedArtFile = File(cacheDir, "telegram_embedded_art_${key}.jpg")
        val noArtMarker = File(cacheDir, "telegram_embedded_art_${key}_none")

        if (cachedArtFile.exists() && cachedArtFile.length() > 0) return cachedArtFile.absolutePath
        if (noArtMarker.exists()) return null

        val lock = extractionMapMutex.withLock { extractionLocks.getOrPut(key) { Mutex() } }

        return lock.withLock {
            if (cachedArtFile.exists() && cachedArtFile.length() > 0) return@withLock cachedArtFile.absolutePath
            
            val message = telegramRepository.getMessage(chatId, messageId) ?: return@withLock null
            val audioFileId = when (val c = message.content) {
                is TdApi.MessageAudio -> c.audio.audio.id
                is TdApi.MessageDocument -> c.document.document.id
                else -> null
            } ?: return@withLock null

            val audioFile = telegramRepository.getFile(audioFileId)
            if (audioFile?.local?.isDownloadingCompleted != true || audioFile.local.path.isNullOrEmpty()) return@withLock null

            val extractedPath = extractAndCacheEmbeddedArt(audioFile.local.path, cachedArtFile, noArtMarker)
            if (extractedPath != null) {
                telegramCacheManager?.notifyEmbeddedArtExtracted(chatId, messageId)
            }
            extractedPath
        }
    }

    private fun extractAndCacheEmbeddedArt(audioFilePath: String, cacheFile: File, noArtMarker: File): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(audioFilePath)
            val pic = retriever.embeddedPicture
            if (pic != null && pic.isNotEmpty()) {
                // Validate image data before caching: inJustDecodeBounds only reads
                // dimensions without loading pixels — zero memory overhead.
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(pic, 0, pic.size, opts)
                if (opts.outWidth > 0 && opts.outHeight > 0) {
                    FileOutputStream(cacheFile).use { it.write(pic) }
                    cacheFile.absolutePath
                } else {
                    noArtMarker.createNewFile()
                    null
                }
            } else {
                noArtMarker.createNewFile()
                null
            }
        } catch (e: Exception) {
            noArtMarker.createNewFile()
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun extractFileIdFromContent(content: TdApi.MessageContent?): Int? {
        return when (content) {
            is TdApi.MessageAudio -> {
                val audio = content.audio
                val candidates = buildList {
                    audio.albumCoverThumbnail?.let(::add)
                    audio.externalAlbumCovers?.let(::addAll)
                }
                candidates.maxByOrNull { it.width * it.height }?.file?.id
            }
            is TdApi.MessageDocument -> content.document.thumbnail?.file?.id
            else -> null
        }
    }

    private fun cleanTitle(title: String): String = title
        .replace(Regex("\\.mp3$", RegexOption.IGNORE_CASE), "")
        .replace(Regex("^\\[\\d+]"), "")
        .replace(Regex("^\\d+\\s*-\\s*"), "")
        .trim()

    private val deezerSizeRegex = Regex("/\\d{2,4}x\\d{2,4}([\\-.])")
    private fun upgradeToHighResDeezerUrl(url: String): String = deezerSizeRegex.replace(url, "/1000x1000$1")

    private suspend fun downloadDeezerCover(url: String, outputFile: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            if (conn.responseCode == 200) {
                conn.inputStream.use { input -> FileOutputStream(outputFile).use { output -> input.copyTo(output) } }
                true
            } else false
        } catch (_: Exception) { false }
    }

    class Factory @Inject constructor(
        private val telegramRepository: TelegramRepository,
        private val telegramCacheManager: com.unshoo.pixelmusic.data.telegram.TelegramCacheManager,
        private val deezerApiService: DeezerApiService
    ) : Fetcher.Factory<Uri> {
        private var cacheDir: File? = null
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.scheme != "telegram_art") return null
            val cache = cacheDir ?: options.context.cacheDir.also { cacheDir = it }
            return TelegramCoilFetcher(options.context, data, telegramRepository, cache, telegramCacheManager, deezerApiService)
        }
    }
}
