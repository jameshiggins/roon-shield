package com.roonshield.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.roonshield.ui.theme.RoonSurfaceVariant
import com.roonshield.ui.theme.RoonTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** In-memory cache of decoded album art, keyed by image_key + requested size. */
object AlbumArtCache {
    private val cache = object : LruCache<String, ImageBitmap>(64) {}

    suspend fun get(
        imageKey: String,
        width: Int,
        height: Int,
        fetch: suspend (String, Int, Int) -> ByteArray?,
    ): ImageBitmap? {
        val cacheKey = "$imageKey@${width}x$height"
        cache.get(cacheKey)?.let { return it }
        val bytes = fetch(imageKey, width, height) ?: return null
        val bitmap = withContext(Dispatchers.Default) {
            runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
        } ?: return null
        val img = bitmap.asImageBitmap()
        cache.put(cacheKey, img)
        return img
    }
}

@Composable
fun AlbumArt(
    imageKey: String?,
    sizePx: Int,
    modifier: Modifier = Modifier,
    showPlaceholder: Boolean = true,
    fetch: suspend (String, Int, Int) -> ByteArray?,
) {
    val image by produceState<ImageBitmap?>(initialValue = null, imageKey, sizePx) {
        value = imageKey
            ?.takeIf { it.isNotEmpty() }
            ?.let { AlbumArtCache.get(it, sizePx, sizePx, fetch) }
    }
    val bg = if (showPlaceholder) RoonSurfaceVariant else androidx.compose.ui.graphics.Color.Transparent
    Box(modifier = modifier.background(bg), contentAlignment = Alignment.Center) {
        val img = image
        when {
            img != null -> Image(
                bitmap = img,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            showPlaceholder -> Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = RoonTextSecondary,
            )
        }
    }
}
