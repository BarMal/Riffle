package com.riffle.core.domain.launcher.settings

data class LauncherGestureMappings(
    val actions: Map<LauncherGestureSurface, Map<LauncherGesture, LauncherGestureAction>> = emptyMap(),
) {
    fun actionFor(
        surface: LauncherGestureSurface,
        gesture: LauncherGesture,
    ): LauncherGestureAction = actions[surface]?.get(gesture) ?: LauncherGestureAction.NONE

    fun withAction(
        surface: LauncherGestureSurface,
        gesture: LauncherGesture,
        action: LauncherGestureAction,
    ): LauncherGestureMappings =
        copy(
            actions =
                actions + (surface to ((actions[surface] ?: emptyMap()) + (gesture to action))),
        )
}

data class LauncherGestureConflict(
    val surface: LauncherGestureSurface,
    val action: LauncherGestureAction,
    val gestures: List<LauncherGesture>,
)

enum class LauncherGestureSurface {
    HOME_PAGE,
    DOCK,
    CARD,
}

enum class LauncherGesture {
    ONE_FINGER_UP,
    ONE_FINGER_DOWN,
    ONE_FINGER_LEFT,
    ONE_FINGER_RIGHT,
    TWO_FINGER_UP,
    TWO_FINGER_DOWN,
    TWO_FINGER_LEFT,
    TWO_FINGER_RIGHT,
    THREE_FINGER_UP,
    THREE_FINGER_DOWN,
    THREE_FINGER_LEFT,
    THREE_FINGER_RIGHT,
    PINCH_IN,
    PINCH_OUT,
}

object LauncherGestureConflictDetector {
    fun conflictsIn(mappings: LauncherGestureMappings): List<LauncherGestureConflict> =
        mappings.actions.entries.flatMap { (surface, surfaceActions) ->
            surfaceActions.entries
                .filter { (_, action) -> action != LauncherGestureAction.NONE }
                .groupBy(keySelector = { it.value }, valueTransform = { it.key })
                .filterValues { gestures -> gestures.size > 1 }
                .map { (action, gestures) ->
                    LauncherGestureConflict(
                        surface = surface,
                        action = action,
                        gestures = gestures,
                    )
                }
        }
}

fun HomeGestureSettings.toLauncherGestureMappings(): LauncherGestureMappings =
    HomeGesture.entries.fold(LauncherGestureMappings()) { mappings, gesture ->
        mappings.withAction(
            surface = LauncherGestureSurface.HOME_PAGE,
            gesture = gesture.toLauncherGesture(),
            action = actionFor(gesture),
        )
    }

fun HomeGesture.toLauncherGesture(): LauncherGesture =
    when (this) {
        HomeGesture.ONE_FINGER_UP -> LauncherGesture.ONE_FINGER_UP
        HomeGesture.ONE_FINGER_DOWN -> LauncherGesture.ONE_FINGER_DOWN
        HomeGesture.ONE_FINGER_LEFT -> LauncherGesture.ONE_FINGER_LEFT
        HomeGesture.ONE_FINGER_RIGHT -> LauncherGesture.ONE_FINGER_RIGHT
        HomeGesture.TWO_FINGER_UP -> LauncherGesture.TWO_FINGER_UP
        HomeGesture.TWO_FINGER_DOWN -> LauncherGesture.TWO_FINGER_DOWN
        HomeGesture.TWO_FINGER_LEFT -> LauncherGesture.TWO_FINGER_LEFT
        HomeGesture.TWO_FINGER_RIGHT -> LauncherGesture.TWO_FINGER_RIGHT
        HomeGesture.THREE_FINGER_UP -> LauncherGesture.THREE_FINGER_UP
        HomeGesture.THREE_FINGER_DOWN -> LauncherGesture.THREE_FINGER_DOWN
        HomeGesture.THREE_FINGER_LEFT -> LauncherGesture.THREE_FINGER_LEFT
        HomeGesture.THREE_FINGER_RIGHT -> LauncherGesture.THREE_FINGER_RIGHT
        HomeGesture.PINCH_IN -> LauncherGesture.PINCH_IN
        HomeGesture.PINCH_OUT -> LauncherGesture.PINCH_OUT
    }

fun LauncherGesture.toHomeGesture(): HomeGesture =
    when (this) {
        LauncherGesture.ONE_FINGER_UP -> HomeGesture.ONE_FINGER_UP
        LauncherGesture.ONE_FINGER_DOWN -> HomeGesture.ONE_FINGER_DOWN
        LauncherGesture.ONE_FINGER_LEFT -> HomeGesture.ONE_FINGER_LEFT
        LauncherGesture.ONE_FINGER_RIGHT -> HomeGesture.ONE_FINGER_RIGHT
        LauncherGesture.TWO_FINGER_UP -> HomeGesture.TWO_FINGER_UP
        LauncherGesture.TWO_FINGER_DOWN -> HomeGesture.TWO_FINGER_DOWN
        LauncherGesture.TWO_FINGER_LEFT -> HomeGesture.TWO_FINGER_LEFT
        LauncherGesture.TWO_FINGER_RIGHT -> HomeGesture.TWO_FINGER_RIGHT
        LauncherGesture.THREE_FINGER_UP -> HomeGesture.THREE_FINGER_UP
        LauncherGesture.THREE_FINGER_DOWN -> HomeGesture.THREE_FINGER_DOWN
        LauncherGesture.THREE_FINGER_LEFT -> HomeGesture.THREE_FINGER_LEFT
        LauncherGesture.THREE_FINGER_RIGHT -> HomeGesture.THREE_FINGER_RIGHT
        LauncherGesture.PINCH_IN -> HomeGesture.PINCH_IN
        LauncherGesture.PINCH_OUT -> HomeGesture.PINCH_OUT
    }
