package com.riffle.core.domain.launcher.settings

data class HomeGestureConflict(
    val action: LauncherGestureAction,
    val gestures: List<HomeGesture>,
)

object HomeGestureConflictDetector {
    fun conflictsIn(settings: HomeGestureSettings): List<HomeGestureConflict> =
        LauncherGestureConflictDetector
            .conflictsIn(settings.toLauncherGestureMappings())
            .filter { it.surface == LauncherGestureSurface.HOME_PAGE }
            .flatMap { conflict ->
                conflict.homeGestureConflicts(settings)
            }
}

private fun LauncherGestureConflict.homeGestureConflicts(settings: HomeGestureSettings): List<HomeGestureConflict> {
    val homeGestures = gestures.map(LauncherGesture::toHomeGesture)
    if (!action.requiresLaunchTarget) {
        return listOf(HomeGestureConflict(action = action, gestures = homeGestures))
    }

    return homeGestures
        .groupBy(settings::launchTargetFor)
        .filterKeys { target -> target != null }
        .filterValues { targetGestures -> targetGestures.size > 1 }
        .map { (_, targetGestures) ->
            HomeGestureConflict(action = action, gestures = targetGestures)
        }
}

private val LauncherGestureAction.requiresLaunchTarget: Boolean
    get() = this == LauncherGestureAction.LAUNCH_APP || this == LauncherGestureAction.LAUNCH_APP_SHORTCUT
