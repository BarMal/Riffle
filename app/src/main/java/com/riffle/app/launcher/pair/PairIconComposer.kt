package com.riffle.app.launcher.pair

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.Shader

/**
 * Composes the one bitmap an app pair is drawn as (docs/product/dock-modes-plan.md, App pairs): the left part of the
 * left app's icon and the right part of the right app's, cross-faded through the middle. The icons keep their natural
 * place in the tile; each is revealed by a horizontal mask, the left one opaque up to 38% of the width and gone by 62%,
 * the right one the reverse, so each shows about 62% of its icon and the middle 24% is the cross-fade. The fade is
 * baked in here, once, with a gradient shader and `DST_IN` on a scratch canvas; nothing is blurred, masked or layered
 * when the Dock draws, which only paints this bitmap. In a right-to-left layout the pair's first app is on the right.
 */
internal object PairIconComposer {
    /** How much of each icon shows, from its own side. */
    const val VISIBLE_FRACTION = 0.62f

    /** The middle band, as a share of the width, where one icon fades into the other. */
    const val BLEND_FRACTION = 0.24f

    /** Where the left icon is still fully opaque, and where the right one starts to show. */
    private const val FADE_START = (1f - BLEND_FRACTION) / 2f

    /** Where the left icon is gone and the right one fully shown. */
    private const val FADE_END = FADE_START + BLEND_FRACTION

    /** A square [sizePx] bitmap of [first] and [second] fused. [first] is on the left, or on the right when [rtl]. */
    fun compose(
        first: Bitmap,
        second: Bitmap,
        sizePx: Int,
        rtl: Boolean,
    ): Bitmap {
        val size = sizePx.coerceAtLeast(1)
        val left = if (rtl) second else first
        val right = if (rtl) first else second
        val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(revealed(left, size, leftSide = true), 0f, 0f, null)
        // The two layers' alphas sum to one across the fade, so adding them is a cross-fade, not one over the other.
        val add = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
        canvas.drawBitmap(revealed(right, size, leftSide = false), 0f, 0f, add)
        return result
    }

    private fun revealed(
        icon: Bitmap,
        size: Int,
        leftSide: Boolean,
    ): Bitmap {
        val layer = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(layer)
        val source = if (icon.config == Bitmap.Config.HARDWARE) icon.copy(Bitmap.Config.ARGB_8888, false) else icon
        canvas.drawBitmap(
            source,
            Rect(0, 0, source.width, source.height),
            Rect(0, 0, size, size),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        val startX = size * FADE_START
        val endX = size * FADE_END
        val mask =
            Paint().apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                shader =
                    if (leftSide) {
                        LinearGradient(startX, 0f, endX, 0f, Color.BLACK, Color.TRANSPARENT, Shader.TileMode.CLAMP)
                    } else {
                        LinearGradient(startX, 0f, endX, 0f, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
                    }
            }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), mask)
        return layer
    }
}
