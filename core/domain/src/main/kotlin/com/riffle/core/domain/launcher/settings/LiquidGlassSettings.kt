package com.riffle.core.domain.launcher.settings

/**
 * Riffle's glass material -- one flat translucent tint lit at its edges (see `GlassSurface.kt`)
 * applied to every translucent surface. Glass is always on: there is no master switch, and reduced
 * transparency ([AppearanceSettings.reducedTransparency]) is the only way it is turned off. Stored
 * `enabled`, `frostStrengthPercent` and `refractionStrengthPercent` values from older builds
 * (when glass blurred and refracted a captured backdrop) are ignored on decode.
 *
 * No motion on/off axis lives here: unlike iOS, Android has no OS-level "Reduce Transparency"
 * signal, and the material's motion rides the launcher's existing, already system-aware
 * [MotionSettings.reducedMotion]. [spotlightFlowMillis] is a pace, not a switch: how slowly the
 * dock's icon-colour spotlight follows its icons when they move (0 follows instantly); reduced
 * motion still overrides it to instant.
 */
data class LiquidGlassSettings(
    val spotlightFlowMillis: Int = DEFAULT_SPOTLIGHT_FLOW_MILLIS,
)

/** The spotlight's flow time constant: 0 follows the icons instantly, 1000 drifts after them. */
const val MIN_SPOTLIGHT_FLOW_MILLIS = 0
const val MAX_SPOTLIGHT_FLOW_MILLIS = 1000
const val SPOTLIGHT_FLOW_STEP_MILLIS = 50
const val DEFAULT_SPOTLIGHT_FLOW_MILLIS = 350

fun LiquidGlassSettings.coerced(): LiquidGlassSettings =
    copy(
        spotlightFlowMillis = spotlightFlowMillis.coerceIn(MIN_SPOTLIGHT_FLOW_MILLIS, MAX_SPOTLIGHT_FLOW_MILLIS),
    )

/**
 * The single resolved value every glass surface reads: the persisted spotlight pace (coerced),
 * folded with the launcher-wide reduced-motion signal so a glass surface can gate its specular/morph
 * animation without needing its own settings object threaded through.
 */
fun LauncherSettings.resolveLiquidGlass(): ResolvedLiquidGlass =
    liquidGlass.coerced().let { settings ->
        ResolvedLiquidGlass(
            reducedMotion = motion.reducedMotion,
            reducedTransparency = appearance.reducedTransparency,
            spotlightFlowSeconds = settings.spotlightFlowMillis / MILLIS_PER_SECOND,
        )
    }

data class ResolvedLiquidGlass(
    val reducedMotion: Boolean,
    /** Every glass surface draws opaque, with faint edge lights; see [AppearanceSettings.reducedTransparency]. */
    val reducedTransparency: Boolean = false,
    /** The spotlight's flow time constant in seconds; 0 follows instantly. */
    val spotlightFlowSeconds: Float = DEFAULT_SPOTLIGHT_FLOW_MILLIS / MILLIS_PER_SECOND,
)

private const val MILLIS_PER_SECOND = 1000f

/** Sets the one top-level reduced-transparency switch ([AppearanceSettings.reducedTransparency]). */
fun LauncherSettings.withReducedTransparency(reduced: Boolean): LauncherSettings =
    copy(appearance = appearance.copy(reducedTransparency = reduced))
