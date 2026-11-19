package com.riffle.app.launcher.icons

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.icons.AppFilterTag
import com.riffle.core.domain.launcher.icons.IconPackAppFilter

internal fun identity(
    packageName: String,
    activity: String = "$packageName.Main",
) = AppIdentity(AppPackageName(packageName), AppActivityName(activity))

/** A solid disc-less adaptive icon: a solid background and a smaller solid square in the foreground's safe zone. */
internal fun adaptiveIcon(
    background: Int = Color.rgb(30, 100, 220),
    mark: Int = Color.WHITE,
    monochromeMark: Int? = null,
): AdaptiveIconDrawable {
    val foreground = markDrawable(mark)
    val monochrome = monochromeMark?.let { markDrawable(it) }
    return if (monochrome != null) {
        AdaptiveIconDrawable(ColorDrawable(background), foreground, monochrome)
    } else {
        AdaptiveIconDrawable(ColorDrawable(background), foreground)
    }
}

/** A 108-unit foreground layer with a square mark in its middle third. */
internal fun markDrawable(color: Int): Drawable {
    val bitmap = Bitmap.createBitmap(108, 108, Bitmap.Config.ARGB_8888)
    Canvas(bitmap).drawRect(36f, 36f, 72f, 72f, android.graphics.Paint().apply { this.color = color })
    return BitmapDrawable(null, bitmap)
}

/** A legacy icon: an opaque rounded-looking square filling its whole bitmap with a light mark. */
internal fun legacyIcon(
    fill: Int = Color.rgb(220, 60, 60),
    mark: Int = Color.WHITE,
): Drawable {
    val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(fill)
    canvas.drawRect(30f, 30f, 66f, 66f, android.graphics.Paint().apply { color = mark })
    return BitmapDrawable(null, bitmap)
}

/** A fake icon pack source: no platform, just a mapping and a few solid drawables (packages are com.example.*). */
internal class FakeIconPackSource(
    var packs: Map<String, FakePack> = emptyMap(),
) : IconPackSource {
    class FakePack(
        val label: String,
        val version: Long = 1L,
        val appFilter: String? = null,
        val drawableXml: String? = null,
        val drawables: Map<String, Drawable> = emptyMap(),
    )

    var appFilterReads = 0

    override fun installedPacks() = packs.map { (pack, fake) -> InstalledIconPack(pack, fake.label) }

    override fun appFilterTags(packPackage: String): List<AppFilterTag>? {
        appFilterReads++
        return packs[packPackage]?.appFilter?.let(IconPackAppFilter::tagsOf)
    }

    override fun drawableTags(packPackage: String): List<AppFilterTag>? =
        packs[packPackage]?.drawableXml?.let(IconPackAppFilter::tagsOf)

    override fun drawable(
        packPackage: String,
        name: String,
    ): Drawable? = packs[packPackage]?.drawables?.get(name)

    override fun packIcon(packPackage: String): Drawable? = ColorDrawable(Color.GRAY)

    override fun versionOf(packPackage: String): Long? = packs[packPackage]?.version
}

internal fun Bitmap.alphaAt(
    x: Int,
    y: Int,
): Int = Color.alpha(getPixel(x, y))

/** An adaptive icon for the screenshots: a solid background, a white letter and a monochrome layer of the letter. */
internal fun letterAdaptiveIcon(
    background: Int,
    letter: String,
): AdaptiveIconDrawable =
    AdaptiveIconDrawable(ColorDrawable(background), letterLayer(letter, Color.WHITE), letterLayer(letter, Color.BLACK))

/** A legacy icon for the screenshots: a coloured rounded tile with a white letter, as a bitmap with its own corners. */
internal fun letterLegacyIcon(
    fill: Int,
    letter: String,
): Drawable {
    val bitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawRoundRect(
        8f,
        8f,
        184f,
        184f,
        36f,
        36f,
        android.graphics.Paint(
            android.graphics.Paint.ANTI_ALIAS_FLAG,
        ).apply {
            color = fill
        },
    )
    drawLetter(canvas, letter, Color.WHITE, 96f, 132f, 110f)
    return BitmapDrawable(null, bitmap)
}

private fun letterLayer(
    letter: String,
    color: Int,
): Drawable {
    val bitmap = Bitmap.createBitmap(216, 216, Bitmap.Config.ARGB_8888)
    drawLetter(Canvas(bitmap), letter, color, 108f, 142f, 80f)
    return BitmapDrawable(null, bitmap)
}

private fun drawLetter(
    canvas: Canvas,
    letter: String,
    color: Int,
    x: Float,
    baseline: Float,
    sizePx: Float,
) {
    val paint =
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = sizePx
            isFakeBoldText = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
    canvas.drawText(letter, x, baseline, paint)
}
