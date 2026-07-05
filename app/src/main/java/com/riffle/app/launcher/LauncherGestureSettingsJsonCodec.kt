package com.riffle.app.launcher

import com.riffle.core.domain.launcher.settings.GestureSettings
import com.riffle.core.domain.launcher.settings.HomeGesture
import com.riffle.core.domain.launcher.settings.HomeGestureSettings
import com.riffle.core.domain.launcher.settings.HomeSwipeGestureSettings
import com.riffle.core.domain.launcher.settings.LauncherGestureAction
import com.riffle.core.domain.launcher.settings.toHomeGestureSettings
import org.json.JSONObject

fun encodeGestures(settings: GestureSettings): JSONObject =
    JSONObject()
        .put("homeGestures", encodeHomeGestures(settings.homeGestures))
        .put("homeSwipe", encodeHomeSwipeGestures(settings.homeSwipe))

fun JSONObject.toGestures(defaults: GestureSettings): GestureSettings =
    defaults.copy(
        homeGestures =
            optJSONObject("homeGestures")?.toHomeGestures(defaults.homeGestures)
                ?: optJSONObject("homeSwipe")?.toHomeSwipeGestures(defaults.homeSwipe)?.toHomeGestureSettings()
                ?: defaults.homeGestures,
    )

private fun encodeHomeGestures(settings: HomeGestureSettings): JSONObject =
    JSONObject().apply {
        HomeGesture.entries.forEach { gesture ->
            put(gesture.name, settings.actionFor(gesture).name)
        }
    }

private fun JSONObject.toHomeGestures(defaults: HomeGestureSettings): HomeGestureSettings =
    HomeGestureSettings(
        actions =
            HomeGesture.entries.associateWith { gesture ->
                optGestureAction(gesture.name, defaults.actionFor(gesture))
            },
    )

private fun encodeHomeSwipeGestures(settings: HomeSwipeGestureSettings): JSONObject =
    JSONObject()
        .put("up", settings.up.name)
        .put("down", settings.down.name)
        .put("left", settings.left.name)
        .put("right", settings.right.name)

private fun JSONObject.toHomeSwipeGestures(defaults: HomeSwipeGestureSettings): HomeSwipeGestureSettings =
    HomeSwipeGestureSettings(
        up = optGestureAction("up", defaults.up),
        down = optGestureAction("down", defaults.down),
        left = optGestureAction("left", defaults.left),
        right = optGestureAction("right", defaults.right),
    )

private fun JSONObject.optGestureAction(
    name: String,
    default: LauncherGestureAction,
): LauncherGestureAction =
    runCatching { LauncherGestureAction.valueOf(optString(name)) }
        .getOrDefault(default)
