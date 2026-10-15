package com.roonshield.ui.nowplaying

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.roonshield.ui.components.AlbumArtCache
import com.roonshield.ui.theme.RoonSurfaceVariant
import com.roonshield.ui.theme.RoonTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.random.Random

/**
 * Now Playing album art as a brand-new LP sleeve — the copy you'd pull out of the rack in a shop,
 * not a second-hand one. The cover art printed on card stock: paper tooth, a little unevenness in
 * the board, a crisp cut edge and a soft drop shadow so it sits on the screen as an object rather
 * than as a flat picture. No wear of any kind.
 *
 * The texture is baked into one bitmap off the main thread and then simply blitted, so the
 * per-second seek-position recomposition on this screen never redraws it.
 */
@Composable
fun LpSleeveArt(
    imageKey: String?,
    sizePx: Int,
    fetch: suspend (String, Int, Int) -> ByteArray?,
    modifier: Modifier = Modifier,
) {
    val sleeve by produceState<ImageBitmap?>(initialValue = null, imageKey, sizePx) {
        val key = imageKey?.takeIf { it.isNotEmpty() } ?: return@produceState
        val src = AlbumArtCache.get(key, sizePx, sizePx, fetch) ?: return@produceState
        value = withContext(Dispatchers.Default) {
            runCatching { printedSleeve(src.asAndroidBitmap(), sizePx.coerceIn(240, 1280), key.hashCode()) }
                .onFailure { android.util.Log.w("LpSleeve", "sleeve render failed; showing plain art", it) }
                .getOrNull()?.asImageBitmap() ?: src
        }
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind { drawSleeveShadow() }
                .clip(RoundedCornerShape(2.dp)),
        ) {
            val img = sleeve
            if (img != null) {
                Image(
                    bitmap = img,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(RoonSurfaceVariant), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = RoonTextSecondary)
                }
            }
        }
    }
}

// ---- Shadow ----------------------------------------------------------------------------------

/**
 * A soft drop shadow, stacked by hand out of fading rounded rects. The platform's elevation shadow
 * all but vanishes against this screen's near-black backdrop, and a blur mask filter is a software
 * -rendering trick that doesn't belong on a live canvas — twenty cheap rects give a cleaner falloff
 * than either, and the sleeve covers the overdraw in the middle.
 */
private fun DrawScope.drawSleeveShadow() {
    val layers = 20
    val maxSpread = 20.dp.toPx()
    val drop = 7.dp.toPx()
    val radius = CornerRadius(4.dp.toPx())
    for (i in layers downTo 1) {
        val t = i / layers.toFloat() // 1 = the outermost, faintest ring
        val spread = maxSpread * t
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.055f * (1f - t) * (1f - t) + 0.012f),
            topLeft = Offset(-spread, -spread + drop),
            size = Size(size.width + spread * 2, size.height + spread * 2),
            cornerRadius = radius,
        )
    }
}

// ---- The printed sleeve ----------------------------------------------------------------------

/** The cut edge of the board, where the printing stops: pale, warm, never pure white. */
private const val BOARD = 0xFFEDE7DA.toInt()

private fun printedSleeve(src: Bitmap, n: Int, phase: Int): Bitmap {
    val out = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    val rnd = Random(phase.toLong())

    printArt(c, src, n)
    boardMottle(c, n, rnd)
    cutEdge(c, n)
    sheen(c, n)
    grain(c, n, rnd)
    return out
}

/** The cover art itself, untouched — a new sleeve has not faded. */
private fun printArt(c: Canvas, src: Bitmap, n: Int) {
    val p = Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true
    }
    val s = min(src.width, src.height)
    val sr = Rect((src.width - s) / 2, (src.height - s) / 2, (src.width + s) / 2, (src.height + s) / 2)
    c.drawBitmap(src, sr, RectF(0f, 0f, n.toFloat(), n.toFloat()), p)
}

/** Very low-frequency unevenness: board is pressed pulp, and it never lies perfectly flat. */
private fun boardMottle(c: Canvas, n: Int, rnd: Random) {
    val p = Paint().apply {
        isFilterBitmap = true
        alpha = 18
        xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
        shader = BitmapShader(mottleTile, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR).apply {
            setLocalMatrix(
                Matrix().apply {
                    val sc = n / 26f
                    setScale(sc, sc)
                    postTranslate(-rnd.nextFloat() * n, -rnd.nextFloat() * n)
                },
            )
        }
    }
    c.drawRect(0f, 0f, n.toFloat(), n.toFloat(), p)
}

/**
 * The cut edge of the card, a hairline of pale board around the print. It is what tells you the
 * sleeve is a physical thing with a thickness rather than an image that stops.
 */
private fun cutEdge(c: Canvas, n: Int) {
    val w = n * 0.0022f
    val p = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = w
        color = BOARD
        alpha = 38
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }
    c.drawRect(w / 2f, w / 2f, n - w / 2f, n - w / 2f, p)
}

/** A sliver of room light across the board, and the faintest falloff into the corners. */
private fun sheen(c: Canvas, n: Int) {
    c.drawRect(
        0f, 0f, n.toFloat(), n.toFloat(),
        Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
            shader = LinearGradient(
                0f, 0f, n * 0.85f, n * 0.95f,
                intArrayOf(0x0CFFFFFF, 0x04FFFFFF, 0x00FFFFFF, 0x02FFFFFF),
                floatArrayOf(0f, 0.3f, 0.62f, 1f),
                Shader.TileMode.CLAMP,
            )
        },
    )
    c.drawRect(
        0f, 0f, n.toFloat(), n.toFloat(),
        Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DARKEN)
            shader = RadialGradient(
                n / 2f, n / 2f, n * 0.78f,
                intArrayOf(0x00000000, 0x00000000, 0x1A000000),
                floatArrayOf(0f, 0.62f, 1f),
                Shader.TileMode.CLAMP,
            )
        },
    )
}

/**
 * Paper tooth over the lot. This is the whole point of the pass: without it the cover is a clean
 * digital image, and with it the same image reads as ink sitting on card.
 */
private fun grain(c: Canvas, n: Int, rnd: Random) {
    val p = Paint().apply {
        isFilterBitmap = false
        alpha = 30
        xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
        shader = BitmapShader(grainTile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
            setLocalMatrix(
                Matrix().apply {
                    setScale(1.7f, 1.7f)
                    postTranslate(-rnd.nextFloat() * 180f, -rnd.nextFloat() * 180f)
                },
            )
        }
    }
    c.drawRect(0f, 0f, n.toFloat(), n.toFloat(), p)
}

// ---- Tiles -----------------------------------------------------------------------------------

/** Fine fibre grain, OVERLAY-neutral at mid grey. Built once per process. */
private val grainTile: Bitmap by lazy { noiseTile(192, 88f, 0x6A09E667) }

/** Coarse board mottle: the same idea at a 26th of the frequency, stretched smooth. */
private val mottleTile: Bitmap by lazy { noiseTile(26, 70f, 0xBB67AE85) }

private fun noiseTile(size: Int, spread: Float, seed: Long): Bitmap {
    val rnd = Random(seed)
    val px = IntArray(size * size)
    for (i in px.indices) {
        val v = (128 + (rnd.nextFloat() - 0.5f) * spread).toInt().coerceIn(0, 255)
        px[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
}
