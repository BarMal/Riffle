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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.settings.AdaptiveStageAccentSource
import com.riffle.core.domain.launcher.settings.AdaptiveStageAppearanceSettings
import com.riffle.core.domain.launcher.settings.AdaptiveStageBackgroundSource
import com.riffle.core.domain.launcher.settings.AdaptiveStageCardEffect
import com.riffle.core.domain.launcher.settings.AdaptiveStageCardStackResolution
import com.riffle.core.domain.launcher.settings.AdaptiveStageContentDensity
import com.riffle.core.domain.launcher.settings.AdaptiveStageRendererCapabilities
import java.security.MessageDigest
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
internal class AdaptiveStageArtworkCache<Value : Any>(
    maxEntries: Int = DEFAULT_ADAPTIVE_STAGE_ARTWORK_CACHE_ENTRIES,
    private val decode: (String?) -> Value?,
) {
    // Wrapped in a non-null payload so a legitimately absent decode (a corrupt/empty artwork
    // payload) is a cached "known null" hit that skips re-decoding, distinguishable from a key
    // BoundedCache has never seen -- LruCache itself rejects null values outright.
    private val values = BoundedCache<String, ArtworkCachePayload<Value>>(maxEntries)

    fun getOrDecode(
        sourceKey: String,
        artwork: String?,
    ): Value? {
        val cached = values[sourceKey]
        if (cached != null) return cached.value
        val decoded = decode(artwork)
        values[sourceKey] = ArtworkCachePayload(decoded)
        return decoded
    }

    internal fun sizeForTest(): Int = values.size
}

private data class ArtworkCachePayload<Value>(val value: Value?)

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
    // Which surface the content is actually legible against depends on the effect: GLASS and
    // FROSTED both put text over the tinted treatment, but SOLID skips the tint entirely and leaves
    // text sitting on the bare base colour. Contrast has to be measured against whichever of those
    // is really behind the text -- picking a foreground for a light tint and then painting it onto
    // an untinted, much darker base is how a card ends up unreadable.
    val contentSurface = adaptiveStageContentSurface(surface.cardEffect, tinted = glass, base = adjustedBase)
    val requestedForeground =
        if (effective.typography.automaticForegroundContrast) {
            adaptiveStageAccessibleForeground(contentSurface)
        } else {
            materialBackground
        }
    val foreground = adaptiveStageForeground(requestedForeground, contentSurface)
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

/**
 * Whichever surface a card's content is really drawn over, which is what its foreground contrast
 * has to be measured against. [AdaptiveStageCardEffect.SOLID] skips the tint layer entirely, so its
 * text sits on the bare [base]; the other effects put text over the [tinted] treatment.
 */
private fun adaptiveStageContentSurface(
    cardEffect: AdaptiveStageCardEffect,
    tinted: Color,
    base: Color,
): Color = if (cardEffect == AdaptiveStageCardEffect.SOLID) base else tinted

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
        val palette = Palette.Builder(artwork.asAndroidBitmap()).generate()
        val swatch =
            palette.dominantSwatch
                ?: palette.vibrantSwatch
                ?: palette.mutedSwatch
                ?: palette.lightVibrantSwatch
                ?: palette.darkVibrantSwatch
                ?: palette.lightMutedSwatch
                ?: palette.darkMutedSwatch
                ?: return@runCatching null
        Color(swatch.rgb)
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
    // contentDensityScale also drives fontScale, not just padding: COMPACT is meant to shrink text
    // and line spacing (both derive from sp, which resolves through this same Density) so more
    // content fits without an internal scroll, not just carve out a bigger content box around
    // text that stayed full-size. EXPANDED mirrors that the other way, for a more spacious feel.
    val adjustedDensity =
        Density(
            density = density.density,
            fontScale = density.fontScale * effective.typography.textScalePercent / 100f * contentDensityScale,
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
                        // Was *0.24f (max 24dp blur at 100%) -- too weak to read as a deliberate
                        // effect against real artwork. *0.5f gives a max of 50dp.
                        Modifier.blur((effective.surface.blurStrengthPercent * 0.5f).dp)
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
        // Only GLASS reveals the layered treatment, and only because its bezel leaves a rim of it
        // showing around an opaque content face. The layers are inseparable from that frame: the
        // gradient darkens toward one corner and artwork is arbitrary, so text drawn straight onto
        // either has no contrast guarantee at all. SOLID and FROSTED therefore present one flat,
        // deterministic field edge to edge -- the base colour, or that colour tinted -- which is
        // exactly the surface GLASS's own content face already composites to. The layers below are
        // skipped rather than drawn and then covered. The outline is likewise GLASS-only; it reads
        // as a second border stacked on the bezel, which is what this treatment exists to remove.
        val effect = effective.surface.cardEffect
        val drawsLayeredTreatment = effect == AdaptiveStageCardEffect.GLASS
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(
                        when (effect) {
                            AdaptiveStageCardEffect.GLASS ->
                                adaptiveStageBackgroundBrush(effective, colors.background)

                            AdaptiveStageCardEffect.FROSTED -> SolidColor(colors.glass)
                            AdaptiveStageCardEffect.SOLID -> SolidColor(colors.background)
                        },
                    ).then(
                        if (drawsLayeredTreatment) {
                            Modifier.border(effective.surface.outlineWidthDp.dp, colors.outline, shape)
                        } else {
                            Modifier
                        },
                    ),
        ) {
            if (artworkEnabled && drawsLayeredTreatment) {
                Image(
                    bitmap = requireNotNull(background.artwork),
                    contentDescription = null,
                    modifier = artworkModifier,
                    contentScale = ContentScale.Crop,
                    colorFilter = artworkColorFilter,
                )
            }
            if (drawsLayeredTreatment) {
                Box(modifier = Modifier.fillMaxSize().background(colors.glassTint))
                AdaptiveStageTexture(
                    color = colors.accent,
                    intensityPercent = effective.surface.textureIntensityPercent,
                )
            }
        }
        // GLASS alone gives the content its own opaque face, inset wider than adjustedPadding so
        // the background layer's blur/texture/tinted-artwork reads as a visible frame around it
        // rather than an imperceptible sliver. That frame *is* the translucent border, and it is
        // the only reason any of the treatment shows under this effect.
        //
        // SOLID and FROSTED need no such face: the card is already one flat field of exactly the
        // colour that face would have composited to, so the content can sit straight on it with no
        // inset and no frame, and with the same contrast it had before. contentPaddingDp is
        // untouched either way -- it remains the real content inset other call sites reason about;
        // only the extra bezel is conditional.
        val contentModifier =
            when (effect) {
                AdaptiveStageCardEffect.GLASS ->
                    Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .padding(adjustedPadding + ADAPTIVE_STAGE_GLASS_BEZEL_EXTRA_DP.dp)
                        .background(colors.glass, shape)

                AdaptiveStageCardEffect.SOLID,
                AdaptiveStageCardEffect.FROSTED,
                ->
                    Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .padding(adjustedPadding)
            }
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
        // Was /500f (max alpha 0.08 at the old 40% cap) -- barely perceptible. /250f against the
        // new 100% cap gives a max alpha of 0.4.
        val alpha = intensityPercent / 250f
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
private const val ADAPTIVE_STAGE_GLASS_BEZEL_EXTRA_DP = 10f
