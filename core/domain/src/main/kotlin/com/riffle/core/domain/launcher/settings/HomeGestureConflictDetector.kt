package com.riffle.core.domain.launcher.settings

data class HomeGestureConflict(
    val action: LauncherGestureAction,
    val gestures: List<HomeGesture>,
)

object HomeGestureConflictDetector {
    fun conflictsIn(settings: HomeGestureSettings): List<HomeGestureConflict> =
        settings.actions.entries
            .filter { (_, action) -> action != LauncherGestureAction.NONE }
            .groupBy(keySelector = { it.value }, valueTransform = { it.key })
            .filterValues { gestures -> gestures.size > 1 }
            .map { (action, gestures) -> HomeGestureConflict(action = action, gestures = gestures) }
}
