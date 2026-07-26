package com.unshoo.pixelmusic.data.remote.youtube

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.datastore.preferences.core.intPreferencesKey
import com.unshoo.pixelmusic.data.model.youtube.Song
import com.unshoo.pixelmusic.data.preferences.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import kotlin.coroutines.cancellation.CancellationException

object DownloadHelper {
    private val client = YoutubeHelper.client

    suspend fun downloadImage(context: Context, imageUrl: String, id: String): File? {
        return withContext(Dispatchers.IO) {
            try {
                val imageDir =
                    UmihiHelper.getDownloadDirectory(context, Constants.Downloads.THUMBNAILS_FOLDER)
                val imageFile = File(imageDir, "$id.jpg")

                if (imageFile.exists()) {
                    UmihiHelper.printd("Song Image $id was already downloaded")
                    return@withContext imageFile
                }

                URL(imageUrl).openStream().use { input ->
                    imageFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                imageFile

            } catch (e: Exception) {
                UmihiHelper.printe(
                    tag = "PlaylistDownloadWorker",
                    message = "Error Downloading Thumbnail",
                    exception = e
                )
                null
            }
        }
    }

    suspend fun downloadAudio(
        context: Context,
        song: Song,
        connections: Int = 8
    ): String? = withContext(Dispatchers.IO) {

        val repo = DatastoreRepository(context)
        val customPath = repo.customDownloadPath.first()
        val safeTitle = song.title.replace(Regex("[\\\\/:*?\"\\<>|]"), "_")
        val safeArtist = song.artist.replace(Regex("[\\\\/:*?\"\\<>|]"), "_")
        val fileName = "$safeTitle - $safeArtist.webm"

        if (customPath.isNotBlank()) {
            try {
                val treeUri = Uri.parse(customPath)
                val documentDir = DocumentFile.fromTreeUri(context, treeUri)
                val existingFile = documentDir?.findFile(fileName)
                if (existingFile != null && existingFile.exists()) {
                    return@withContext existingFile.uri.toString()
                }
            } catch (e: Exception) {
                UmihiHelper.printe("Error checking custom download path exists: ${e.message}")
            }
        }

        val audioDir =
            UmihiHelper.getDownloadDirectory(context, Constants.Downloads.AUDIO_FILES_FOLDER)
        val outputFile = File(audioDir, "${song.youtubeId}.webm")

        if (outputFile.exists()) {
            return@withContext outputFile.absolutePath
        }

        val url = YoutubeHelper.getSongPlayerUrl(context, song)

        val total = try {
            val headReq = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-0")
                .build()

            client.newCall(headReq).execute().use { headRes ->
                if (!headRes.isSuccessful) {
                    return@withContext null
                }
                headRes.headers["Content-Range"]
                    ?.substringAfter("/")
                    ?.toLongOrNull()
                    ?: return@withContext null
            }
        } catch (e: Exception) {
            UmihiHelper.printe("Failed to get content length: ${e.message}")
            return@withContext null
        }

        val chunkSize = total / connections
        // Pre-compute every chunk's temp file path up front (instead of only tracking
        // successfully-awaited results). This guarantees we always know the full set of
        // files that *could* have been written, so cleanup on cancel/failure is complete
        // even when some chunks finished before a sibling chunk failed or the download
        // was cancelled. Previously, only chunks captured by a completed `awaitAll()` were
        // tracked, so any chunk that finished successfully right before a cancellation or a
        // sibling failure was silently leaked to disk forever (never cleaned up, since
        // enforceStorageLimit only runs after a *successful* download).
        val tempFiles = (0 until connections).map { i -> File(audioDir, "${song.youtubeId}.part$i") }

        fun cleanupTempFiles() {
            tempFiles.forEach { it.delete() }
        }

        try {
            (0 until connections).map { i ->
                async {
                    val start = i * chunkSize
                    val end = if (i == connections - 1) total - 1 else (start + chunkSize - 1)
                    val temp = tempFiles[i]

                    val req = Request.Builder()
                        .url(url)
                        .header("Range", "bytes=$start-$end")
                        .header("User-Agent", Constants.YoutubeApi.USER_AGENT)
                        .build()

                    client.newCall(req).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw IOException("Failed to download chunk $i: ${response.code}")
                        }

                        response.body?.byteStream()?.use { input ->
                            FileOutputStream(temp).use { output ->
                                input.copyTo(output)
                            }
                        } ?: throw IOException("Empty response body for chunk $i")
                    }

                    temp
                }
            }.awaitAll()

            if (customPath.isNotBlank()) {
                val treeUri = Uri.parse(customPath)
                val documentDir = DocumentFile.fromTreeUri(context, treeUri)
                val file = documentDir?.createFile("audio/webm", fileName)
                val outputUri = file?.uri
                if (outputUri != null) {
                    context.contentResolver.openOutputStream(outputUri)?.use { out ->
                        tempFiles.sortedBy { it.name }.forEach { part ->
                            part.inputStream().use { it.copyTo(out) }
                            part.delete()
                        }
                    }
                    return@withContext outputUri.toString()
                }
            }

            FileOutputStream(outputFile).use { out ->
                tempFiles.sortedBy { it.name }.forEach { part ->
                    part.inputStream().use { it.copyTo(out) }
                    part.delete()
                }
            }

            enforceStorageLimit(context, keepFile = outputFile)
            return@withContext outputFile.absolutePath

        } catch (e: CancellationException) {
            // Never swallow cancellation: doing so breaks structured concurrency and can make
            // a user-initiated pause/cancel look like a normal function return to callers
            // (e.g. WorkManager workers up the stack), which previously caused false
            // "download failed" states. Clean up partial files, then rethrow so cancellation
            // propagates correctly.
            UmihiHelper.printd("Download cancelled for ${song.youtubeId}, cleaning up partial files")
            cleanupTempFiles()
            outputFile.delete()
            throw e
        } catch (e: Exception) {
            UmihiHelper.printe("Download failed for ${song.youtubeId}: ${e.message}")
            cleanupTempFiles()
            outputFile.delete()
            return@withContext null
        }
    }

    private suspend fun enforceStorageLimit(context: Context, keepFile: File? = null) = withContext(Dispatchers.IO) {
        val limitMb = runCatching {
            context.dataStore.data.first()[intPreferencesKey("storage_limit_mb")] ?: 1536
        }.getOrDefault(1536).coerceIn(0, 10240)
        if (limitMb <= 0) return@withContext

        val audioDir = UmihiHelper.getDownloadDirectory(context, Constants.Downloads.AUDIO_FILES_FOLDER)
        val imageDir = UmihiHelper.getDownloadDirectory(context, Constants.Downloads.THUMBNAILS_FOLDER)
        val limitBytes = limitMb.toLong() * 1024L * 1024L

        fun allCacheFiles(): List<File> = listOf(audioDir, imageDir)
            .flatMap { dir -> dir.listFiles()?.filter { it.isFile } ?: emptyList() }

        var files = allCacheFiles()
        var totalBytes = files.sumOf { it.length() }
        if (totalBytes <= limitBytes) return@withContext

        files.sortedBy { it.lastModified().takeIf { ts -> ts > 0L } ?: Long.MIN_VALUE }
            .forEach { file ->
                if (totalBytes <= limitBytes) return@forEach
                if (keepFile != null && file.absolutePath == keepFile.absolutePath) return@forEach
                val size = file.length()
                if (file.delete()) totalBytes -= size
            }
    }

    suspend fun copyToPublicDownload(context: Context, sourceFilePath: String, songTitle: String, artistName: String): File? {
        try {
            val sourceFile = File(sourceFilePath)
            if (!sourceFile.exists()) return null

            val safeTitle = songTitle.replace(Regex("[\\\\/:*?\"\\<>|]"), "_")
            val safeArtist = artistName.replace(Regex("[\\\\/:*?\"\\<>|]"), "_")
            val fileName = "$safeTitle - $safeArtist.webm"

            val repo = DatastoreRepository(context)
            val customPath = repo.customDownloadPath.first()
            val publicDownloadDir = if (customPath.isNotBlank()) {
                File(customPath)
            } else {
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "PixelMusic")
            }
            if (!publicDownloadDir.exists()) {
                publicDownloadDir.mkdirs()
            }
            val destinationFile = File(publicDownloadDir, fileName)

            sourceFile.inputStream().use { input ->
                destinationFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            MediaScannerConnection.scanFile(
                context,
                arrayOf(destinationFile.absolutePath),
                arrayOf("audio/webm"),
                null
            )

            return destinationFile
        } catch (e: Exception) {
            UmihiHelper.printe("Failed to copy to public downloads: ${e.message}", exception = e)
            return null
        }
    }
}
