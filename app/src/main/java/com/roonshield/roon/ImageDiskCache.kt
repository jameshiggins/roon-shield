package com.roonshield.roon

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * On-disk cache of raw art bytes. Roon image keys are content hashes, so an entry never goes
 * stale — once fetched, art loads from flash instead of a Core round-trip (which is what makes
 * dense grids and playlist mosaics slow on a cold start).
 */
class ImageDiskCache(context: Context) {
    private val dir = File(context.cacheDir, "art").apply { mkdirs() }

    private fun file(imageKey: String, width: Int, height: Int) =
        File(dir, imageKey.replace(Regex("[^A-Za-z0-9]"), "_") + "_${width}x$height")

    suspend fun get(imageKey: String, width: Int, height: Int): ByteArray? = withContext(Dispatchers.IO) {
        val f = file(imageKey, width, height)
        if (!f.exists()) return@withContext null
        f.setLastModified(System.currentTimeMillis()) // LRU order for trim()
        runCatching { f.readBytes() }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    suspend fun put(imageKey: String, width: Int, height: Int, bytes: ByteArray) = withContext(Dispatchers.IO) {
        runCatching {
            val f = file(imageKey, width, height)
            val tmp = File(dir, f.name + ".tmp")
            tmp.writeBytes(bytes)
            tmp.renameTo(f)
        }
        Unit
    }

    /** Drop least-recently-used files once the cache passes [MAX_BYTES]. */
    suspend fun trim() = withContext(Dispatchers.IO) {
        val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: return@withContext
        var total = files.sumOf { it.length() }
        if (total <= MAX_BYTES) return@withContext
        for (f in files) {
            if (total <= TRIM_TO_BYTES) break
            total -= f.length()
            f.delete()
        }
    }

    private companion object {
        const val MAX_BYTES = 256L * 1024 * 1024
        const val TRIM_TO_BYTES = 192L * 1024 * 1024
    }
}
