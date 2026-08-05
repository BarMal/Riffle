package com.riffle.core.domain.launcher.cards

/** The physical posture relevant to the compact and docked-rail AdaptiveStage surfaces. */
enum class AdaptiveStagePosture {
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
data class AdaptiveStagePostureTransitionState(
    val settledPosture: AdaptiveStagePosture = AdaptiveStagePosture.UNKNOWN,
    val pendingPosture: AdaptiveStagePosture? = null,
) {
    val effectivePosture: AdaptiveStagePosture
        get() = pendingPosture ?: settledPosture

    fun transitionTo(posture: AdaptiveStagePosture): AdaptiveStagePostureTransitionState =
        if (posture == settledPosture) {
            copy(pendingPosture = null)
        } else {
            copy(pendingPosture = posture)
        }

    fun settle(): AdaptiveStagePostureTransitionState =
        pendingPosture?.let { posture -> copy(settledPosture = posture, pendingPosture = null) } ?: this
}

/** Stable interaction identity retained while the launcher moves between Home destinations. */
data class AdaptiveStageInteractionContext(
    val selectedStageKey: String? = null,
    val detailStageKey: String? = null,
    val focusedCardKey: String? = null,
    val detailCardKey: String? = null,
    val templateId: String? = null,
    val scrollOffsetPx: Int = 0,
) {
    fun reconcile(
        availableStageKeys: Set<String>,
        availableCardKeys: Set<String>,
    ): AdaptiveStageInteractionContext =
        copy(
            selectedStageKey = selectedStageKey?.takeIf(availableStageKeys::contains),
            detailStageKey = detailStageKey?.takeIf(availableStageKeys::contains),
            focusedCardKey = focusedCardKey?.takeIf(availableCardKeys::contains),
            detailCardKey = detailCardKey?.takeIf(availableCardKeys::contains),
        )
}
