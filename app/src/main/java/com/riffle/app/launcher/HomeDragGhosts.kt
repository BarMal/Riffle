package com.riffle.app.launcher

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.riffle.core.domain.launcher.apps.AppIdentity
import android.graphics.Color as AndroidColor

/** The two markers a Home drag leaves: where the item was ([source]) and where it would land ([destination]). */
internal class HomeDragGhost(
    /** The icon desaturated and dimmed. */
    val source: ImageBitmap,
    /** A faint outline of the icon's silhouette (its alpha). */
    val destination: ImageBitmap,
)

/**
 * Bakes the ghosts once per icon (desaturate + dim, and the outline of the alpha) into a bounded cache, so a drag draws
 * two ready bitmaps and never blurs, tints at runtime or opens an offscreen layer. Pure bitmap work; main or
 * background thread.
 */
internal object HomeDragGhosts {
    // The icon style is part of the key: a ghost baked from the old style is never drawn for the new one.
    private data class GhostKey(val identity: AppIdentity, val version: Int)

    private val cache = BoundedCache<GhostKey, HomeDragGhost>(MAX_GHOSTS)

    fun forIcon(
        identity: AppIdentity,
        icon: ImageBitmap,
        version: Int = 0,
    ): HomeDragGhost {
        val key = GhostKey(identity, version)
        return cache[key] ?: HomeDragGhost(bakeSource(icon), bakeOutline(icon)).also { cache[key] = it }
    }

    internal fun clear() = cache.evictAll()

    internal val cachedCount: Int get() = cache.size

    internal fun bakeSource(icon: ImageBitmap): ImageBitmap {
        val src = icon.asAndroidBitmap()
        val pixels = IntArray(src.width * src.height)
        src.getPixels(pixels, 0, src.width, 0, 0, src.width, src.height)
        for (i in pixels.indices) {
            val p = pixels[i]
            val grey =
                (LUMA_R * AndroidColor.red(p) + LUMA_G * AndroidColor.green(p) + LUMA_B * AndroidColor.blue(p))
                    .toInt().coerceIn(0, MAX_CHANNEL)
            pixels[i] = AndroidColor.argb((AndroidColor.alpha(p) * SOURCE_ALPHA).toInt(), grey, grey, grey)
        }
        return Bitmap.createBitmap(pixels, src.width, src.height, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    internal fun bakeOutline(icon: ImageBitmap): ImageBitmap {
        val src = icon.asAndroidBitmap()
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)

        fun solid(
            x: Int,
            y: Int,
        ): Boolean = x in 0 until w && y in 0 until h && AndroidColor.alpha(pixels[y * w + x]) > SOLID_ALPHA
        for (y in 0 until h) {
            for (x in 0 until w) {
                val edge = solid(x, y) && !(solid(x - 1, y) && solid(x + 1, y) && solid(x, y - 1) && solid(x, y + 1))
                if (edge) out[y * w + x] = AndroidColor.argb(OUTLINE_ALPHA, MAX_CHANNEL, MAX_CHANNEL, MAX_CHANNEL)
            }
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    private const val MAX_GHOSTS = 64
    private const val LUMA_R = 0.299f
    private const val LUMA_G = 0.587f
    private const val LUMA_B = 0.114f
    private const val MAX_CHANNEL = 255
    private const val SOURCE_ALPHA = 0.4f
    private const val OUTLINE_ALPHA = 170
    private const val SOLID_ALPHA = 16
}
