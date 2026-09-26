package com.riffle.core.domain.launcher.dockpull

import com.riffle.core.domain.launcher.dockpull.DockPullTransitionState.Idle
import com.riffle.core.domain.launcher.dockpull.DockPullTransitionState.Settling

/**
 * The same commit as a pull, without one (Decision 12): the dock's accessibility action and
 * keyboard shortcut. From [Idle] it goes straight to a committing [Settling] from progress 0, so the
 * UI runs the ordinary settle (or, under reduced motion, its crossfade) and switches on finish. A
 * transition already under way is left alone.
 */
fun DockPullTransitionController.switchNow(
    state: DockPullTransitionState,
    direction: DockPullDirection,
    reducedMotion: Boolean = state.reducedMotion,
): DockPullTransitionState =
    when (state) {
        is Idle ->
            Settling(
                from = state.surface,
                to = state.surface.other,
                direction = direction,
                travelDp = SWITCH_NOW_TRAVEL_DP,
                outcome = SettleOutcome.COMMIT,
                releaseProgress = 0f,
                reducedMotion = reducedMotion,
            )

        else -> state
    }

/** Any positive travel: a switch without a pull never reads it. */
private const val SWITCH_NOW_TRAVEL_DP = 1f
