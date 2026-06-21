package com.riffle.app.launcher

import com.riffle.core.domain.launcher.settings.GestureSettings
import com.riffle.core.domain.launcher.settings.HomeSwipeGestureSettings
import com.riffle.core.domain.launcher.settings.LauncherGestureAction
import org.json.JSONObject

fun encodeGestures(settings: GestureSettings): JSONObject =
    JSONObject()
        .put("homeSwipe", encodeHomeSwipeGestures(settings.homeSwipe))

fun JSONObject.toGestures(defaults: GestureSettings): GestureSettings =
    defaults.copy(
        homeSwipe = optJSONObject("homeSwipe")?.toHomeSwipeGestures(defaults.homeSwipe) ?: defaults.homeSwipe,
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
