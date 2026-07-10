package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.LauncherViewMode
import org.json.JSONArray
import org.json.JSONObject

internal fun encodePreferredModes(preferredModes: Map<HomeLayoutDeviceClass, LauncherViewMode>): JSONArray =
    JSONArray(
        preferredModes.map { (deviceClass, mode) ->
            JSONObject()
                .put("deviceClass", deviceClass.name)
                .put("viewMode", mode.name)
        },
    )

internal fun JSONObject.optPreferredModes(): Map<HomeLayoutDeviceClass, LauncherViewMode> =
    optJSONArray("preferredModes")
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
