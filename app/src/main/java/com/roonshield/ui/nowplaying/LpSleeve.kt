package com.roonshield.ui.nowplaying

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.roonshield.ui.components.AlbumArtCache
import com.roonshield.ui.theme.RoonSurfaceVariant
import com.roonshield.ui.theme.RoonTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Now Playing album art rendered as a second-hand 12" LP sleeve: the cover art printed on
 * cardboard that has been pulled in and out of a shelf for forty years.
 *
 * Every load rolls a fresh copy. A grade is drawn from a weighted table spanning near-mint to
 * ex-jukebox-destroyed and scales every effect; ring wear (the disc abrading a circle through the
 * ink from the inside) turns up on ~4 copies in 5 regardless of grade, because that is how real
 * sleeves age. Nothing here is a texture asset — it is all drawn from the seed, so no two repeat.
 *
 * The composite is baked into one bitmap off the main thread and then simply blitted, so the
 * per-second seek-position recomposition on this screen never redraws any of it.
 */
@Composable
fun LpSleeveArt(
    imageKey: String?,
    sizePx: Int,
    seed: Long,
    fetch: suspend (String, Int, Int) -> ByteArray?,
    modifier: Modifier = Modifier,
) {
    // Ageing a 1080px cover costs a few hundred milliseconds, so the flat art goes up the moment it
    // arrives and the wear fades in over it rather than the screen holding an empty box.
    var flat by remember(imageKey, sizePx, seed) { mutableStateOf<ImageBitmap?>(null) }
    var worn by remember(imageKey, sizePx, seed) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(imageKey, sizePx, seed) {
        // Both, so a re-roll on the same cover can't crossfade worn -> clean -> worn again.
        flat = null
        worn = null
        val key = imageKey?.takeIf { it.isNotEmpty() } ?: run { flat = null; return@LaunchedEffect }
        val src = AlbumArtCache.get(key, sizePx, sizePx, fetch) ?: run { flat = null; return@LaunchedEffect }
        flat = src
        worn = withContext(Dispatchers.Default) {
            runCatching { agedSleeve(src.asAndroidBitmap(), sizePx.coerceIn(240, 1280), Random(seed)) }
                .onFailure { android.util.Log.w("LpSleeve", "sleeve render failed; showing plain art", it) }
                .getOrNull()?.asImageBitmap()
        }
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Crossfade(targetState = worn ?: flat, animationSpec = tween(650), label = "sleeve") { img ->
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

// ---- The aged composite ----------------------------------------------------------------------

/** Exposed board under the ink: warm, slightly grey, never pure white. */
private const val BOARD = 0xFFE6DFCE.toInt()
private const val BOARD_DEEP = 0xFFCFC3A9.toInt()
private const val GRIME = 0xFF2A2318.toInt()
private const val TIDE = 0xFF7E5C30.toInt()
private const val TAU = 6.2831855f

private fun agedSleeve(src: Bitmap, n: Int, rnd: Random): Bitmap {
    val out = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    val sev = rollGrade(rnd)
    // Which edge the record goes in: top-opening sleeves wear differently from side-opening ones.
    val opening = if (rnd.nextFloat() < 0.55f) EDGE_TOP else EDGE_RIGHT

    printArt(c, src, n, sev)
    cardboardMottle(c, n, sev, rnd)
    discImpression(c, n, sev, rnd)
    ringWear(c, out, n, sev, rnd)
    creases(c, n, sev, rnd)
    edgeWear(c, n, sev, rnd, opening)
    spineShade(c, n, sev, rnd)
    seamSplit(c, n, sev, rnd, opening)
    waterStain(c, n, sev, rnd)
    foxing(c, n, sev, rnd)
    scuffs(c, n, sev, rnd)
    handlingGrime(c, n, sev, rnd)
    sheen(c, n, rnd)
    grain(c, n, sev, rnd)
    cornerDamage(c, n, sev, rnd) // last: it cuts the board away, so nothing may paint over it
    return out
}

/**
 * Record-trade condition grades, weighted the way a crate of second-hand LPs actually is: a few
 * near-mint, a few beaten to death, most somewhere in the middle.
 */
private fun rollGrade(rnd: Random): Float {
    val r = rnd.nextFloat() * 100f
    return when {
        r < 9f -> 0.02f + rnd.nextFloat() * 0.05f  // M / NM — effectively untouched
        r < 25f -> 0.10f + rnd.nextFloat() * 0.11f // EX
        r < 51f -> 0.24f + rnd.nextFloat() * 0.15f // VG+
        r < 77f -> 0.40f + rnd.nextFloat() * 0.19f // VG
        r < 92f -> 0.60f + rnd.nextFloat() * 0.20f // G
        else -> 0.81f + rnd.nextFloat() * 0.18f    // P — split, ringed, stained, cornerless
    }
}

/**
 * The printed cover: ink fades, loses bite, desaturates and yellows as the years go by.
 *
 * The yellowing has to live here in the colour matrix rather than as a multiply pass over the top:
 * Android's `PorterDuff.Mode.MULTIPLY` is really modulate — it multiplies *alpha* as well, so a
 * warm-white wash over the sleeve quietly eats the whole composite's opacity. Everything below
 * that wants to darken uses DARKEN instead, which composites alpha properly and never brightens.
 */
private fun printArt(c: Canvas, src: Bitmap, n: Int, sev: Float) {
    val m = ColorMatrix().apply { setSaturation(1f - 0.24f * sev) }
    val lift = 6.5f * sev
    val gain = 1f - 0.11f * sev
    val warmG = 1f - 0.05f * sev
    val warmB = 1f - 0.11f * sev
    m.postConcat(
        ColorMatrix(
            floatArrayOf(
                gain, 0f, 0f, 0f, lift,
                0f, gain * warmG, 0f, 0f, lift * 0.95f,
                0f, 0f, gain * warmB, 0f, lift * 0.8f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
    val p = Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true
        colorFilter = ColorMatrixColorFilter(m)
    }
    val s = min(src.width, src.height)
    val sr = Rect((src.width - s) / 2, (src.height - s) / 2, (src.width + s) / 2, (src.height + s) / 2)
    c.drawBitmap(src, sr, RectF(0f, 0f, n.toFloat(), n.toFloat()), p)
}

/** Low-frequency unevenness: damp board, pulped fibre, uneven old printing. */
private fun cardboardMottle(c: Canvas, n: Int, sev: Float, rnd: Random) {
    val p = Paint().apply {
        isFilterBitmap = true
        alpha = (34 + 54 * sev).toInt()
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

// ---- Ring wear -------------------------------------------------------------------------------
//
// A 12" disc inside a 12 3/8" sleeve: the ring sits at ~97% of the sleeve width and all but
// touches the edges. It is the rim of the record abrading the board from the inside, so it shows
// as the *board* coming through the ink, heaviest in the arcs where the disc bears.

private class Ring(val cx: Float, val cy: Float, val r: Float, val band: Float, val profile: (Float) -> Float)

private fun ringGeometry(n: Int, rnd: Random): Ring {
    val cx = n * (0.5f + bell(rnd) * 0.008f)
    val cy = n * (0.5f + bell(rnd) * 0.008f)
    val r = n * (0.479f + rnd.nextFloat() * 0.012f)
    val band = n * (0.013f + rnd.nextFloat() * 0.023f)
    val lobes = 1 + rnd.nextInt(3)
    val at = FloatArray(lobes) { rnd.nextFloat() * TAU }
    val wide = FloatArray(lobes) { 0.45f + rnd.nextFloat() * 1.5f }
    val gain = FloatArray(lobes) { 0.55f + rnd.nextFloat() * 0.6f }
    val floorLevel = 0.1f + rnd.nextFloat() * 0.3f
    return Ring(cx, cy, r, band) { a ->
        var v = floorLevel
        for (i in 0 until lobes) {
            val d = angleDelta(a, at[i])
            v += gain[i] * exp(-(d * d) / (2f * wide[i] * wide[i]))
        }
        v.coerceIn(0f, 1.25f)
    }
}

private fun ringWear(c: Canvas, dst: Bitmap, n: Int, sev: Float, rnd: Random) {
    if (rnd.nextFloat() > 0.8f) return // one sleeve in five escaped it
    val strength = (0.1f + sev * 1.05f).coerceAtMost(1.05f)
    val ring = ringGeometry(n, rnd)

    // The abrasion itself: ink gone in speckles, never a clean band, and matte rather than bright —
    // many faint marks read as worn board where a few strong ones just read as glitter.
    //
    // Each speckle looks at the cover underneath it first. Over dark ink the board comes through
    // (pale, SCREEN); over a light passage there is no ink left to lose and what you actually see
    // is the dirt the rubbing ground in (GRIME, DARKEN). Picking per-speckle rather than globally
    // is what keeps the ring continuous across a cover that is half black and half cream — and
    // stops it frosting white over the light half.
    val pale = Paint().apply {
        isAntiAlias = true
        color = BOARD_DEEP
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }
    val dirt = Paint().apply {
        isAntiAlias = true
        color = GRIME
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DARKEN)
    }
    // Two clouds of the same speckle: a broad soft one spread either side of the ring that gives it
    // a bald band to sit in, then the tight grainy one on the line of the disc rim itself.
    fun speckle(tries: Int, spread: Float, minRadius: Float, radiusSpan: Float, gain: Float) {
        repeat(tries) {
            val a = rnd.nextFloat() * TAU
            val w = ring.profile(a) * strength
            if (rnd.nextFloat() > w) return@repeat
            val off = bell(rnd) * spread
            val rr = ring.r + off * ring.band
            if (rr > n * 0.502f) return@repeat
            val x = ring.cx + cos(a) * rr
            val y = ring.cy + sin(a) * rr
            val falloff = (1f - (abs(off) / spread) * 0.7f) * w
            val lum = luminanceAt(dst, x, y)
            // Mostly decided by the ink, but not rigidly — a hard luminance threshold draws a
            // visible seam of its own wherever the cover changes tone.
            val dark = if (rnd.nextFloat() < 0.18f) rnd.nextBoolean() else lum > 0.52f
            val p = if (dark) dirt else pale
            val bite = if (dark) (lum - 0.25f).coerceIn(0f, 1f) else (0.9f - lum).coerceIn(0f, 1f)
            p.alpha = (falloff * bite * gain * (18f + rnd.nextFloat() * 44f)).toInt().coerceIn(0, 110)
            if (p.alpha < 2) return@repeat
            c.drawCircle(x, y, n * (minRadius + rnd.nextFloat() * radiusSpan), p)
        }
    }
    speckle((n * n / 420f * (0.35f + strength)).toInt(), 2.4f, 0.004f, 0.009f, 0.42f)
    speckle((n * n / 110f * (0.35f + strength)).toInt(), 1f, 0.0011f, 0.0031f, 1f)

    // Bare patches: where it has gone right through to the board.
    if (sev > 0.52f) {
        val blob = Paint().apply {
            isAntiAlias = true
            color = BOARD_DEEP
            maskFilter = BlurMaskFilter(n * 0.006f, BlurMaskFilter.Blur.NORMAL)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        }
        repeat((sev * 14).toInt()) {
            val a = rnd.nextFloat() * TAU
            val w = ring.profile(a) * strength
            if (rnd.nextFloat() > w * 0.75f) return@repeat
            val x = ring.cx + cos(a) * (ring.r + bell(rnd) * ring.band * 0.5f)
            val y = ring.cy + sin(a) * (ring.r + bell(rnd) * ring.band * 0.5f)
            // Only where there was dark ink to lose: a bald patch on an already-pale passage is
            // just a white smudge, and a big blurred one at that.
            val lum = luminanceAt(dst, x, y)
            if (lum > 0.5f) return@repeat
            blob.alpha = (26 + rnd.nextFloat() * 54f * (1f - lum)).toInt()
            c.drawCircle(x, y, n * (0.004f + rnd.nextFloat() * 0.011f), blob)
        }
    }
}

/** The pressed-in outline of the disc, and sometimes its label, embossed through the board. */
private fun discImpression(c: Canvas, n: Int, sev: Float, rnd: Random) {
    if (sev < 0.16f) return // a sleeve this fresh hasn't been stood on a shelf long enough
    val r = n * (0.479f + rnd.nextFloat() * 0.012f)
    val dark = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = n * 0.0035f
        color = GRIME
        alpha = (16 + 54 * sev).toInt()
        maskFilter = BlurMaskFilter(n * 0.0028f, BlurMaskFilter.Blur.NORMAL)
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DARKEN)
    }
    c.drawCircle(n / 2f, n / 2f, r, dark)
    val light = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = n * 0.0025f
        color = BOARD
        alpha = (12 + 40 * sev).toInt()
        maskFilter = BlurMaskFilter(n * 0.002f, BlurMaskFilter.Blur.NORMAL)
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }
    c.drawCircle(n / 2f, n / 2f, r - n * 0.004f, light)

    // The label edge, pressed through from inside. Kept faint and loosely scattered: tight bright
    // dots on a small circle read as a bead necklace, not as wear.
    if (sev > 0.45f && rnd.nextFloat() < 0.45f) {
        val lr = n * (0.152f + rnd.nextFloat() * 0.022f)
        dark.alpha = (8 + 26 * sev).toInt()
        c.drawCircle(n / 2f, n / 2f, lr, dark)
        val dot = Paint().apply {
            isAntiAlias = true
            color = BOARD_DEEP
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        }
        repeat((sev * 700).toInt()) {
            val a = rnd.nextFloat() * TAU
            dot.alpha = (4 + rnd.nextFloat() * 15f).toInt()
            c.drawCircle(
                n / 2f + cos(a) * (lr + bell(rnd) * n * 0.013f),
                n / 2f + sin(a) * (lr + bell(rnd) * n * 0.013f),
                n * (0.001f + rnd.nextFloat() * 0.0025f),
                dot,
            )
        }
    }
}

// ---- Edges, spine, seams ---------------------------------------------------------------------

private const val EDGE_TOP = 0
private const val EDGE_RIGHT = 1
private const val EDGE_BOTTOM = 2
private const val EDGE_LEFT = 3

/** Rubbed edges: the cut edge of the board always shows, pale, long before anything else does. */
private fun edgeWear(c: Canvas, n: Int, sev: Float, rnd: Random, opening: Int) {
    val dot = Paint().apply {
        isAntiAlias = true
        color = BOARD
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }
    val line = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        color = BOARD
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }
    for (e in 0..3) {
        val base = when (e) {
            EDGE_BOTTOM -> 1f // the edge it stands on
            EDGE_LEFT -> 0.72f
            EDGE_TOP -> 0.66f
            else -> 0.6f
        } * (if (e == opening) 1.3f else 1f)
        val w = (0.1f + sev * base * (0.6f + rnd.nextFloat() * 0.6f)).coerceAtMost(1f)

        // The exposed cut edge — present even on a near-mint copy, just faint.
        line.strokeWidth = n * (0.0016f + 0.004f * w)
        line.alpha = (14 + 74 * w).toInt().coerceAtMost(110)
        val inset = line.strokeWidth * 0.5f
        when (e) {
            EDGE_TOP -> c.drawLine(0f, inset, n.toFloat(), inset, line)
            EDGE_RIGHT -> c.drawLine(n - inset, 0f, n - inset, n.toFloat(), line)
            EDGE_BOTTOM -> c.drawLine(0f, n - inset, n.toFloat(), n - inset, line)
            else -> c.drawLine(inset, 0f, inset, n.toFloat(), line)
        }

        // Abrasion creeping inwards from it.
        val depth = n * (0.006f + 0.03f * w)
        repeat((n * 2.6f * w).toInt()) {
            val t = rnd.nextFloat()
            val d = abs(bell(rnd)) * depth
            val fade = (1f - d / depth).coerceAtLeast(0f)
            dot.alpha = (fade * fade * w * (16f + rnd.nextFloat() * 56f)).toInt().coerceIn(0, 110)
            if (dot.alpha < 3) return@repeat
            val rad = n * (0.0012f + rnd.nextFloat() * 0.003f)
            when (e) {
                EDGE_TOP -> c.drawCircle(t * n, d, rad, dot)
                EDGE_RIGHT -> c.drawCircle(n - d, t * n, rad, dot)
                EDGE_BOTTOM -> c.drawCircle(t * n, n - d, rad, dot)
                else -> c.drawCircle(d, t * n, rad, dot)
            }
        }

        // Frayed bites out of the edge once it gets bad.
        if (w > 0.45f) {
            val bite = Paint().apply {
                isAntiAlias = true
                color = BOARD_DEEP
                maskFilter = BlurMaskFilter(n * 0.004f, BlurMaskFilter.Blur.NORMAL)
                xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
            }
            repeat((w * 11).toInt()) {
                val t = rnd.nextFloat()
                bite.alpha = (34 + rnd.nextFloat() * 60f).toInt()
                val rad = n * (0.004f + rnd.nextFloat() * 0.013f)
                when (e) {
                    EDGE_TOP -> c.drawCircle(t * n, 0f, rad, bite)
                    EDGE_RIGHT -> c.drawCircle(n.toFloat(), t * n, rad, bite)
                    EDGE_BOTTOM -> c.drawCircle(t * n, n.toFloat(), rad, bite)
                    else -> c.drawCircle(0f, t * n, rad, bite)
                }
            }
        }
    }
}

/** Shelf shadow down the spine, with the crease that runs beside it. */
private fun spineShade(c: Canvas, n: Int, sev: Float, rnd: Random) {
    val w = n * (0.03f + rnd.nextFloat() * 0.02f)
    val p = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DARKEN)
        shader = LinearGradient(
            0f, 0f, w, 0f,
            intArrayOf(withAlpha(GRIME, (90 + 90 * sev).toInt()), withAlpha(GRIME, 0)),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    c.drawRect(0f, 0f, w, n.toFloat(), p)
    if (sev > 0.25f) {
        val x = n * (0.018f + rnd.nextFloat() * 0.03f)
        creaseLine(c, n, x, 0f, x + bell(rnd) * n * 0.01f, n.toFloat(), 0.012f, sev * 0.9f, rnd)
    }
}

/** Bottom-seam or opening-seam split: the glue let go and the board parted. */
private fun seamSplit(c: Canvas, n: Int, sev: Float, rnd: Random, opening: Int) {
    if (sev < 0.45f || rnd.nextFloat() > sev * 0.8f) return
    // Never the opening edge: that one is supposed to be open, so a "split" there just reads as a
    // stray line. Seams give way along the bottom or the glued side.
    val side = if (opening == EDGE_RIGHT || rnd.nextBoolean()) EDGE_LEFT else EDGE_RIGHT
    val e = if (rnd.nextFloat() < 0.7f) EDGE_BOTTOM else side
    val inset = n * (0.004f + rnd.nextFloat() * 0.01f)
    val frac = 0.2f + rnd.nextFloat() * (0.2f + sev * 0.55f)
    val fromStart = rnd.nextBoolean()
    val dark = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = n * (0.0015f + sev * 0.0035f)
        color = 0xFF14100A.toInt()
        alpha = (85 + 70 * sev).toInt()
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DARKEN)
    }
    val lip = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = n * 0.0022f
        color = BOARD
        alpha = (40 + 55 * sev).toInt()
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }
    val split = Path()
    val lipPath = Path()
    val steps = 26
    for (i in 0..steps) {
        val t = i.toFloat() / steps
        val along = (if (fromStart) t * frac else 1f - frac + t * frac) * n
        val wobble = sin(t * 9f + rnd.nextFloat()) * n * 0.0025f + bell(rnd) * n * 0.0022f
        val taper = sin(t * 3.1415927f).coerceAtLeast(0.15f)
        val off = inset + wobble * taper
        val x: Float
        val y: Float
        val lx: Float
        val ly: Float
        when (e) {
            EDGE_BOTTOM -> { x = along; y = n - off; lx = along; ly = n - off - n * 0.004f }
            EDGE_TOP -> { x = along; y = off; lx = along; ly = off + n * 0.004f }
            EDGE_RIGHT -> { x = n - off; y = along; lx = n - off - n * 0.004f; ly = along }
            else -> { x = off; y = along; lx = off + n * 0.004f; ly = along }
        }
        if (i == 0) {
            split.moveTo(x, y)
            lipPath.moveTo(lx, ly)
        } else {
            split.lineTo(x, y)
            lipPath.lineTo(lx, ly)
        }
    }
    c.drawPath(lipPath, lip)
    c.drawPath(split, dark)
}

// ---- Corners ---------------------------------------------------------------------------------

/** Bumped, crushed and finally missing corners — the second-loudest cue after ring wear. */
private fun cornerDamage(c: Canvas, n: Int, sev: Float, rnd: Random) {
    val corners = arrayOf(
        floatArrayOf(0f, 0f, 0.74f), // top-left
        floatArrayOf(1f, 0f, 0.66f), // top-right
        floatArrayOf(1f, 1f, 1f),    // bottom-right — shelf contact
        floatArrayOf(0f, 1f, 0.96f), // bottom-left
    )
    val dot = Paint().apply {
        isAntiAlias = true
        color = BOARD
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }
    for (k in corners) {
        val weight = k[2]
        if (rnd.nextFloat() > (0.25f + sev * 1.05f) * weight) continue
        val ci = (sev * weight * (0.55f + rnd.nextFloat() * 0.6f)).coerceAtMost(1f)
        val cx = k[0] * n
        val cy = k[1] * n
        val sx = if (k[0] == 0f) 1f else -1f
        val sy = if (k[1] == 0f) 1f else -1f

        // Rounded-off ink loss spreading back from the point.
        val reach = n * (0.03f + 0.12f * ci)
        repeat((1400 * ci).toInt()) {
            val d = sqrt(rnd.nextFloat()) * reach
            val a = rnd.nextFloat() * (TAU / 4f)
            val fade = 1f - d / reach
            dot.alpha = (fade * fade * (14f + rnd.nextFloat() * 62f)).toInt().coerceIn(0, 120)
            if (dot.alpha < 3) return@repeat
            c.drawCircle(cx + sx * cos(a) * d, cy + sy * sin(a) * d, n * (0.0011f + rnd.nextFloat() * 0.0032f), dot)
        }

        // Crush lines across the point where the board folded.
        repeat(1 + (ci * 2.4f).toInt()) {
            val d = n * (0.015f + rnd.nextFloat() * 0.075f) * (0.4f + ci)
            creaseLine(
                c, n,
                cx + sx * d * (0.3f + rnd.nextFloat() * 0.5f), cy,
                cx, cy + sy * d * (0.3f + rnd.nextFloat() * 0.5f),
                0.02f, ci, rnd,
            )
        }

        // Badly gone: the tip is simply no longer there. The cut is a blunt round-off with a slow
        // wobble in it — per-point jitter gives an even sawtooth, which reads as torn paper rather
        // than as a corner that has been knocked about in a shelf for decades.
        if (ci > 0.52f) {
            val cut = n * (0.008f + 0.045f * (ci - 0.5f) * 2f)
            val wob1 = 0.06f + rnd.nextFloat() * 0.12f
            val wob2 = 0.03f + rnd.nextFloat() * 0.07f
            val ph1 = rnd.nextFloat() * TAU
            val ph2 = rnd.nextFloat() * TAU
            val lobes = 2 + rnd.nextInt(3)
            val gone = Path()
            val pts = 26
            gone.moveTo(cx, cy)
            for (i in 0..pts) {
                val a = i.toFloat() / pts * (TAU / 4f)
                val r = cut * (0.9f + wob1 * sin(a * lobes * 4f + ph1) + wob2 * sin(a * 11f + ph2))
                gone.lineTo(cx + sx * cos(a) * r, cy + sy * sin(a) * r)
            }
            gone.close()
            c.drawPath(gone, Paint().apply { isAntiAlias = true; xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) })
            c.drawPath(
                gone,
                Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.STROKE
                    strokeWidth = n * 0.004f
                    color = BOARD_DEEP
                    alpha = 150
                    maskFilter = BlurMaskFilter(n * 0.0035f, BlurMaskFilter.Blur.NORMAL)
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
                },
            )
        }
    }
}

// ---- Creases, stains, scuffs -----------------------------------------------------------------

/** One crease = a lit side and a shadowed side. Drawing only one of the two never convinces. */
private fun creaseLine(
    c: Canvas,
    n: Int,
    x1: Float,
    y1: Float,
    x2: Float,
    y2: Float,
    bow: Float,
    strength: Float,
    rnd: Random,
) {
    val mx = (x1 + x2) / 2f + (y2 - y1) * bow * (if (rnd.nextBoolean()) 1f else -1f)
    val my = (y1 + y2) / 2f + (x1 - x2) * bow * (if (rnd.nextBoolean()) 1f else -1f)
    val off = n * 0.0022f
    val light = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = n * 0.0028f
        color = BOARD
        alpha = (18 + 62 * strength).toInt()
        maskFilter = BlurMaskFilter(n * 0.0022f, BlurMaskFilter.Blur.NORMAL)
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }
    val dark = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = n * 0.003f
        color = GRIME
        alpha = (18 + 66 * strength).toInt()
        maskFilter = BlurMaskFilter(n * 0.0026f, BlurMaskFilter.Blur.NORMAL)
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DARKEN)
    }
    val nx = y2 - y1
    val ny = -(x2 - x1)
    val len = hypot(nx.toDouble(), ny.toDouble()).toFloat().coerceAtLeast(1f)
    val ox = nx / len * off
    val oy = ny / len * off
    val p1 = Path().apply { moveTo(x1 + ox, y1 + oy); quadTo(mx + ox, my + oy, x2 + ox, y2 + oy) }
    val p2 = Path().apply { moveTo(x1 - ox, y1 - oy); quadTo(mx - ox, my - oy, x2 - ox, y2 - oy) }
    c.drawPath(p1, light)
    c.drawPath(p2, dark)
}

private fun creases(c: Canvas, n: Int, sev: Float, rnd: Random) {
    if (sev < 0.22f) return
    repeat(rnd.nextInt(1, 2 + (sev * 3.2f).toInt())) {
        // Most creases run from an edge or a corner clean across the board.
        val a = edgePoint(n, rnd)
        val b = edgePoint(n, rnd)
        creaseLine(c, n, a[0], a[1], b[0], b[1], 0.03f + rnd.nextFloat() * 0.1f, sev * (0.4f + rnd.nextFloat() * 0.7f), rnd)
    }
}

private fun edgePoint(n: Int, rnd: Random): FloatArray {
    val t = rnd.nextFloat() * n
    return when (rnd.nextInt(4)) {
        0 -> floatArrayOf(t, 0f)
        1 -> floatArrayOf(n.toFloat(), t)
        2 -> floatArrayOf(t, n.toFloat())
        else -> floatArrayOf(0f, t)
    }
}

/** Damp basement: a tide line with a stained field inside it. */
private fun waterStain(c: Canvas, n: Int, sev: Float, rnd: Random) {
    if (sev < 0.42f || rnd.nextFloat() > 0.18f + sev * 0.4f) return
    repeat(if (sev > 0.75f && rnd.nextBoolean()) 2 else 1) {
        val cx = (if (rnd.nextBoolean()) rnd.nextFloat() * 0.35f else 0.65f + rnd.nextFloat() * 0.35f) * n
        val cy = (if (rnd.nextBoolean()) rnd.nextFloat() * 0.35f else 0.65f + rnd.nextFloat() * 0.35f) * n
        val base = n * (0.1f + rnd.nextFloat() * 0.3f)
        val w1 = 0.1f + rnd.nextFloat() * 0.2f
        val w2 = 0.06f + rnd.nextFloat() * 0.14f
        val ph1 = rnd.nextFloat() * TAU
        val ph2 = rnd.nextFloat() * TAU
        val lobes1 = 2 + rnd.nextInt(3)
        val lobes2 = 5 + rnd.nextInt(4)
        val path = Path()
        val steps = 90
        for (i in 0..steps) {
            val a = i.toFloat() / steps * TAU
            val r = base * (1f + w1 * sin(a * lobes1 + ph1) + w2 * sin(a * lobes2 + ph2))
            val x = cx + cos(a) * r
            val y = cy + sin(a) * r
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        c.drawPath(
            path,
            Paint().apply {
                isAntiAlias = true
                color = TIDE
                alpha = (22 + 40 * sev).toInt()
                maskFilter = BlurMaskFilter(n * 0.02f, BlurMaskFilter.Blur.NORMAL)
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DARKEN)
            },
        )
        c.drawPath(
            path,
            Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeWidth = n * (0.004f + rnd.nextFloat() * 0.005f)
                color = TIDE
                alpha = (50 + 70 * sev).toInt()
                maskFilter = BlurMaskFilter(n * 0.005f, BlurMaskFilter.Blur.NORMAL)
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DARKEN)
            },
        )
    }
}

/** Foxing: the brown speckle old paper grows. */
private fun foxing(c: Canvas, n: Int, sev: Float, rnd: Random) {
    if (sev < 0.3f) return
    val p = Paint().apply {
        isAntiAlias = true
        color = TIDE
        maskFilter = BlurMaskFilter(n * 0.004f, BlurMaskFilter.Blur.NORMAL)
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DARKEN)
    }
    // Clustered, not evenly scattered — spots breed where the damp got in.
    repeat(1 + rnd.nextInt(3)) {
        val cx = rnd.nextFloat() * n
        val cy = rnd.nextFloat() * n
        val spread = n * (0.05f + rnd.nextFloat() * 0.22f)
        repeat((sev * (6 + rnd.nextInt(22))).toInt()) {
            p.alpha = (14 + rnd.nextFloat() * 56f).toInt()
            c.drawCircle(
                cx + bell(rnd) * spread,
                cy + bell(rnd) * spread,
                n * (0.0018f + rnd.nextFloat() * 0.009f),
                p,
            )
        }
    }
}

/** Shelf scuffing: fine bright skids where the board rubbed its neighbours, plus dark hairlines. */
private fun scuffs(c: Canvas, n: Int, sev: Float, rnd: Random) {
    val light = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = n * 0.0014f
        color = BOARD
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }
    val dark = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = n * 0.0012f
        color = GRIME
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DARKEN)
    }
    val bias = rnd.nextFloat() * TAU
    repeat((8 + sev * 70).toInt()) {
        val x = rnd.nextFloat() * n
        val y = rnd.nextFloat() * n
        val a = bias + bell(rnd) * 0.8f
        val len = n * (0.006f + rnd.nextFloat() * (0.03f + sev * 0.07f))
        val p = if (rnd.nextFloat() < 0.72f) light else dark
        p.alpha = (7 + rnd.nextFloat() * (16f + sev * 40f)).toInt()
        c.drawLine(x, y, x + cos(a) * len, y + sin(a) * len, p)
    }
}

/** Handled edges go grubby while the middle stays cleaner. */
private fun handlingGrime(c: Canvas, n: Int, sev: Float, rnd: Random) {
    val p = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DARKEN)
        shader = RadialGradient(
            n * (0.5f + bell(rnd) * 0.06f), n * (0.5f + bell(rnd) * 0.06f), n * 0.76f,
            intArrayOf(withAlpha(GRIME, 0), withAlpha(GRIME, 0), withAlpha(GRIME, (40 + 95 * sev).toInt())),
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    c.drawRect(0f, 0f, n.toFloat(), n.toFloat(), p)
}

/** A sliver of room light across the board, so it reads as an object and not a flat picture. */
private fun sheen(c: Canvas, n: Int, rnd: Random) {
    val from = rnd.nextFloat() * 0.3f
    val p = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        shader = LinearGradient(
            0f, 0f, n * (0.8f + from), n * (0.9f + from),
            intArrayOf(0x08FFFFFF, 0x03FFFFFF, 0x00FFFFFF, 0x02FFFFFF),
            floatArrayOf(0f, 0.3f, 0.62f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    c.drawRect(0f, 0f, n.toFloat(), n.toFloat(), p)
}

/**
 * Paper grain over the lot. Without this last pass every effect above sits on the art like a
 * decal; with it they fuse into one printed surface.
 */
private fun grain(c: Canvas, n: Int, sev: Float, rnd: Random) {
    val p = Paint().apply {
        isFilterBitmap = false
        alpha = (46 + 60 * sev).toInt()
        xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
        shader = BitmapShader(grainTile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
            setLocalMatrix(
                Matrix().apply {
                    val sc = 1.5f + rnd.nextFloat() * 0.9f
                    setScale(sc, sc)
                    postTranslate(-rnd.nextFloat() * 180f, -rnd.nextFloat() * 180f)
                },
            )
        }
    }
    c.drawRect(0f, 0f, n.toFloat(), n.toFloat(), p)
}

// ---- Tiles + maths ---------------------------------------------------------------------------

/** Fine fibre grain, OVERLAY-neutral at mid grey. Built once per process. */
private val grainTile: Bitmap by lazy { noiseTile(192, 104f, 0x6A09E667) }

/** Coarse board mottle: the same idea at a 26th of the frequency, stretched smooth. */
private val mottleTile: Bitmap by lazy { noiseTile(26, 86f, 0xBB67AE85) }

private fun noiseTile(size: Int, spread: Float, seed: Long): Bitmap {
    val rnd = Random(seed)
    val px = IntArray(size * size)
    for (i in px.indices) {
        val v = (128 + (rnd.nextFloat() - 0.5f) * spread).toInt().coerceIn(0, 255)
        px[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
}

/** Perceived brightness of what has been painted so far at a point, 0..1. */
private fun luminanceAt(b: Bitmap, x: Float, y: Float): Float {
    val px = x.toInt().coerceIn(0, b.width - 1)
    val py = y.toInt().coerceIn(0, b.height - 1)
    val c = b.getPixel(px, py)
    return (0.3f * ((c shr 16) and 0xFF) + 0.59f * ((c shr 8) and 0xFF) + 0.11f * (c and 0xFF)) / 255f
}

/** Roughly normal, in -1..1: three uniforms is plenty of bell for dirt. */
private fun bell(rnd: Random): Float =
    ((rnd.nextFloat() + rnd.nextFloat() + rnd.nextFloat()) / 3f - 0.5f) * 2f

/** Signed shortest distance between two angles, in radians. */
private fun angleDelta(a: Float, b: Float): Float {
    var d = (a - b) % TAU
    if (d > TAU / 2f) d -= TAU
    if (d < -TAU / 2f) d += TAU
    return d
}

private fun withAlpha(color: Int, alpha: Int): Int = (alpha.coerceIn(0, 255) shl 24) or (color and 0xFFFFFF)
