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
            .map { conflict ->
                HomeGestureConflict(
                    action = conflict.action,
                    gestures = conflict.gestures.map(LauncherGesture::toHomeGesture),
                )
            }
}
