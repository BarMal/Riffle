package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.LauncherViewMode
import org.json.JSONArray
import org.json.JSONObject

// A device-class-to-mode map, encoded and decoded the same way wherever one is stored -- the
// active-mode preferences and the leaving-Cards return modes are both this shape.
internal fun encodeDeviceClassModes(modes: Map<HomeLayoutDeviceClass, LauncherViewMode>): JSONArray =
    JSONArray(
        modes.map { (deviceClass, mode) ->
            JSONObject()
                .put("deviceClass", deviceClass.name)
                .put("viewMode", mode.name)
        },
    )

internal fun JSONObject.optDeviceClassModes(key: String): Map<HomeLayoutDeviceClass, LauncherViewMode> =
    optJSONArray(key)
        ?.toPreferredModes()
        .orEmpty()

private fun JSONArray.toPreferredModes(): Map<HomeLayoutDeviceClass, LauncherViewMode> =
    (0 until length())
        .mapNotNull { index ->
            optJSONObject(index)?.toPreferredModeEntry()
        }
        .toMap()

private fun JSONObject.toPreferredModeEntry(): Pair<HomeLayoutDeviceClass, LauncherViewMode>? {
    val deviceClass =
        optString("deviceClass", "")
            .takeIf(String::isNotBlank)
            ?.let { value -> runCatching { HomeLayoutDeviceClass.valueOf(value) }.getOrNull() }
            ?: HomeLayoutDeviceClass.PHONE
    val mode =
        optString("viewMode", "")
            .takeIf(String::isNotBlank)
            ?.let { value -> runCatching { LauncherViewMode.valueOf(value) }.getOrNull() }
            ?: return null

    return deviceClass to mode
}
