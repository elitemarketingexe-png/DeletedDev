package com.unshoo.pixelmusic.utils

import android.content.Context
import dalvik.system.DexClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object JapaneseDictionaryManager {

    const val DICTIONARY_URL = "https://repo1.maven.org/maven2/com/atilika/kuromoji/kuromoji-ipadic/0.9.0/kuromoji-ipadic-0.9.0.jar"
    private const val DICTIONARY_FILE_NAME = "kuromoji-ipadic.jar"

    sealed class DownloadState {
        data object Idle : DownloadState()
        data class Downloading(val progressPercent: Int) : DownloadState()
        data object Installed : DownloadState()
        data class Error(val message: String) : DownloadState()
    }

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private fun getDictionaryFile(context: Context): File {
        val dir = File(context.filesDir, "dictionaries")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, DICTIONARY_FILE_NAME)
    }

    fun isDictionaryInstalled(context: Context): Boolean {
        val file = getDictionaryFile(context)
        return file.exists() && file.length() > 5_000_000L
    }

    fun initAtAppStart(context: Context) {
        if (isDictionaryInstalled(context)) {
            loadDictionaryIntoRomanizer(context)
            _downloadState.value = DownloadState.Installed
        } else {
            _downloadState.value = DownloadState.Idle
        }
    }

    suspend fun downloadDictionary(context: Context): Boolean = withContext(Dispatchers.IO) {
        val targetFile = getDictionaryFile(context)
        val tempFile = File(targetFile.parentFile, "$DICTIONARY_FILE_NAME.tmp")
        
        try {
            _downloadState.value = DownloadState.Downloading(0)
            val url = URL(DICTIONARY_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                requestMethod = "GET"
            }
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                _downloadState.value = DownloadState.Error("Server returned code ${connection.responseCode}")
                return@withContext false
            }

            val fileLength = connection.contentLength
            val inputStream = connection.inputStream
            val outputStream = FileOutputStream(tempFile)

            val buffer = ByteArray(8 * 1024)
            var totalBytesRead = 0L
            var bytesRead: Int
            var lastReportedPercent = 0

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                if (fileLength > 0) {
                    val percent = ((totalBytesRead * 100) / fileLength).toInt()
                    if (percent != lastReportedPercent) {
                        lastReportedPercent = percent
                        _downloadState.value = DownloadState.Downloading(percent)
                    }
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()
            connection.disconnect()

            if (tempFile.exists()) {
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
            }

            loadDictionaryIntoRomanizer(context)
            _downloadState.value = DownloadState.Installed
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to download Japanese dictionary pack")
            if (tempFile.exists()) tempFile.delete()
            _downloadState.value = DownloadState.Error(e.localizedMessage ?: "Download failed")
            false
        }
    }

    fun deleteDictionary(context: Context): Boolean {
        return try {
            val file = getDictionaryFile(context)
            if (file.exists()) {
                file.delete()
            }
            MultiLangRomanizer.setDynamicDictionaryClassLoader(null)
            _downloadState.value = DownloadState.Idle
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete Japanese dictionary pack")
            false
        }
    }

    private fun loadDictionaryIntoRomanizer(context: Context) {
        try {
            val file = getDictionaryFile(context)
            if (!file.exists()) return

            val dexClassLoader = DexClassLoader(
                file.absolutePath,
                context.codeCacheDir.absolutePath,
                null,
                context.classLoader
            )
            MultiLangRomanizer.setDynamicDictionaryClassLoader(dexClassLoader)
            Timber.i("Japanese Kuromoji dictionary pack loaded dynamically.")
        } catch (e: Throwable) {
            Timber.e(e, "Failed to load Kuromoji dictionary into DexClassLoader")
        }
    }
}
