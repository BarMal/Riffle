package com.riffle.app.launcher.icons

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.os.Build
import androidx.core.graphics.drawable.toBitmap
import com.riffle.core.domain.launcher.icons.IconShape
import com.riffle.core.domain.launcher.icons.IconShapeGeometry
import com.riffle.core.domain.launcher.icons.LegacyIconHandling
import com.riffle.core.domain.launcher.icons.MaskCommand
import com.riffle.core.domain.launcher.icons.MonochromeSynthesis

/** The two colours a themed icon is drawn in: the plate and the glyph, from the launcher's theme (light or dark). */
data class ThemedIconColors(
    val plateArgb: Int,
    val glyphArgb: Int,
)

/** What [IconRenderer.styled] applies: the shape and legacy choices, and the themed icons choices. */
data class IconLook(
    val shape: IconShape,
    val legacyIcons: LegacyIconHandling,
    val themed: Boolean,
    val themeAll: Boolean,
    val colors: ThemedIconColors,
)

/**
 * Draws one icon at one size through the chosen style. Pure drawing: no caching, no threading; callers run it off the
 * main thread. Everything that is not a style choice (an icon with the default look) is drawn exactly as before:
 * `Drawable.toBitmap`, so the pixels do not change.
 *
 * Adaptive icons have two layers 108 dp wide of which the central 72 dp are visible. A layer is therefore drawn
 * [IconShapeGeometry.ADAPTIVE_LAYER_SCALE] times the icon's size, centred, and the shape's mask cuts the icon out of
 * it. A legacy icon becomes the foreground of a plate, scaled down to the shape's inscribed square so nothing is
 * cropped.
 */
@Suppress("TooManyFunctions")
object IconRenderer {
    /** A neutral light plate for legacy icons: the same in light and dark, so a legacy icon looks like itself. */
    const val LEGACY_PLATE_ARGB: Int = 0xFFF1F1F1.toInt()

    /** Today's drawing: the drawable at [sizePx], as the platform draws it. */
    fun platform(
        drawable: Drawable,
        sizePx: Int,
    ): Bitmap = drawable.toBitmap(width = sizePx, height = sizePx)

    /** [drawable] through [look]; an icon the look leaves alone is drawn as [platform] draws it. */
    fun styled(
        drawable: Drawable,
        sizePx: Int,
        look: IconLook,
    ): Bitmap {
        val layers = layersFor(drawable, sizePx, look) ?: return platform(drawable, sizePx)
        return if (look.shape == IconShape.SYSTEM) {
            // The platform's own mask: the layers become an adaptive icon and draw as one.
            AdaptiveIconDrawable(layers.background, layers.foreground).toBitmap(width = sizePx, height = sizePx)
        } else {
            masked(layers, sizePx, look.shape)
        }
    }

    /**
     * An app without a drawable in the pack: its icon is drawn on the pack's back plate, cut by the pack's mask,
     * with the pack's overlay on top. [scale] is the pack's content scale (1 draws the icon at full size).
     */
    fun composite(
        icon: Drawable,
        sizePx: Int,
        back: Drawable?,
        mask: Drawable?,
        upon: Drawable?,
        scale: Float,
    ): Bitmap {
        val result = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        back?.let { drawFitted(canvas, it, sizePx, 1f) }
        val content = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val contentCanvas = Canvas(content)
        drawFitted(contentCanvas, icon, sizePx, scale)
        mask?.let { drawFitted(contentCanvas, it, sizePx, 1f, Paint().apply { xfermode = DST_OUT }) }
        canvas.drawBitmap(content, 0f, 0f, null)
        upon?.let { drawFitted(canvas, it, sizePx, 1f) }
        return result
    }

    private class Layers(
        val background: Drawable,
        val foreground: Drawable,
    )

    @Suppress("CyclomaticComplexMethod")
    private fun layersFor(
        drawable: Drawable,
        sizePx: Int,
        look: IconLook,
    ): Layers? {
        val layerPx = (sizePx * IconShapeGeometry.ADAPTIVE_LAYER_SCALE).toInt()
        val adaptive = drawable as? AdaptiveIconDrawable
        val themed = look.themed
        val monochrome = if (themed && adaptive != null) monochromeOf(adaptive) else null
        return when {
            themed && monochrome != null ->
                Layers(ColorDrawable(look.colors.plateArgb), tinted(monochrome, layerPx, look.colors.glyphArgb))
            themed && look.themeAll ->
                Layers(ColorDrawable(look.colors.plateArgb), generated(glyphSource(drawable, look), layerPx, look))
            adaptive != null && look.shape != IconShape.SYSTEM ->
                Layers(adaptive.background ?: ColorDrawable(0), adaptive.foreground ?: ColorDrawable(0))
            adaptive != null -> null
            look.shape == IconShape.SYSTEM -> null
            look.legacyIcons == LegacyIconHandling.PLATE ->
                Layers(ColorDrawable(LEGACY_PLATE_ARGB), legacyForeground(drawable, look.shape))
            else -> null
        }
    }

    private fun monochromeOf(adaptive: AdaptiveIconDrawable): Drawable? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) adaptive.monochrome else null

    // The drawable whose shape becomes the glyph: an adaptive icon's foreground, or a legacy icon on its inset.
    private fun glyphSource(
        drawable: Drawable,
        look: IconLook,
    ): Drawable = (drawable as? AdaptiveIconDrawable)?.foreground ?: legacyForeground(drawable, look.shape)

    private fun legacyForeground(
        drawable: Drawable,
        shape: IconShape,
    ): Drawable {
        // Themed legacy icons under the system shape are inset as for a circle, the platform's usual mask.
        val fitted = if (shape == IconShape.SYSTEM) IconShape.CIRCLE else shape
        val fraction = IconShapeGeometry.legacyContentFraction(fitted)
        val inset = (1f - fraction / IconShapeGeometry.ADAPTIVE_LAYER_SCALE) / 2f
        return InsetDrawable(drawable, inset)
    }

    private fun tinted(
        source: Drawable,
        layerPx: Int,
        glyphArgb: Int,
    ): Drawable {
        val bitmap = Bitmap.createBitmap(layerPx, layerPx, Bitmap.Config.ARGB_8888)
        source.setBounds(0, 0, layerPx, layerPx)
        source.colorFilter = PorterDuffColorFilter(glyphArgb, PorterDuff.Mode.SRC_IN)
        source.draw(Canvas(bitmap))
        source.colorFilter = null
        return BitmapDrawable(null, bitmap)
    }

    private fun generated(
        source: Drawable,
        layerPx: Int,
        look: IconLook,
    ): Drawable {
        val bitmap = Bitmap.createBitmap(layerPx, layerPx, Bitmap.Config.ARGB_8888)
        source.setBounds(0, 0, layerPx, layerPx)
        source.draw(Canvas(bitmap))
        val pixels = IntArray(layerPx * layerPx)
        bitmap.getPixels(pixels, 0, layerPx, 0, 0, layerPx, layerPx)
        val coverage = MonochromeSynthesis.coverage(pixels)
        val rgb = look.colors.glyphArgb and RGB_MASK
        for (i in pixels.indices) pixels[i] = (coverage[i] shl ALPHA_SHIFT) or rgb
        bitmap.setPixels(pixels, 0, layerPx, 0, 0, layerPx, layerPx)
        return BitmapDrawable(null, bitmap)
    }

    private fun masked(
        layers: Layers,
        sizePx: Int,
        shape: IconShape,
    ): Bitmap {
        val result = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val overhang = (sizePx * (IconShapeGeometry.ADAPTIVE_LAYER_SCALE - 1f) / 2f).toInt()
        listOf(layers.background, layers.foreground).forEach { layer ->
            layer.setBounds(-overhang, -overhang, sizePx + overhang, sizePx + overhang)
            layer.draw(canvas)
        }
        // Everything outside the shape is cleared (a Porter-Duff mode only changes the pixels the geometry covers, so
        // the geometry is the outside of the shape), with an anti-aliased edge.
        val outside = maskPath(shape, sizePx).apply { fillType = Path.FillType.INVERSE_WINDING }
        canvas.drawPath(outside, Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = DST_OUT })
        return result
    }

    /** The outline of [shape] as a path in a [sizePx] box. */
    fun maskPath(
        shape: IconShape,
        sizePx: Int,
    ): Path {
        val scale = sizePx / IconShapeGeometry.VIEWPORT
        return Path().also { path ->
            IconShapeGeometry.outline(shape)?.forEach { command ->
                when (command) {
                    is MaskCommand.MoveTo -> path.moveTo(command.x * scale, command.y * scale)
                    is MaskCommand.LineTo -> path.lineTo(command.x * scale, command.y * scale)
                    is MaskCommand.CubicTo ->
                        path.cubicTo(
                            command.x1 * scale,
                            command.y1 * scale,
                            command.x2 * scale,
                            command.y2 * scale,
                            command.x * scale,
                            command.y * scale,
                        )
                    MaskCommand.Close -> path.close()
                }
            }
        }
    }

    private fun drawFitted(
        canvas: Canvas,
        drawable: Drawable,
        sizePx: Int,
        scale: Float,
        paint: Paint? = null,
    ) {
        val side = (sizePx * scale).toInt()
        val offset = (sizePx - side) / 2
        val layer = drawable.toBitmap(width = side.coerceAtLeast(1), height = side.coerceAtLeast(1))
        canvas.drawBitmap(layer, offset.toFloat(), offset.toFloat(), paint)
    }

    private val DST_OUT = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    private const val RGB_MASK = 0x00FFFFFF
    private const val ALPHA_SHIFT = 24
}
