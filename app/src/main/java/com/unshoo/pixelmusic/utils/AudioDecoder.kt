package com.unshoo.pixelmusic.utils

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.nio.ByteBuffer

object AudioDecoder {

    private const val TIMEOUT_US = 1000L
    private const val ENCODING_PCM_16BIT = 2
    private const val ENCODING_PCM_FLOAT = 4

    suspend fun decodeToFloatArray(context: Context, uri: Uri, requiredSamples: Int): Result<FloatArray> = withContext(Dispatchers.IO) {
        runCatching {
            val extractor = MediaExtractor()
            extractor.setDataSource(context, uri, null)

            val trackIndex = findAudioTrack(extractor)
            if (trackIndex == -1) {
                extractor.release()
                error("No audio track found in the file.")
            }
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)

            val mime = format.getString(MediaFormat.KEY_MIME) ?: error("MIME type not found.")
            val decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            var pcmData = FloatArray(requiredSamples)
            var pcmSize = 0
            val bufferInfo = MediaCodec.BufferInfo()
            var isEndOfStream = false

            while (!isEndOfStream && pcmSize < requiredSamples) {
                val inputBufferIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputBufferIndex)
                    if (inputBuffer == null) {
                        Timber.tag("AudioDecoder").w("Decoder input buffer was null, ending decode early")
                        decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        isEndOfStream = true
                        continue
                    }
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        isEndOfStream = true
                    } else {
                        decoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }

                var outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                while (outputBufferIndex >= 0) {
                    val outputBuffer = decoder.getOutputBuffer(outputBufferIndex)
                    if (outputBuffer == null) {
                        Timber.tag("AudioDecoder").w("Decoder output buffer was null, skipping chunk")
                        decoder.releaseOutputBuffer(outputBufferIndex, false)
                        outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                        continue
                    }
                    val chunk = byteBufferToFloatArray(outputBuffer, format)
                    decoder.releaseOutputBuffer(outputBufferIndex, false)

                    val copyCount = Math.min(chunk.size, requiredSamples - pcmSize)
                    if (copyCount > 0) {
                        if (pcmSize + copyCount > pcmData.size) {
                            pcmData = pcmData.copyOf(Math.max(pcmData.size * 2, pcmSize + copyCount))
                        }
                        System.arraycopy(chunk, 0, pcmData, pcmSize, copyCount)
                        pcmSize += copyCount
                    }

                    if (pcmSize >= requiredSamples) break

                    outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                }
            }

            decoder.stop()
            decoder.release()
            extractor.release()

            Timber.tag("AudioDecoder").d("Successfully decoded $pcmSize samples.")

            // Return exact FloatArray slice of requiredSamples (silence padded if shorter)
            val finalArray = FloatArray(requiredSamples)
            if (pcmSize > 0) {
                System.arraycopy(pcmData, 0, finalArray, 0, Math.min(pcmSize, requiredSamples))
            }
            finalArray
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) {
                return i
            }
        }
        return -1
    }

    private fun byteBufferToFloatArray(buffer: ByteBuffer, format: MediaFormat): FloatArray {
        val pcmEncoding = format.getInteger(MediaFormat.KEY_PCM_ENCODING, ENCODING_PCM_16BIT)
        buffer.rewind()

        return when (pcmEncoding) {
            ENCODING_PCM_16BIT -> {
                val shortBuffer = buffer.asShortBuffer()
                FloatArray(shortBuffer.remaining()) {
                    shortBuffer.get().toFloat() / Short.MAX_VALUE
                }
            }
            ENCODING_PCM_FLOAT -> {
                val floatBuffer = buffer.asFloatBuffer()
                FloatArray(floatBuffer.remaining()) { floatBuffer.get() }
            }
            else -> throw UnsupportedOperationException("Unsupported PCM encoding: $pcmEncoding")
        }
    }
}
