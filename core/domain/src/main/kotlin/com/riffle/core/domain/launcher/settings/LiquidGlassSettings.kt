package com.riffle.core.domain.launcher.settings

/**
 * Riffle's "liquid glass" material -- a real backdrop-blur-and-refraction treatment (see
 * `GlassSurface.kt`) applied to the launcher's floating chrome (dock pills, card-stack header,
 * settings sheets). [enabled] is the master switch; the two strengths are independent axes rather
 * than one combined "amount" slider, per the design research behind this feature: a person who
 * wants the frosted look but finds edge lensing distracting (or vice versa) should be able to say
 * so without a single dial fighting them.
 *
 * Deliberately conservative defaults: Apple shipped Liquid Glass too transparent by default at
 * WWDC25 and had to walk it back twice after backlash, so [frostStrengthPercent] defaults to a
 * moderate midpoint rather than the maximum, and [refractionStrengthPercent] -- the more visually
 * noisy of the two -- defaults low.
 *
 * No motion axis lives here: unlike iOS, Android has no OS-level "Reduce Transparency" signal, and
 * this feature's own motion (a specular sweep, once later PRs animate it) rides the launcher's
 * existing, already system-aware [MotionSettings.reducedMotion] rather than inventing a second one.
 */
data class LiquidGlassSettings(
    val enabled: Boolean = true,
    val frostStrengthPercent: Int = DEFAULT_LIQUID_GLASS_FROST_STRENGTH_PERCENT,
    val refractionStrengthPercent: Int = DEFAULT_LIQUID_GLASS_REFRACTION_STRENGTH_PERCENT,
)

const val MIN_LIQUID_GLASS_STRENGTH_PERCENT = 0
const val MAX_LIQUID_GLASS_STRENGTH_PERCENT = 100
const val DEFAULT_LIQUID_GLASS_FROST_STRENGTH_PERCENT = 45
const val DEFAULT_LIQUID_GLASS_REFRACTION_STRENGTH_PERCENT = 20

fun LiquidGlassSettings.coerced(): LiquidGlassSettings =
    copy(
        frostStrengthPercent =
            frostStrengthPercent.coerceIn(MIN_LIQUID_GLASS_STRENGTH_PERCENT, MAX_LIQUID_GLASS_STRENGTH_PERCENT),
        refractionStrengthPercent =
            refractionStrengthPercent.coerceIn(MIN_LIQUID_GLASS_STRENGTH_PERCENT, MAX_LIQUID_GLASS_STRENGTH_PERCENT),
    )

/**
 * The single resolved value every liquid-glass surface reads: the persisted strengths (coerced),
 * folded with the launcher-wide reduced-motion signal so a glass surface can gate its specular/morph
 * animation without needing its own settings object threaded through.
 */
fun LauncherSettings.resolveLiquidGlass(): ResolvedLiquidGlass =
    liquidGlass.coerced().let { settings ->
        ResolvedLiquidGlass(
            enabled = settings.enabled,
            frostStrengthFraction = settings.frostStrengthPercent / 100f,
            refractionStrengthFraction = settings.refractionStrengthPercent / 100f,
            reducedMotion = motion.reducedMotion,
        )
    }

data class ResolvedLiquidGlass(
    val enabled: Boolean,
    val frostStrengthFraction: Float,
    val refractionStrengthFraction: Float,
    val reducedMotion: Boolean,
)
