package com.unshoo.pixelmusic.data.remote.youtube

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
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
        val tempFiles = mutableListOf<File>()

        try {
            (0 until connections).map { i ->
                async {
                    val start = i * chunkSize
                    val end = if (i == connections - 1) total - 1 else (start + chunkSize - 1)
                    val temp = File(audioDir, "${song.youtubeId}.part$i")

                    try {
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
                    } catch (e: Exception) {
                        temp.delete()
                        throw e
                    }
                }
            }.awaitAll().also { tempFiles.addAll(it) }

            FileOutputStream(outputFile).use { out ->
                tempFiles.sortedBy { it.name }.forEach { part ->
                    part.inputStream().use { it.copyTo(out) }
                    part.delete()
                }
            }

            enforceStorageLimit(context, keepFile = outputFile)
            return@withContext outputFile.absolutePath

        } catch (e: Exception) {
            UmihiHelper.printe("Download failed for ${song.youtubeId}: ${e.message}")
            tempFiles.forEach { it.delete() }
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

    fun copyToPublicDownload(context: Context, sourceFilePath: String, songTitle: String, artistName: String): File? {
        try {
            val sourceFile = File(sourceFilePath)
            if (!sourceFile.exists()) return null

            val safeTitle = songTitle.replace(Regex("[\\\\/:*?\"\\<>|]"), "_")
            val safeArtist = artistName.replace(Regex("[\\\\/:*?\"\\<>|]"), "_")
            val fileName = "$safeTitle - $safeArtist.webm"

            val publicDownloadDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "PixelMusic"
            )
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
