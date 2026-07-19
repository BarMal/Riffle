package com.riffle.core.domain.launcher.settings

import com.riffle.core.domain.launcher.cards.CardStackAnimationSpec
import com.riffle.core.domain.launcher.cards.CardStackLayoutPolicy
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Versioned, renderer-independent appearance intent for the optional TimeScape card surface.
 * Values are always normalized through [coerce] before persistence or use.
 */
data class TimeScapeAppearanceSettings(
    val version: Int = CURRENT_TIMESCAPE_APPEARANCE_VERSION,
    val preset: TimeScapeAppearancePreset = TimeScapeAppearancePreset.MODERN_TIMESCAPE,
    val geometry: TimeScapeGeometry = TimeScapeGeometry(),
    val surface: TimeScapeSurface = TimeScapeSurface(),
    val typography: TimeScapeTypography = TimeScapeTypography(),
    val motion: TimeScapeMotion = TimeScapeMotion(),
) {
    fun coerce(): TimeScapeAppearanceSettings =
        copy(
            version = version.coerceIn(1, CURRENT_TIMESCAPE_APPEARANCE_VERSION),
            geometry = geometry.coerce(),
            surface = surface.coerce(),
            typography = typography.coerce(),
            motion = motion.coerce(),
        )

    /** Applies all preset values in one immutable update. */
    fun applyPreset(preset: TimeScapeAppearancePreset): TimeScapeAppearanceSettings = preset.settings

    /** Reset has one stable result regardless of the current customisation. */
    fun reset(): TimeScapeAppearanceSettings = modern()

    /** Resolves platform limitations without rewriting the stored user preference. */
    fun effectiveFor(capabilities: TimeScapeRendererCapabilities): TimeScapeAppearanceSettings =
        coerce().let { settings ->
            settings.copy(
                surface =
                    settings.surface.copy(
                        blurStrengthPercent =
                            if (capabilities.supportsBlur && !settings.motion.reducedTransparency) {
                                settings.surface.blurStrengthPercent
                            } else {
                                0
                            },
                        glassTransparencyPercent =
                            if (settings.motion.reducedTransparency) {
                                0
                            } else {
                                settings.surface.glassTransparencyPercent
                            },
                        textureIntensityPercent =
                            if (capabilities.supportsTexture) settings.surface.textureIntensityPercent else 0,
                    ),
                motion =
                    if (settings.motion.reducedMotion) {
                        settings.motion.copy(
                            settleDurationMillis = 0,
                            reflowDurationMillis = 0,
                            enterDurationMillis = 0,
                            exitDurationMillis = 0,
                            expandDurationMillis = 0,
                            easing = TimeScapeEasing.STANDARD,
                            springBouncinessPercent = 0,
                            travelIntensityPercent = 0,
                            parallaxIntensityPercent = 0,
                            rotationIntensityPercent = 0,
                        )
                    } else {
                        settings.motion
                    },
            )
        }

    /**
     * Converts persisted intent to the card-stack primitives used by renderers. The resolution is
     * viewport- and inset-aware; callers must use [isUsable] to choose a non-stack fallback on a
     * space-constrained surface.
     */
    fun resolveCardStack(
        viewport: TimeScapeViewportDp,
        capabilities: TimeScapeRendererCapabilities = TimeScapeRendererCapabilities(),
    ): TimeScapeCardStackResolution {
        val appearance = effectiveFor(capabilities)
        val requestedPadding = appearance.geometry.contentPaddingDp
        val focusedScale = appearance.geometry.focusedScalePercent / 100f
        val stackBounds = resolveStackBounds(appearance.geometry, appearance.motion, focusedScale)
        val cardSize = resolveCardSize(viewport, requestedPadding, appearance.geometry, stackBounds)
        val isUsable = cardSize.isUsable
        val depth = if (isUsable) appearance.geometry.visibleDepth else 1
        val horizontalTravel =
            ((viewport.safeWidthDp - cardSize.widthDp * stackBounds.maxWidthScale) / 2f).coerceAtLeast(0f)
        val verticalTravel =
            ((viewport.safeHeightDp - cardSize.heightDp * stackBounds.maxHeightScale) / 2f).coerceAtLeast(0f)
        val motionScale = appearance.motion.travelIntensityPercent / 100f
        val offsetDirection =
            when (appearance.geometry.fanDirection) {
                TimeScapeFanDirection.NONE -> 0f
                TimeScapeFanDirection.START -> -1f
                TimeScapeFanDirection.END -> 1f
            }
        val focusedGap =
            if (offsetDirection == 0f) {
                0f
            } else {
                min(appearance.geometry.focusedGapDp * motionScale, horizontalTravel)
            }
        val horizontalStep =
            min(
                appearance.geometry.horizontalOffsetDp * motionScale,
                (horizontalTravel - focusedGap).coerceAtLeast(0f) / depth,
            )
        val verticalStep =
            min(
                appearance.geometry.verticalSpacingDp * motionScale,
                verticalTravel / depth,
            )
        val remainingVerticalTravel = (verticalTravel - verticalStep * depth).coerceAtLeast(0f)
        val curveStep =
            min(
                appearance.geometry.curveDp * motionScale,
                remainingVerticalTravel / (depth * depth),
            )
        val layoutPolicy =
            CardStackLayoutPolicy(
                maxVisibleDepth = depth,
                scaleStep = stackBounds.scaleStep,
                offsetStep = horizontalStep,
                focusedGap = focusedGap,
                offsetDirection = offsetDirection,
                alphaStep = appearance.geometry.overlapPercent / 100f / depth,
                verticalOffsetStep = verticalStep,
                curveStep = curveStep,
                rotationStep = stackBounds.rotationStep,
                reducedMotionScaleStep = 0f,
                reducedMotionOffsetStep = 0f,
            )
        val animated = !appearance.motion.reducedMotion && isUsable
        return TimeScapeCardStackResolution(
            isUsable = isUsable,
            cardWidthDp = cardSize.widthDp,
            cardHeightDp = cardSize.heightDp,
            contentPaddingDp = requestedPadding.coerceAtMost(min(cardSize.widthDp, cardSize.heightDp) / 4),
            focusedScale = focusedScale,
            reducedMotion = appearance.motion.reducedMotion,
            layoutPolicy = layoutPolicy,
            animation =
                CardStackAnimationSpec(
                    horizontalTravelFraction = if (animated) appearance.motion.travelIntensityPercent / 100f else 0f,
                    verticalTravelFraction = if (animated) appearance.motion.parallaxIntensityPercent / 100f else 0f,
                    reflowsStack = animated,
                    animatesAlpha = animated,
                    animatesHorizontalTranslation = animated,
                    animatesVerticalTranslation = animated,
                    animatesScale = animated,
                    animatesRotation = animated,
                    durationMillis = maxOf(1, appearance.motion.reflowDurationMillis),
                ),
        )
    }

    companion object {
        fun modern(): TimeScapeAppearanceSettings = TimeScapeAppearancePreset.MODERN_TIMESCAPE.settings
    }
}

private fun resolveCardSize(
    viewport: TimeScapeViewportDp,
    requestedPadding: Int,
    geometry: TimeScapeGeometry,
    stackBounds: ResolvedTimeScapeStackBounds,
): ResolvedTimeScapeCardSize {
    val availableWidth = (viewport.safeWidthDp - requestedPadding * 2).coerceAtLeast(0)
    val availableHeight = (viewport.safeHeightDp - requestedPadding * 2).coerceAtLeast(0)
    val aspectRatio = geometry.cardAspectRatioPercent / 100f
    val width = min(availableWidth / stackBounds.maxWidthScale, availableHeight / stackBounds.maxHeightScale).toInt()
    val height = if (aspectRatio == 0f) 0 else (width / aspectRatio).toInt()
    val focusedWidth = ceil(width * stackBounds.focusedScale).toInt()
    val focusedHeight = ceil(height * stackBounds.focusedScale).toInt()
    return ResolvedTimeScapeCardSize(
        widthDp = width,
        heightDp = height,
        focusedWidthDp = focusedWidth,
        focusedHeightDp = focusedHeight,
        fitsAvailableSpace = focusedWidth <= availableWidth && focusedHeight <= availableHeight,
    )
}

private fun resolveStackBounds(
    geometry: TimeScapeGeometry,
    motion: TimeScapeMotion,
    focusedScale: Float,
): ResolvedTimeScapeStackBounds {
    val depth = geometry.visibleDepth
    val scaleStep = (1f - MIN_TIMESCAPE_BACKGROUND_CARD_SCALE) / depth
    val rotationStep = geometry.rotationDegrees * motion.rotationIntensityPercent / 100f
    val aspectRatio = geometry.cardAspectRatioPercent / 100f
    var maxWidthScale = focusedScale
    var maxHeightScale = focusedScale / aspectRatio
    for (cardDepth in 1..depth) {
        val scale = 1f - scaleStep * cardDepth
        val angleRadians = Math.toRadians((rotationStep * cardDepth).toDouble())
        val cosine = cos(angleRadians).toFloat()
        val sine = sin(angleRadians).toFloat()
        maxWidthScale = maxOf(maxWidthScale, scale * (cosine + sine / aspectRatio))
        maxHeightScale = maxOf(maxHeightScale, scale * (sine + cosine / aspectRatio))
    }
    return ResolvedTimeScapeStackBounds(
        focusedScale = focusedScale,
        scaleStep = scaleStep,
        rotationStep = rotationStep,
        maxWidthScale = maxWidthScale,
        maxHeightScale = maxHeightScale,
    )
}

private data class ResolvedTimeScapeStackBounds(
    val focusedScale: Float,
    val scaleStep: Float,
    val rotationStep: Float,
    val maxWidthScale: Float,
    val maxHeightScale: Float,
)

private data class ResolvedTimeScapeCardSize(
    val widthDp: Int,
    val heightDp: Int,
    val focusedWidthDp: Int,
    val focusedHeightDp: Int,
    val fitsAvailableSpace: Boolean,
) {
    val isUsable: Boolean
        get() =
            focusedWidthDp >= MIN_TIMESCAPE_REACHABLE_CARD_WIDTH_DP &&
                focusedHeightDp >= MIN_TIMESCAPE_REACHABLE_CARD_HEIGHT_DP &&
                fitsAvailableSpace
}

data class TimeScapeViewportDp(
    val widthDp: Int,
    val heightDp: Int,
    val insets: TimeScapeInsetsDp = TimeScapeInsetsDp(),
) {
    val safeWidthDp: Int get() = (widthDp - insets.startDp - insets.endDp).coerceAtLeast(0)
    val safeHeightDp: Int get() = (heightDp - insets.topDp - insets.bottomDp).coerceAtLeast(0)
}

data class TimeScapeInsetsDp(
    val startDp: Int = 0,
    val topDp: Int = 0,
    val endDp: Int = 0,
    val bottomDp: Int = 0,
)

/** Renderer contract joining appearance intent to the existing stack layout and animation APIs. */
data class TimeScapeCardStackResolution(
    val isUsable: Boolean,
    val cardWidthDp: Int,
    val cardHeightDp: Int,
    val contentPaddingDp: Int,
    val focusedScale: Float,
    val reducedMotion: Boolean,
    val layoutPolicy: CardStackLayoutPolicy,
    val animation: CardStackAnimationSpec,
)

enum class TimeScapeAppearancePreset {
    MODERN_TIMESCAPE,
    FLAT_REDUCED_DEPTH,
    ;

    internal val settings: TimeScapeAppearanceSettings
        get() =
            when (this) {
                MODERN_TIMESCAPE -> TimeScapeAppearanceSettings(preset = this)
                FLAT_REDUCED_DEPTH ->
                    TimeScapeAppearanceSettings(
                        preset = this,
                        geometry =
                            TimeScapeGeometry(
                                visibleDepth = 2,
                                focusedScalePercent = 100,
                                overlapPercent = 0,
                                horizontalOffsetDp = 0,
                                curveDp = 0,
                                fanDirection = TimeScapeFanDirection.NONE,
                                rotationDegrees = 0,
                            ),
                        surface =
                            TimeScapeSurface(
                                backgroundSource = TimeScapeBackgroundSource.SYSTEM_WALLPAPER_ACCENT,
                                glassTransparencyPercent = 0,
                                blurStrengthPercent = 0,
                                shadowElevationDp = 0,
                                textureIntensityPercent = 0,
                            ),
                        motion =
                            TimeScapeMotion(
                                travelIntensityPercent = 0,
                                parallaxIntensityPercent = 0,
                                rotationIntensityPercent = 0,
                            ),
                    )
            }
}

data class TimeScapeGeometry(
    val cardAspectRatioPercent: Int = 72,
    val focusedScalePercent: Int = 100,
    val focusedGapDp: Int = 12,
    val visibleDepth: Int = 4,
    val overlapPercent: Int = 22,
    val verticalSpacingDp: Int = 8,
    val horizontalOffsetDp: Int = 20,
    val curveDp: Int = 6,
    val fanDirection: TimeScapeFanDirection = TimeScapeFanDirection.END,
    val rotationDegrees: Int = 4,
    val cornerRadiusDp: Int = 28,
    val clipContent: Boolean = true,
    val contentPaddingDp: Int = 20,
) {
    fun coerce(): TimeScapeGeometry =
        copy(
            cardAspectRatioPercent =
                cardAspectRatioPercent.coerceIn(
                    MIN_TIMESCAPE_CARD_ASPECT_RATIO_PERCENT,
                    MAX_TIMESCAPE_CARD_ASPECT_RATIO_PERCENT,
                ),
            focusedScalePercent =
                focusedScalePercent.coerceIn(
                    MIN_TIMESCAPE_FOCUSED_SCALE_PERCENT,
                    MAX_TIMESCAPE_FOCUSED_SCALE_PERCENT,
                ),
            focusedGapDp =
                focusedGapDp.coerceIn(
                    MIN_TIMESCAPE_FOCUSED_GAP_DP,
                    MAX_TIMESCAPE_FOCUSED_GAP_DP,
                ),
            visibleDepth =
                visibleDepth.coerceIn(
                    MIN_TIMESCAPE_VISIBLE_DEPTH,
                    MAX_TIMESCAPE_VISIBLE_DEPTH,
                ),
            overlapPercent =
                overlapPercent.coerceIn(
                    MIN_TIMESCAPE_OVERLAP_PERCENT,
                    MAX_TIMESCAPE_OVERLAP_PERCENT,
                ),
            verticalSpacingDp =
                verticalSpacingDp.coerceIn(
                    MIN_TIMESCAPE_VERTICAL_SPACING_DP,
                    MAX_TIMESCAPE_VERTICAL_SPACING_DP,
                ),
            horizontalOffsetDp =
                horizontalOffsetDp.coerceIn(
                    MIN_TIMESCAPE_HORIZONTAL_OFFSET_DP,
                    MAX_TIMESCAPE_HORIZONTAL_OFFSET_DP,
                ),
            curveDp =
                curveDp.coerceIn(
                    MIN_TIMESCAPE_CURVE_DP,
                    MAX_TIMESCAPE_CURVE_DP,
                ),
            rotationDegrees =
                rotationDegrees.coerceIn(
                    MIN_TIMESCAPE_ROTATION_DEGREES,
                    MAX_TIMESCAPE_ROTATION_DEGREES,
                ),
            cornerRadiusDp =
                cornerRadiusDp.coerceIn(
                    MIN_TIMESCAPE_CORNER_RADIUS_DP,
                    MAX_TIMESCAPE_CORNER_RADIUS_DP,
                ),
            contentPaddingDp =
                contentPaddingDp.coerceIn(
                    MIN_TIMESCAPE_CONTENT_PADDING_DP,
                    MAX_TIMESCAPE_CONTENT_PADDING_DP,
                ),
        )
}

enum class TimeScapeFanDirection { NONE, START, END }

data class TimeScapeSurface(
    val backgroundSource: TimeScapeBackgroundSource = TimeScapeBackgroundSource.APP_DERIVED_GRADIENT,
    val customBackgroundArgb: Long = 0xFF1B1B1FL,
    val glassTransparencyPercent: Int = 38,
    val glassTintArgb: Long = 0xCCFFFFFFL,
    val blurStrengthPercent: Int = 28,
    val saturationPercent: Int = 100,
    val contrastPercent: Int = 100,
    val outlineWidthDp: Int = 1,
    val highlightPercent: Int = 36,
    val shadowElevationDp: Int = 12,
    val textureIntensityPercent: Int = 0,
) {
    fun coerce(): TimeScapeSurface =
        copy(
            customBackgroundArgb =
                customBackgroundArgb.coerceIn(
                    MIN_TIMESCAPE_ARGB,
                    MAX_TIMESCAPE_ARGB,
                ),
            glassTintArgb =
                glassTintArgb.coerceIn(
                    MIN_TIMESCAPE_ARGB,
                    MAX_TIMESCAPE_ARGB,
                ),
            glassTransparencyPercent =
                glassTransparencyPercent.coerceIn(
                    MIN_TIMESCAPE_GLASS_TRANSPARENCY_PERCENT,
                    MAX_TIMESCAPE_GLASS_TRANSPARENCY_PERCENT,
                ),
            blurStrengthPercent =
                blurStrengthPercent.coerceIn(
                    MIN_TIMESCAPE_BLUR_STRENGTH_PERCENT,
                    MAX_TIMESCAPE_BLUR_STRENGTH_PERCENT,
                ),
            saturationPercent =
                saturationPercent.coerceIn(
                    MIN_TIMESCAPE_SATURATION_PERCENT,
                    MAX_TIMESCAPE_SATURATION_PERCENT,
                ),
            contrastPercent =
                contrastPercent.coerceIn(
                    MIN_TIMESCAPE_CONTRAST_PERCENT,
                    MAX_TIMESCAPE_CONTRAST_PERCENT,
                ),
            outlineWidthDp =
                outlineWidthDp.coerceIn(
                    MIN_TIMESCAPE_OUTLINE_WIDTH_DP,
                    MAX_TIMESCAPE_OUTLINE_WIDTH_DP,
                ),
            highlightPercent =
                highlightPercent.coerceIn(
                    MIN_TIMESCAPE_HIGHLIGHT_PERCENT,
                    MAX_TIMESCAPE_HIGHLIGHT_PERCENT,
                ),
            shadowElevationDp =
                shadowElevationDp.coerceIn(
                    MIN_TIMESCAPE_SHADOW_ELEVATION_DP,
                    MAX_TIMESCAPE_SHADOW_ELEVATION_DP,
                ),
            textureIntensityPercent =
                textureIntensityPercent.coerceIn(
                    MIN_TIMESCAPE_TEXTURE_INTENSITY_PERCENT,
                    MAX_TIMESCAPE_TEXTURE_INTENSITY_PERCENT,
                ),
        )
}

enum class TimeScapeBackgroundSource {
    NOTIFICATION_ARTWORK,
    APP_ICON_TREATMENT,
    APP_DERIVED_SOLID,
    APP_DERIVED_GRADIENT,
    SYSTEM_WALLPAPER_ACCENT,
    CUSTOM_SOLID,
}

data class TimeScapeTypography(
    val accentSource: TimeScapeAccentSource = TimeScapeAccentSource.APP_DERIVED,
    val customAccentArgb: Long = 0xFF6750A4L,
    val automaticForegroundContrast: Boolean = true,
    val contentDensity: TimeScapeContentDensity = TimeScapeContentDensity.COMFORTABLE,
    val textScalePercent: Int = 100,
) {
    fun coerce(): TimeScapeTypography =
        copy(
            customAccentArgb = customAccentArgb.coerceIn(MIN_TIMESCAPE_ARGB, MAX_TIMESCAPE_ARGB),
            textScalePercent =
                textScalePercent.coerceIn(
                    MIN_TIMESCAPE_TEXT_SCALE_PERCENT,
                    MAX_TIMESCAPE_TEXT_SCALE_PERCENT,
                ),
        )
}

enum class TimeScapeAccentSource { APP_DERIVED, SYSTEM_WALLPAPER, CUSTOM }

enum class TimeScapeContentDensity { COMPACT, COMFORTABLE, EXPANDED }

data class TimeScapeMotion(
    val settleDurationMillis: Int = 220,
    val reflowDurationMillis: Int = 260,
    val enterDurationMillis: Int = 240,
    val exitDurationMillis: Int = 180,
    val expandDurationMillis: Int = 280,
    val easing: TimeScapeEasing = TimeScapeEasing.GENTLE_SPRING,
    val springBouncinessPercent: Int = 20,
    val travelIntensityPercent: Int = 100,
    val parallaxIntensityPercent: Int = 18,
    val rotationIntensityPercent: Int = 100,
    val hapticStrength: TimeScapeHapticStrength = TimeScapeHapticStrength.MEDIUM,
    val reducedMotion: Boolean = false,
    val reducedTransparency: Boolean = false,
) {
    fun coerce(): TimeScapeMotion =
        copy(
            settleDurationMillis =
                settleDurationMillis.coerceIn(
                    MIN_TIMESCAPE_SETTLE_DURATION_MILLIS,
                    MAX_TIMESCAPE_SETTLE_DURATION_MILLIS,
                ),
            reflowDurationMillis =
                reflowDurationMillis.coerceIn(
                    MIN_TIMESCAPE_TRANSITION_DURATION_MILLIS,
                    MAX_TIMESCAPE_TRANSITION_DURATION_MILLIS,
                ),
            enterDurationMillis =
                enterDurationMillis.coerceIn(
                    MIN_TIMESCAPE_TRANSITION_DURATION_MILLIS,
                    MAX_TIMESCAPE_TRANSITION_DURATION_MILLIS,
                ),
            exitDurationMillis =
                exitDurationMillis.coerceIn(
                    MIN_TIMESCAPE_TRANSITION_DURATION_MILLIS,
                    MAX_TIMESCAPE_TRANSITION_DURATION_MILLIS,
                ),
            expandDurationMillis =
                expandDurationMillis.coerceIn(
                    MIN_TIMESCAPE_TRANSITION_DURATION_MILLIS,
                    MAX_TIMESCAPE_TRANSITION_DURATION_MILLIS,
                ),
            springBouncinessPercent =
                springBouncinessPercent.coerceIn(
                    MIN_TIMESCAPE_SPRING_BOUNCINESS_PERCENT,
                    MAX_TIMESCAPE_SPRING_BOUNCINESS_PERCENT,
                ),
            travelIntensityPercent =
                travelIntensityPercent.coerceIn(
                    MIN_TIMESCAPE_TRAVEL_INTENSITY_PERCENT,
                    MAX_TIMESCAPE_TRAVEL_INTENSITY_PERCENT,
                ),
            parallaxIntensityPercent =
                parallaxIntensityPercent.coerceIn(
                    MIN_TIMESCAPE_PARALLAX_INTENSITY_PERCENT,
                    MAX_TIMESCAPE_PARALLAX_INTENSITY_PERCENT,
                ),
            rotationIntensityPercent =
                rotationIntensityPercent.coerceIn(
                    MIN_TIMESCAPE_ROTATION_INTENSITY_PERCENT,
                    MAX_TIMESCAPE_ROTATION_INTENSITY_PERCENT,
                ),
        )
}

enum class TimeScapeEasing { STANDARD, EMPHASIZED, GENTLE_SPRING }

enum class TimeScapeHapticStrength { OFF, LIGHT, MEDIUM, STRONG }

data class TimeScapeRendererCapabilities(val supportsBlur: Boolean = true, val supportsTexture: Boolean = true)

const val CURRENT_TIMESCAPE_APPEARANCE_VERSION = 1
const val MIN_TIMESCAPE_CARD_ASPECT_RATIO_PERCENT = 55
const val MAX_TIMESCAPE_CARD_ASPECT_RATIO_PERCENT = 100
const val MIN_TIMESCAPE_FOCUSED_SCALE_PERCENT = 85
const val MAX_TIMESCAPE_FOCUSED_SCALE_PERCENT = 115
const val MIN_TIMESCAPE_FOCUSED_GAP_DP = 0
const val MAX_TIMESCAPE_FOCUSED_GAP_DP = 64
const val MIN_TIMESCAPE_VISIBLE_DEPTH = 1
const val MAX_TIMESCAPE_VISIBLE_DEPTH = 6
const val MIN_TIMESCAPE_OVERLAP_PERCENT = 0
const val MAX_TIMESCAPE_OVERLAP_PERCENT = 60
const val MIN_TIMESCAPE_VERTICAL_SPACING_DP = 0
const val MAX_TIMESCAPE_VERTICAL_SPACING_DP = 96
const val MIN_TIMESCAPE_HORIZONTAL_OFFSET_DP = 0
const val MAX_TIMESCAPE_HORIZONTAL_OFFSET_DP = 160
const val MIN_TIMESCAPE_CURVE_DP = 0
const val MAX_TIMESCAPE_CURVE_DP = 96
const val MIN_TIMESCAPE_ROTATION_DEGREES = 0
const val MAX_TIMESCAPE_ROTATION_DEGREES = 18
const val MIN_TIMESCAPE_CORNER_RADIUS_DP = 0
const val MAX_TIMESCAPE_CORNER_RADIUS_DP = 64
const val MIN_TIMESCAPE_CONTENT_PADDING_DP = 0
const val MAX_TIMESCAPE_CONTENT_PADDING_DP = 64
const val MIN_TIMESCAPE_ARGB = 0L
const val MAX_TIMESCAPE_ARGB = 0xFFFFFFFFL
const val MIN_TIMESCAPE_GLASS_TRANSPARENCY_PERCENT = 0
const val MAX_TIMESCAPE_GLASS_TRANSPARENCY_PERCENT = 95
const val MIN_TIMESCAPE_BLUR_STRENGTH_PERCENT = 0
const val MAX_TIMESCAPE_BLUR_STRENGTH_PERCENT = 100
const val MIN_TIMESCAPE_SATURATION_PERCENT = 50
const val MAX_TIMESCAPE_SATURATION_PERCENT = 150
const val MIN_TIMESCAPE_CONTRAST_PERCENT = 75
const val MAX_TIMESCAPE_CONTRAST_PERCENT = 150
const val MIN_TIMESCAPE_OUTLINE_WIDTH_DP = 0
const val MAX_TIMESCAPE_OUTLINE_WIDTH_DP = 4
const val MIN_TIMESCAPE_HIGHLIGHT_PERCENT = 0
const val MAX_TIMESCAPE_HIGHLIGHT_PERCENT = 100
const val MIN_TIMESCAPE_SHADOW_ELEVATION_DP = 0
const val MAX_TIMESCAPE_SHADOW_ELEVATION_DP = 32
const val MIN_TIMESCAPE_TEXTURE_INTENSITY_PERCENT = 0
const val MAX_TIMESCAPE_TEXTURE_INTENSITY_PERCENT = 40
const val MIN_TIMESCAPE_TEXT_SCALE_PERCENT = 85
const val MAX_TIMESCAPE_TEXT_SCALE_PERCENT = 130
const val MIN_TIMESCAPE_SETTLE_DURATION_MILLIS = 80
const val MAX_TIMESCAPE_SETTLE_DURATION_MILLIS = 600
const val MIN_TIMESCAPE_TRANSITION_DURATION_MILLIS = 80
const val MAX_TIMESCAPE_TRANSITION_DURATION_MILLIS = 700
const val MIN_TIMESCAPE_SPRING_BOUNCINESS_PERCENT = 0
const val MAX_TIMESCAPE_SPRING_BOUNCINESS_PERCENT = 40
const val MIN_TIMESCAPE_TRAVEL_INTENSITY_PERCENT = 0
const val MAX_TIMESCAPE_TRAVEL_INTENSITY_PERCENT = 150
const val MIN_TIMESCAPE_PARALLAX_INTENSITY_PERCENT = 0
const val MAX_TIMESCAPE_PARALLAX_INTENSITY_PERCENT = 50
const val MIN_TIMESCAPE_ROTATION_INTENSITY_PERCENT = 0
const val MAX_TIMESCAPE_ROTATION_INTENSITY_PERCENT = 150
const val MIN_TIMESCAPE_REACHABLE_CARD_WIDTH_DP = 160
const val MIN_TIMESCAPE_REACHABLE_CARD_HEIGHT_DP = 220
const val MIN_TIMESCAPE_BACKGROUND_CARD_SCALE = 0.94f
