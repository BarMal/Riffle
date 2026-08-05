@file:Suppress("TooManyFunctions")

package com.riffle.app.launcher

import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.settings.AdaptiveStageAccentSource
import com.riffle.core.domain.launcher.settings.AdaptiveStageAppearanceSettings
import com.riffle.core.domain.launcher.settings.AdaptiveStageBackgroundSource
import com.riffle.core.domain.launcher.settings.AdaptiveStageCardStackResolution
import com.riffle.core.domain.launcher.settings.AdaptiveStageContentDensity
import com.riffle.core.domain.launcher.settings.AdaptiveStageRendererCapabilities
import java.security.MessageDigest
import java.util.LinkedHashMap
import kotlin.math.max

/** Transient visual inputs. Every source has a colour fallback when platform artwork is unavailable. */
internal data class AdaptiveStageCardBackground(
    val artwork: ImageBitmap? = null,
    val appSeed: String = "riffle",
    val appColor: Color? = null,
    val wallpaperAccent: Color? = null,
)

internal data class AdaptiveStageCardColors(
    val background: Color,
    val foreground: Color,
    val accent: Color,
    /** The configured translucent paint layer, retained separately from the resolved opaque surface. */
    val glassTint: Color,
    val glass: Color,
    val outline: Color,
)

internal data class AdaptiveStageCardActionColors(
    val action: Color,
    val onAction: Color,
)

/**
 * Small process-only LRU cache for bounded notification artwork. Cache keys are stable card
 * identities plus a source revision, not artwork payloads, so private base64 data is not retained
 * as cache metadata.
 */
internal class AdaptiveStageArtworkCache<Value>(
    private val maxEntries: Int = DEFAULT_ADAPTIVE_STAGE_ARTWORK_CACHE_ENTRIES,
    private val decode: (String?) -> Value?,
) {
    init {
        require(maxEntries > 0) { "Artwork cache size must be positive." }
    }

    private val values =
        object : LinkedHashMap<String, Value?>(maxEntries, 0.75f, true) {
            override fun removeEldestEntry(eldest: ArtworkCacheEntry<Value>?): Boolean = size > maxEntries
        }

    fun getOrDecode(
        sourceKey: String,
        artwork: String?,
    ): Value? =
        values[sourceKey]
            ?: if (values.containsKey(sourceKey)) {
                null
            } else {
                decode(artwork).also { decoded -> values[sourceKey] = decoded }
            }

    internal fun sizeForTest(): Int = values.size
}

/** Immutable revision lookup consumed by card composition without hashing artwork payloads. */
internal fun interface AdaptiveStageArtworkRevisionLookup {
    fun revisionFor(notification: LauncherNotification): String?
}

/**
 * Process-only revision cache populated while notification state is refreshed off the UI thread.
 * The volatile map replacement makes each UI lookup observe either the prior complete snapshot or
 * the next complete snapshot, never a partially calculated burst.
 */
internal class AdaptiveStageArtworkRevisionStore : AdaptiveStageArtworkRevisionLookup {
    @Volatile
    private var revisionsByNotificationId: Map<String, String> = emptyMap()

    fun replace(groups: List<AppNotificationGroup>) {
        revisionsByNotificationId =
            groups
                .asSequence()
                .flatMap { group -> group.notifications.asSequence() }
                .mapNotNull { notification ->
                    notification.largeIconPngBase64
                        ?.takeIf(String::isNotBlank)
                        ?.let { artwork -> notification.artworkRevisionId() to artwork.sha256Revision() }
                }.toMap()
    }

    override fun revisionFor(notification: LauncherNotification): String? {
        return revisionsByNotificationId[notification.artworkRevisionId()]
    }
}

internal val adaptiveStageArtworkRevisions = AdaptiveStageArtworkRevisionStore()

private fun LauncherNotification.artworkRevisionId(): String = "${profileId.value}:${packageName.value}:${key.value}"

private fun String.sha256Revision(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
    return buildString(digest.size * 2) {
        digest.forEach { byte ->
            append(ARTWORK_REVISION_HEX[(byte.toInt() ushr 4) and 0x0f])
            append(ARTWORK_REVISION_HEX[byte.toInt() and 0x0f])
        }
    }
}

/** Keeps all AdaptiveStage card renderers aligned with the reachability-capped stack resolution. */
@Suppress("MaxLineLength")
internal fun adaptiveStageResolvedContentPadding(resolution: AdaptiveStageCardStackResolution): Dp = resolution.contentPaddingDp.dp

internal fun adaptiveStageRendererCapabilities(sdkInt: Int = Build.VERSION.SDK_INT): AdaptiveStageRendererCapabilities =
    AdaptiveStageRendererCapabilities(supportsBlur = sdkInt >= Build.VERSION_CODES.S)

internal fun resolveAdaptiveStageCardColors(
    appearance: AdaptiveStageAppearanceSettings,
    background: AdaptiveStageCardBackground,
    materialBackground: Color,
    materialAccent: Color,
    rendererCapabilities: AdaptiveStageRendererCapabilities = adaptiveStageRendererCapabilities(),
): AdaptiveStageCardColors {
    val effective = appearance.effectiveFor(rendererCapabilities)
    val surface = effective.surface
    val base =
        when (surface.backgroundSource) {
            AdaptiveStageBackgroundSource.NOTIFICATION_ARTWORK,
            AdaptiveStageBackgroundSource.APP_ICON_TREATMENT,
            ->
                background.artwork?.let(::adaptiveStageArtworkColor)
                    ?: background.appColor
                    ?: adaptiveStageSeedColor(background.appSeed)

            AdaptiveStageBackgroundSource.APP_DERIVED_SOLID,
            AdaptiveStageBackgroundSource.APP_DERIVED_GRADIENT,
            -> background.appColor ?: adaptiveStageSeedColor(background.appSeed)

            AdaptiveStageBackgroundSource.SYSTEM_WALLPAPER_ACCENT -> background.wallpaperAccent ?: materialAccent
            AdaptiveStageBackgroundSource.CUSTOM_SOLID -> Color(surface.customBackgroundArgb.toInt())
        }
    val adjustedBase = adaptiveStageAdjustedColor(base, surface.saturationPercent, surface.contrastPercent)
    val glassTint =
        Color(surface.glassTintArgb.toInt())
            .copy(alpha = 1f - surface.glassTransparencyPercent / 100f)
    val glass =
        glassTint
            .compositeOver(adjustedBase)
    val requestedForeground =
        if (effective.typography.automaticForegroundContrast) {
            adaptiveStageAccessibleForeground(glass)
        } else {
            materialBackground
        }
    val foreground = adaptiveStageForeground(requestedForeground, glass)
    val accent =
        when (effective.typography.accentSource) {
            AdaptiveStageAccentSource.APP_DERIVED -> adjustedBase
            AdaptiveStageAccentSource.SYSTEM_WALLPAPER -> background.wallpaperAccent ?: materialAccent
            AdaptiveStageAccentSource.CUSTOM -> Color(effective.typography.customAccentArgb.toInt())
        }
    return AdaptiveStageCardColors(
        background = adjustedBase,
        foreground = foreground,
        accent = accent,
        glassTint = glassTint,
        glass = glass,
        outline = accent.copy(alpha = surface.highlightPercent / 100f),
    )
}

internal fun adaptiveStageAdjustedColor(
    color: Color,
    saturationPercent: Int,
    contrastPercent: Int,
): Color {
    val saturation = saturationPercent / 100f
    val contrast = contrastPercent / 100f
    val luminance = color.red * 0.213f + color.green * 0.715f + color.blue * 0.072f

    fun adjusted(component: Float): Float =
        (((luminance + (component - luminance) * saturation) - 0.5f) * contrast + 0.5f)
            .coerceIn(0f, 1f)
    return Color(adjusted(color.red), adjusted(color.green), adjusted(color.blue), color.alpha)
}

private fun adaptiveStageArtworkColor(artwork: ImageBitmap): Color? =
    runCatching {
        artwork.toPixelMap()[artwork.width / 2, artwork.height / 2]
    }.getOrNull()

internal fun adaptiveStageAccessibleForeground(background: Color): Color =
    if (contrastRatio(Color.Black, background) >= contrastRatio(Color.White, background)) {
        Color.Black
    } else {
        Color.White
    }

private fun adaptiveStageForeground(
    preferred: Color,
    background: Color,
): Color =
    preferred.takeIf { contrastRatio(it, background) >= MINIMUM_FOREGROUND_CONTRAST_RATIO }
        ?: adaptiveStageAccessibleForeground(background)

internal fun contrastRatio(
    first: Color,
    second: Color,
): Float {
    val firstLuminance = first.luminance()
    val secondLuminance = second.luminance()
    return (max(firstLuminance, secondLuminance) + 0.05f) /
        (minOf(firstLuminance, secondLuminance) + 0.05f)
}

internal fun resolveAdaptiveStageCardActionColors(
    accent: Color,
    surface: Color,
): AdaptiveStageCardActionColors {
    val action =
        accent.takeIf { contrastRatio(it, surface) >= MINIMUM_ACTION_CONTRAST_RATIO }
            ?: adaptiveStageAccessibleForeground(surface)
    return AdaptiveStageCardActionColors(
        action = action,
        onAction = adaptiveStageAccessibleForeground(action),
    )
}

@Composable
@Suppress("LongMethod")
internal fun AdaptiveStageCardSurface(
    appearance: AdaptiveStageAppearanceSettings,
    background: AdaptiveStageCardBackground,
    modifier: Modifier = Modifier,
    contentPadding: Dp = appearance.geometry.contentPaddingDp.dp,
    rendererCapabilities: AdaptiveStageRendererCapabilities = adaptiveStageRendererCapabilities(),
    content: @Composable BoxScope.() -> Unit,
) {
    val effective = remember(appearance, rendererCapabilities) { appearance.effectiveFor(rendererCapabilities) }
    val materialBackground = MaterialTheme.colorScheme.onSurface
    val materialAccent = MaterialTheme.colorScheme.primary
    val colors =
        remember(effective, background, materialBackground, materialAccent, rendererCapabilities) {
            resolveAdaptiveStageCardColors(
                appearance = effective,
                background = background,
                materialBackground = materialBackground,
                materialAccent = materialAccent,
                rendererCapabilities = rendererCapabilities,
            )
        }
    val shape = remember(effective.geometry.cornerRadiusDp) { RoundedCornerShape(effective.geometry.cornerRadiusDp.dp) }
    val actionColors = remember(colors) { resolveAdaptiveStageCardActionColors(colors.accent, colors.glass) }
    val density = LocalDensity.current
    val contentDensityScale = adaptiveStageContentDensityScale(effective.typography.contentDensity)
    val adjustedPadding = contentPadding * contentDensityScale
    val adjustedDensity =
        Density(
            density = density.density,
            fontScale = density.fontScale * effective.typography.textScalePercent / 100f,
        )
    val artworkEnabled =
        background.artwork != null &&
            effective.surface.backgroundSource in
            setOf(
                AdaptiveStageBackgroundSource.NOTIFICATION_ARTWORK,
                AdaptiveStageBackgroundSource.APP_ICON_TREATMENT,
            )
    val artworkModifier =
        remember(effective.surface.blurStrengthPercent) {
            Modifier
                .fillMaxSize()
                .then(
                    if (effective.surface.blurStrengthPercent == 0) {
                        Modifier
                    } else {
                        Modifier.blur((effective.surface.blurStrengthPercent * 0.24f).dp)
                    },
                )
        }
    val artworkColorFilter =
        remember(effective.surface.saturationPercent, effective.surface.contrastPercent) {
            ColorFilter.colorMatrix(
                ColorMatrix().apply {
                    setToSaturation(effective.surface.saturationPercent / 100f)
                    val contrast = effective.surface.contrastPercent / 100f
                    val translation = (1f - contrast) * 127.5f
                    timesAssign(
                        ColorMatrix(
                            floatArrayOf(
                                contrast, 0f, 0f, 0f, translation,
                                0f, contrast, 0f, 0f, translation,
                                0f, 0f, contrast, 0f, translation,
                                0f, 0f, 0f, 1f, 0f,
                            ),
                        ),
                    )
                },
            )
        }

    Box(
        modifier =
            modifier
                .shadow(effective.surface.shadowElevationDp.dp, shape, clip = false)
                .semantics { this[AdaptiveStageCardBlurStrengthKey] = effective.surface.blurStrengthPercent },
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(adaptiveStageBackgroundBrush(effective, colors.background))
                    .border(effective.surface.outlineWidthDp.dp, colors.outline, shape),
        ) {
            if (artworkEnabled) {
                Image(
                    bitmap = requireNotNull(background.artwork),
                    contentDescription = null,
                    modifier = artworkModifier,
                    contentScale = ContentScale.Crop,
                    colorFilter = artworkColorFilter,
                )
            }
            Box(modifier = Modifier.fillMaxSize().background(colors.glassTint))
            AdaptiveStageTexture(
                color = colors.accent,
                intensityPercent = effective.surface.textureIntensityPercent,
            )
        }
        val contentModifier =
            Modifier
                .fillMaxSize()
                .then(if (effective.geometry.clipContent) Modifier.clip(shape) else Modifier)
                .padding(adjustedPadding)
                .background(colors.glass, shape)
        MaterialTheme(
            colorScheme =
                MaterialTheme.colorScheme.copy(
                    primary = actionColors.action,
                    onPrimary = actionColors.onAction,
                    primaryContainer = actionColors.action,
                    onPrimaryContainer = actionColors.onAction,
                    secondary = actionColors.action,
                    onSecondary = actionColors.onAction,
                    secondaryContainer = actionColors.action,
                    onSecondaryContainer = actionColors.onAction,
                    surfaceTint = actionColors.action,
                ),
        ) {
            CompositionLocalProvider(
                LocalContentColor provides colors.foreground,
                LocalDensity provides adjustedDensity,
            ) {
                Box(modifier = contentModifier, content = content)
            }
        }
    }
}

/** Exposes the rendered blur state for accessibility-aware Compose regression coverage. */
internal val AdaptiveStageCardBlurStrengthKey = SemanticsPropertyKey<Int>("AdaptiveStageCardBlurStrength")

internal fun adaptiveStageContentDensityScale(density: AdaptiveStageContentDensity): Float =
    when (density) {
        AdaptiveStageContentDensity.COMPACT -> 0.8f
        AdaptiveStageContentDensity.COMFORTABLE -> 1f
        AdaptiveStageContentDensity.EXPANDED -> 1.2f
    }

@Composable
private fun AdaptiveStageTexture(
    color: Color,
    intensityPercent: Int,
) {
    if (intensityPercent == 0) return
    Canvas(modifier = Modifier.fillMaxSize()) {
        val alpha = intensityPercent / 500f
        val spacing = 12.dp.toPx()
        val radius = 0.7.dp.toPx()
        var y = spacing / 2f
        while (y < size.height) {
            var x = spacing / 2f
            while (x < size.width) {
                drawCircle(
                    color = color.copy(alpha = alpha),
                    radius = radius,
                    center = androidx.compose.ui.geometry.Offset(x, y),
                )
                x += spacing
            }
            y += spacing
        }
    }
}

private fun adaptiveStageBackgroundBrush(
    appearance: AdaptiveStageAppearanceSettings,
    base: Color,
): Brush =
    if (appearance.surface.backgroundSource == AdaptiveStageBackgroundSource.APP_DERIVED_GRADIENT) {
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
internal fun decodeAdaptiveStageArtwork(value: String?): ImageBitmap? {
    if (value.isNullOrBlank() || value.length > MAX_ADAPTIVE_STAGE_ARTWORK_BASE64_CHARS) return null
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
                inSampleSize = adaptiveStageArtworkSampleSize(bounds.outWidth, bounds.outHeight)
            },
        )?.asImageBitmap()
    }.getOrNull()
}

internal fun adaptiveStageArtworkSampleSize(
    width: Int,
    height: Int,
): Int {
    var sample = 1
    while (
        width / sample > MAX_ADAPTIVE_STAGE_ARTWORK_DIMENSION_PX ||
        height / sample > MAX_ADAPTIVE_STAGE_ARTWORK_DIMENSION_PX
    ) {
        sample *= 2
    }
    return sample
}

private fun adaptiveStageSeedColor(seed: String): Color {
    val hue = (seed.hashCode().toUInt().toLong() % 360L).toFloat()
    return Color.hsv(hue, 0.46f, 0.72f)
}

private const val MAX_ADAPTIVE_STAGE_ARTWORK_BASE64_CHARS = 2_800_000
private const val MAX_ADAPTIVE_STAGE_ARTWORK_DIMENSION_PX = 768
private const val DEFAULT_ADAPTIVE_STAGE_ARTWORK_CACHE_ENTRIES = 12
private const val ARTWORK_REVISION_HEX = "0123456789abcdef"
private const val MINIMUM_FOREGROUND_CONTRAST_RATIO = 4.5f
private const val MINIMUM_ACTION_CONTRAST_RATIO = MINIMUM_FOREGROUND_CONTRAST_RATIO

private typealias ArtworkCacheEntry<Value> = MutableMap.MutableEntry<String, Value?>
