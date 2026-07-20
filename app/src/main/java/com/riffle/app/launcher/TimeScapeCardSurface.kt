package com.riffle.app.launcher

import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.settings.TimeScapeAccentSource
import com.riffle.core.domain.launcher.settings.TimeScapeAppearanceSettings
import com.riffle.core.domain.launcher.settings.TimeScapeBackgroundSource
import com.riffle.core.domain.launcher.settings.TimeScapeRendererCapabilities
import kotlin.math.max

/** Transient visual inputs. Every source has a colour fallback when platform artwork is unavailable. */
internal data class TimeScapeCardBackground(
    val artwork: ImageBitmap? = null,
    val appSeed: String = "riffle",
    val appColor: Color? = null,
    val wallpaperAccent: Color? = null,
)

internal data class TimeScapeCardColors(
    val background: Color,
    val foreground: Color,
    val accent: Color,
    val glass: Color,
    val outline: Color,
)

internal fun timeScapeRendererCapabilities(sdkInt: Int = Build.VERSION.SDK_INT): TimeScapeRendererCapabilities =
    TimeScapeRendererCapabilities(supportsBlur = sdkInt >= Build.VERSION_CODES.S)

internal fun resolveTimeScapeCardColors(
    appearance: TimeScapeAppearanceSettings,
    background: TimeScapeCardBackground,
    materialBackground: Color,
    materialAccent: Color,
): TimeScapeCardColors {
    val effective = appearance.effectiveFor(timeScapeRendererCapabilities())
    val surface = effective.surface
    val base =
        when (surface.backgroundSource) {
            TimeScapeBackgroundSource.NOTIFICATION_ARTWORK,
            TimeScapeBackgroundSource.APP_ICON_TREATMENT,
            ->
                background.artwork?.let(::timeScapeArtworkColor)
                    ?: background.appColor
                    ?: timeScapeSeedColor(background.appSeed)

            TimeScapeBackgroundSource.APP_DERIVED_SOLID,
            TimeScapeBackgroundSource.APP_DERIVED_GRADIENT,
            -> background.appColor ?: timeScapeSeedColor(background.appSeed)

            TimeScapeBackgroundSource.SYSTEM_WALLPAPER_ACCENT -> background.wallpaperAccent ?: materialAccent
            TimeScapeBackgroundSource.CUSTOM_SOLID -> Color(surface.customBackgroundArgb.toInt())
        }
    val glass =
        Color(surface.glassTintArgb.toInt())
            .copy(alpha = 1f - surface.glassTransparencyPercent / 100f)
            .compositeOver(base)
    val foreground =
        if (effective.typography.automaticForegroundContrast) {
            timeScapeAccessibleForeground(glass)
        } else {
            materialBackground
        }
    val accent =
        when (effective.typography.accentSource) {
            TimeScapeAccentSource.APP_DERIVED -> base
            TimeScapeAccentSource.SYSTEM_WALLPAPER -> background.wallpaperAccent ?: materialAccent
            TimeScapeAccentSource.CUSTOM -> Color(effective.typography.customAccentArgb.toInt())
        }
    return TimeScapeCardColors(
        background = base,
        foreground = foreground,
        accent = accent,
        glass = glass,
        outline = Color.White.copy(alpha = surface.highlightPercent / 250f),
    )
}

private fun timeScapeArtworkColor(artwork: ImageBitmap): Color? =
    runCatching {
        artwork.toPixelMap()[artwork.width / 2, artwork.height / 2]
    }.getOrNull()

internal fun timeScapeAccessibleForeground(background: Color): Color =
    if (contrastRatio(Color.Black, background) >= contrastRatio(Color.White, background)) {
        Color.Black
    } else {
        Color.White
    }

internal fun contrastRatio(
    first: Color,
    second: Color,
): Float {
    val firstLuminance = first.luminance()
    val secondLuminance = second.luminance()
    return (max(firstLuminance, secondLuminance) + 0.05f) /
        (minOf(firstLuminance, secondLuminance) + 0.05f)
}

@Composable
internal fun TimeScapeCardSurface(
    appearance: TimeScapeAppearanceSettings,
    background: TimeScapeCardBackground,
    modifier: Modifier = Modifier,
    contentPadding: Dp = appearance.geometry.contentPaddingDp.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val effective = appearance.effectiveFor(timeScapeRendererCapabilities())
    val colors =
        resolveTimeScapeCardColors(
            appearance = effective,
            background = background,
            materialBackground = MaterialTheme.colorScheme.onSurface,
            materialAccent = MaterialTheme.colorScheme.primary,
        )
    val shape = RoundedCornerShape(effective.geometry.cornerRadiusDp.dp)
    val artworkEnabled =
        background.artwork != null &&
            effective.surface.backgroundSource in
            setOf(
                TimeScapeBackgroundSource.NOTIFICATION_ARTWORK,
                TimeScapeBackgroundSource.APP_ICON_TREATMENT,
            )
    val artworkModifier =
        Modifier
            .fillMaxSize()
            .then(
                if (effective.surface.blurStrengthPercent == 0) {
                    Modifier
                } else {
                    Modifier.blur((effective.surface.blurStrengthPercent * 0.24f).dp)
                },
            )

    Box(
        modifier =
            modifier
                .clip(shape)
                .background(timeScapeBackgroundBrush(effective, colors.background))
                .border(effective.surface.outlineWidthDp.dp, colors.outline, shape),
    ) {
        if (artworkEnabled) {
            Image(
                bitmap = requireNotNull(background.artwork),
                contentDescription = null,
                modifier = artworkModifier,
                contentScale = ContentScale.Crop,
            )
        }
        Box(modifier = Modifier.fillMaxSize().background(colors.glass))
        CompositionLocalProvider(LocalContentColor provides colors.foreground) {
            Box(modifier = Modifier.fillMaxSize().padding(contentPadding), content = content)
        }
    }
}

private fun timeScapeBackgroundBrush(
    appearance: TimeScapeAppearanceSettings,
    base: Color,
): Brush =
    if (appearance.surface.backgroundSource == TimeScapeBackgroundSource.APP_DERIVED_GRADIENT) {
        Brush.linearGradient(
            listOf(
                base.copy(alpha = 0.92f),
                base.copy(alpha = 0.58f),
                Color.Black.copy(alpha = 0.24f),
            ),
        )
    } else {
        Brush.linearGradient(listOf(base, base))
    }

@Suppress("ReturnCount")
internal fun decodeTimeScapeArtwork(value: String?): ImageBitmap? {
    if (value.isNullOrBlank() || value.length > MAX_TIMESCAPE_ARTWORK_BASE64_CHARS) return null
    return runCatching {
        val bytes = Base64.decode(value, Base64.DEFAULT)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply {
                inSampleSize = timeScapeArtworkSampleSize(bounds.outWidth, bounds.outHeight)
            },
        )?.asImageBitmap()
    }.getOrNull()
}

internal fun timeScapeArtworkSampleSize(
    width: Int,
    height: Int,
): Int {
    var sample = 1
    while (
        width / sample > MAX_TIMESCAPE_ARTWORK_DIMENSION_PX ||
        height / sample > MAX_TIMESCAPE_ARTWORK_DIMENSION_PX
    ) {
        sample *= 2
    }
    return sample
}

private fun timeScapeSeedColor(seed: String): Color {
    val hue = (seed.hashCode().toUInt().toLong() % 360L).toFloat()
    return Color.hsv(hue, 0.46f, 0.72f)
}

private const val MAX_TIMESCAPE_ARTWORK_BASE64_CHARS = 2_800_000
private const val MAX_TIMESCAPE_ARTWORK_DIMENSION_PX = 768
