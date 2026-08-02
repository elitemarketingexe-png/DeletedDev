package com.unshoo.pixelmusic.utils

import android.media.MediaMetadataRetriever
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Thread-safe pool of MediaMetadataRetriever instances to avoid costly creation/destruction
 * during mass scanning operations. Each retriever is reset between uses.
 * 
 * Usage:
 * ```kotlin
 * MediaMetadataRetrieverPool.withRetriever { retriever ->
 *     retriever.setDataSource(filePath)
 *     retriever.embeddedPicture
 * }
 * ```
 */
object MediaMetadataRetrieverPool {
    
    private const val MAX_POOL_SIZE = 4
    private val pool = ConcurrentLinkedQueue<MediaMetadataRetriever>()
    private val poolCount = AtomicInteger(0)
    private val createdCount = AtomicInteger(0)
    
    /**
     * Acquires a MediaMetadataRetriever from the pool, or creates a new one if pool is empty.
     * The caller is responsible for returning it via [release].
     */
    @PublishedApi
    internal fun acquire(): MediaMetadataRetriever {
        val retriever = pool.poll()
        if (retriever != null) {
            poolCount.decrementAndGet()
            return retriever
        }
        createdCount.incrementAndGet()
        return MediaMetadataRetriever()
    }
    
    /**
     * Returns a MediaMetadataRetriever to the pool for reuse.
     * If the pool is full, the retriever is released instead.
     */
    @PublishedApi
    internal fun release(retriever: MediaMetadataRetriever) {
        if (poolCount.get() < MAX_POOL_SIZE) {
            pool.offer(retriever)
            poolCount.incrementAndGet()
        } else {
            try {
                retriever.release()
            } catch (_: Exception) {
                // Ignore release errors
            }
            createdCount.decrementAndGet()
        }
    }
    
    /**
     * Executes the given block with a pooled MediaMetadataRetriever.
     * Automatically handles acquiring and returning the retriever.
     * 
     * @param block The operation to perform with the retriever
     * @return The result of the block, or null if an error occurred
     */
    inline fun <T> withRetriever(block: (MediaMetadataRetriever) -> T): T? {
        val retriever = acquire()
        return try {
            block(retriever)
        } catch (e: Exception) {
            null
        } finally {
            release(retriever)
        }
    }
    
    /**
     * Clears all pooled retrievers. Call this when the app is low on memory.
     */
    fun clear() {
        var retriever = pool.poll()
        while (retriever != null) {
            poolCount.decrementAndGet()
            try {
                retriever.release()
            } catch (_: Exception) {
                // Ignore release errors
            }
            createdCount.decrementAndGet()
            retriever = pool.poll()
        }
    }
    
    /**
     * Returns the current number of retrievers held in the pool.
     */
    fun poolSize(): Int = poolCount.get()
    
    /**
     * Returns the total number of retrievers created (including those in use).
     */
    fun totalCreated(): Int = createdCount.get()
}
