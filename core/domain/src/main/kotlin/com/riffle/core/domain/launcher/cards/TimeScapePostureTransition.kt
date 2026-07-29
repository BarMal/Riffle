package com.riffle.core.domain.launcher.cards

/** The physical posture relevant to the compact and Stage Manager TimeScape surfaces. */
enum class TimeScapePosture {
    UNKNOWN,
    COMPACT,
    UNFOLDED,
    PARTIALLY_FOLDED,
    TABLETOP,
}

/**
 * Small, deterministic reducer for posture changes. A new report replaces an interrupted pending
 * transition, so rapid unfold/fold changes cannot leave the renderer with stale target state.
 */
data class TimeScapePostureTransitionState(
    val settledPosture: TimeScapePosture = TimeScapePosture.UNKNOWN,
    val pendingPosture: TimeScapePosture? = null,
) {
    val effectivePosture: TimeScapePosture
        get() = pendingPosture ?: settledPosture

    fun transitionTo(posture: TimeScapePosture): TimeScapePostureTransitionState =
        if (posture == settledPosture) {
            copy(pendingPosture = null)
        } else {
            copy(pendingPosture = posture)
        }

    fun settle(): TimeScapePostureTransitionState =
        pendingPosture?.let { posture -> copy(settledPosture = posture, pendingPosture = null) } ?: this
}

/** Stable interaction identity retained while the launcher moves between Home destinations. */
data class TimeScapeInteractionContext(
    val selectedStageKey: String? = null,
    val focusedCardKey: String? = null,
    val detailCardKey: String? = null,
    val templateId: String? = null,
    val scrollOffsetPx: Int = 0,
) {
    fun reconcile(
        availableStageKeys: Set<String>,
        availableCardKeys: Set<String>,
    ): TimeScapeInteractionContext =
        copy(
            selectedStageKey = selectedStageKey?.takeIf(availableStageKeys::contains),
            focusedCardKey = focusedCardKey?.takeIf(availableCardKeys::contains),
            detailCardKey = detailCardKey?.takeIf(availableCardKeys::contains),
        )
}
