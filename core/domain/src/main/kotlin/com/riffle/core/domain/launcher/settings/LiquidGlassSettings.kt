package com.riffle.core.domain.launcher.settings

/**
 * Riffle's "liquid glass" material -- a real backdrop-blur-and-refraction treatment (see
 * `GlassSurface.kt`) applied to the launcher's floating chrome (dock pills, card-stack header,
 * settings sheets). Glass is always on: there is no master switch, and reduced transparency
 * ([AppearanceSettings.reducedTransparency]) is the only way it is turned off. A stored `enabled`
 * flag from older builds is ignored on decode. The two strengths are independent axes rather
 * than one combined "amount" slider, per the design research behind this feature: a person who
 * wants the frosted look but finds edge lensing distracting (or vice versa) should be able to say
 * so without a single dial fighting them.
 *
 * Deliberately conservative defaults: Apple shipped Liquid Glass too transparent by default at
 * WWDC25 and had to walk it back twice after backlash, so [frostStrengthPercent] defaults to a
 * moderate midpoint rather than the maximum, and [refractionStrengthPercent] -- the more visually
 * noisy of the two -- defaults low.
 *
 * No motion on/off axis lives here: unlike iOS, Android has no OS-level "Reduce Transparency"
 * signal, and this feature's own motion rides the launcher's existing, already system-aware
 * [MotionSettings.reducedMotion] rather than inventing a second one. [spotlightFlowMillis] is a
 * pace, not a switch: how slowly the dock's icon-colour spotlight follows its icons when they move
 * (0 follows instantly); reduced motion still overrides it to instant.
 */
data class LiquidGlassSettings(
    val frostStrengthPercent: Int = DEFAULT_LIQUID_GLASS_FROST_STRENGTH_PERCENT,
    val refractionStrengthPercent: Int = DEFAULT_LIQUID_GLASS_REFRACTION_STRENGTH_PERCENT,
    val spotlightFlowMillis: Int = DEFAULT_SPOTLIGHT_FLOW_MILLIS,
)

const val MIN_LIQUID_GLASS_STRENGTH_PERCENT = 0
const val MAX_LIQUID_GLASS_STRENGTH_PERCENT = 100
const val DEFAULT_LIQUID_GLASS_FROST_STRENGTH_PERCENT = 45
const val DEFAULT_LIQUID_GLASS_REFRACTION_STRENGTH_PERCENT = 20

/** The spotlight's flow time constant: 0 follows the icons instantly, 1000 drifts after them. */
const val MIN_SPOTLIGHT_FLOW_MILLIS = 0
const val MAX_SPOTLIGHT_FLOW_MILLIS = 1000
const val SPOTLIGHT_FLOW_STEP_MILLIS = 50
const val DEFAULT_SPOTLIGHT_FLOW_MILLIS = 350

fun LiquidGlassSettings.coerced(): LiquidGlassSettings =
    copy(
        frostStrengthPercent =
            frostStrengthPercent.coerceIn(MIN_LIQUID_GLASS_STRENGTH_PERCENT, MAX_LIQUID_GLASS_STRENGTH_PERCENT),
        refractionStrengthPercent =
            refractionStrengthPercent.coerceIn(MIN_LIQUID_GLASS_STRENGTH_PERCENT, MAX_LIQUID_GLASS_STRENGTH_PERCENT),
        spotlightFlowMillis = spotlightFlowMillis.coerceIn(MIN_SPOTLIGHT_FLOW_MILLIS, MAX_SPOTLIGHT_FLOW_MILLIS),
    )

/**
 * The single resolved value every liquid-glass surface reads: the persisted strengths (coerced),
 * folded with the launcher-wide reduced-motion signal so a glass surface can gate its specular/morph
 * animation without needing its own settings object threaded through.
 */
fun LauncherSettings.resolveLiquidGlass(): ResolvedLiquidGlass =
    liquidGlass.coerced().let { settings ->
        ResolvedLiquidGlass(
            frostStrengthFraction = settings.frostStrengthPercent / 100f,
            refractionStrengthFraction = settings.refractionStrengthPercent / 100f,
            reducedMotion = motion.reducedMotion,
            reducedTransparency = appearance.reducedTransparency,
            spotlightFlowSeconds = settings.spotlightFlowMillis / MILLIS_PER_SECOND,
        )
    }

data class ResolvedLiquidGlass(
    val frostStrengthFraction: Float,
    val refractionStrengthFraction: Float,
    val reducedMotion: Boolean,
    /** Every glass surface draws opaque, with a faint sheen; see [AppearanceSettings.reducedTransparency]. */
    val reducedTransparency: Boolean = false,
    /** The spotlight's flow time constant in seconds; 0 follows instantly. */
    val spotlightFlowSeconds: Float = DEFAULT_SPOTLIGHT_FLOW_MILLIS / MILLIS_PER_SECOND,
) {
    /** Whether a glass surface may sample the backdrop: transparency is not reduced. */
    val samplesBackdrop: Boolean get() = !reducedTransparency
}

private const val MILLIS_PER_SECOND = 1000f

/**
 * Sets the one top-level reduced-transparency switch ([AppearanceSettings.reducedTransparency]).
 * Until the adaptive stage's own surface is replaced it still reads its own copy in
 * [AdaptiveStageMotion], so both are kept in step here; nothing else writes either.
 */
fun LauncherSettings.withReducedTransparency(reduced: Boolean): LauncherSettings =
    copy(
        appearance = appearance.copy(reducedTransparency = reduced),
        cards =
            cards.copy(
                adaptiveStageAppearance =
                    cards.adaptiveStageAppearance.copy(
                        motion = cards.adaptiveStageAppearance.motion.copy(reducedTransparency = reduced),
                    ),
                unfoldedAppearance =
                    cards.unfoldedAppearance.copy(
                        motion = cards.unfoldedAppearance.motion.copy(reducedTransparency = reduced),
                    ),
            ),
    )
