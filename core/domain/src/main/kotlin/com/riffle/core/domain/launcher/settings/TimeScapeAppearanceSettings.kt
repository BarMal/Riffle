package com.riffle.core.domain.launcher.settings

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
                            if (settings.motion.reducedTransparency) 0 else settings.surface.glassTransparencyPercent,
                        textureIntensityPercent = if (capabilities.supportsTexture) settings.surface.textureIntensityPercent else 0,
                    ),
                motion =
                    if (settings.motion.reducedMotion) {
                        settings.motion.copy(
                            settleDurationMillis = 0,
                            reflowDurationMillis = 0,
                            enterDurationMillis = 0,
                            exitDurationMillis = 0,
                            expandDurationMillis = 0,
                            travelIntensityPercent = 0,
                            parallaxIntensityPercent = 0,
                            rotationIntensityPercent = 0,
                        )
                    } else {
                        settings.motion
                    },
            )
        }

    companion object {
        fun modern(): TimeScapeAppearanceSettings = TimeScapeAppearancePreset.MODERN_TIMESCAPE.settings
    }
}

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
                    55,
                    100,
                ),
            focusedScalePercent =
                focusedScalePercent.coerceIn(
                    85,
                    115,
                ),
            focusedGapDp =
                focusedGapDp.coerceIn(
                    0,
                    64,
                ),
            visibleDepth =
                visibleDepth.coerceIn(
                    1,
                    6,
                ),
            overlapPercent =
                overlapPercent.coerceIn(
                    0,
                    60,
                ),
            verticalSpacingDp =
                verticalSpacingDp.coerceIn(
                    0,
                    96,
                ),
            horizontalOffsetDp =
                horizontalOffsetDp.coerceIn(
                    0,
                    160,
                ),
            curveDp =
                curveDp.coerceIn(
                    0,
                    96,
                ),
            rotationDegrees =
                rotationDegrees.coerceIn(
                    0,
                    18,
                ),
            cornerRadiusDp =
                cornerRadiusDp.coerceIn(
                    0,
                    64,
                ),
            contentPaddingDp = contentPaddingDp.coerceIn(0, 64),
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
                    0,
                    0xFFFFFFFFL,
                ),
            glassTintArgb =
                glassTintArgb.coerceIn(
                    0,
                    0xFFFFFFFFL,
                ),
            glassTransparencyPercent =
                glassTransparencyPercent.coerceIn(
                    0,
                    95,
                ),
            blurStrengthPercent =
                blurStrengthPercent.coerceIn(
                    0,
                    100,
                ),
            saturationPercent =
                saturationPercent.coerceIn(
                    50,
                    150,
                ),
            contrastPercent =
                contrastPercent.coerceIn(
                    75,
                    150,
                ),
            outlineWidthDp =
                outlineWidthDp.coerceIn(
                    0,
                    4,
                ),
            highlightPercent =
                highlightPercent.coerceIn(
                    0,
                    100,
                ),
            shadowElevationDp =
                shadowElevationDp.coerceIn(
                    0,
                    32,
                ),
            textureIntensityPercent = textureIntensityPercent.coerceIn(0, 40),
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
            customAccentArgb = customAccentArgb.coerceIn(0, 0xFFFFFFFFL),
            textScalePercent = textScalePercent.coerceIn(85, 130),
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
                    80,
                    600,
                ),
            reflowDurationMillis =
                reflowDurationMillis.coerceIn(
                    80,
                    700,
                ),
            enterDurationMillis =
                enterDurationMillis.coerceIn(
                    80,
                    700,
                ),
            exitDurationMillis =
                exitDurationMillis.coerceIn(
                    80,
                    700,
                ),
            expandDurationMillis =
                expandDurationMillis.coerceIn(
                    80,
                    700,
                ),
            springBouncinessPercent =
                springBouncinessPercent.coerceIn(
                    0,
                    40,
                ),
            travelIntensityPercent =
                travelIntensityPercent.coerceIn(
                    0,
                    150,
                ),
            parallaxIntensityPercent =
                parallaxIntensityPercent.coerceIn(
                    0,
                    50,
                ),
            rotationIntensityPercent = rotationIntensityPercent.coerceIn(0, 150),
        )
}

enum class TimeScapeEasing { STANDARD, EMPHASIZED, GENTLE_SPRING }

enum class TimeScapeHapticStrength { OFF, LIGHT, MEDIUM, STRONG }

data class TimeScapeRendererCapabilities(val supportsBlur: Boolean = true, val supportsTexture: Boolean = true)

const val CURRENT_TIMESCAPE_APPEARANCE_VERSION = 1
