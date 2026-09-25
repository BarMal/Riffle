package com.riffle.core.domain.launcher.dockpull

/**
 * Pure numbers for how a dock pull's COMMIT re-orientation is drawn (follow-up to #1278): the
 * "iPhone Duo" fold-overlay reference's frost/darken ramp and reveal hold, and the App Library-style
 * per-icon stagger. Everything here is a function of the same 0..1 progress the UI already carries
 * ([dockBackgroundAlpha][com.riffle.core.domain.launcher.dockpull.DockPullTransitionState.dockBackgroundAlpha]
 * for the frost/tilt, index for the stagger); nothing here animates, exactly like [DockPullFrame].
 *
 * How strongly the dock's frost (darken, and blur where available) shows for a dock background
 * alpha of [dockBackgroundAlpha]: the same pull/settle progress the background fade already uses,
 * inverted so the frost is strongest while the background is most faded and clears as it settles
 * back to opaque. Not a flat/linear alpha fade on its own -- callers multiply this into a darken
 * strength and a blur radius, both of which ramp together.
 */
fun dockPullReorientFrostStrength(dockBackgroundAlpha: Float): Float = (1f - dockBackgroundAlpha).coerceIn(0f, 1f)

/**
 * The perspective tilt (degrees) for a dock background alpha of [dockBackgroundAlpha], up to
 * [maxDegrees]: proportional to [dockPullReorientFrostStrength], so it eases back to flat exactly as
 * the frost clears on settle. Kept small by [maxDegrees]'s default -- a polish detail, not a hinge
 * simulator.
 */
fun dockPullReorientTiltDegrees(
    dockBackgroundAlpha: Float,
    maxDegrees: Float = DOCK_PULL_REORIENT_MAX_TILT_DEGREES,
): Float = dockPullReorientFrostStrength(dockBackgroundAlpha) * maxDegrees

/**
 * The stagger delay (ms) for the item at [index] in an App Library-style reflow: a small per-item
 * offset so the reorient reads as a wave, capped by [capMillis] so a dock with many items still
 * finishes its whole reflow within the settle duration.
 */
fun dockPullItemStaggerDelayMillis(
    index: Int,
    stepMillis: Int = DOCK_PULL_REORIENT_STAGGER_STEP_MILLIS,
    capMillis: Int = DOCK_PULL_REORIENT_STAGGER_CAP_MILLIS,
): Int = (index.coerceAtLeast(0) * stepMillis).coerceIn(0, capMillis)

/**
 * Whether the item at [index] (0-based, in the order it is drawn) has a slot in a destination dock
 * of [destinationCapacity]. Reuses the destination [com.riffle.core.domain.launcher.home.DockModel]'s
 * own capacity rather than inventing a new resolution -- an item beyond it fades out under the frost
 * in place instead of being force-positioned (Decision: capacity mismatch).
 */
fun dockPullItemFitsDestination(
    index: Int,
    destinationCapacity: Int,
): Boolean = index < destinationCapacity

/** Default per-item stagger step (App Library-style: 15-30ms between icons). */
const val DOCK_PULL_REORIENT_STAGGER_STEP_MILLIS: Int = 20

/** The whole reflow's stagger never pushes the last item out further than this. */
const val DOCK_PULL_REORIENT_STAGGER_CAP_MILLIS: Int = 120

/** Default max perspective tilt at full frost -- a few degrees, restrained on purpose. */
const val DOCK_PULL_REORIENT_MAX_TILT_DEGREES: Float = 6f

/**
 * How long a freshly-committed dock holds its incoming content near-invisible before it resolves to
 * legible, so the swap doesn't pop under the frost (duo-open's ~0.4s "black to reveal" hold, shortened
 * for a UI dock rather than a full display panel).
 */
const val DOCK_PULL_REORIENT_REVEAL_HOLD_MILLIS: Int = 180
