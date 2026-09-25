package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.ModeRing
import org.json.JSONArray
import org.json.JSONObject

/** Each device class's mode ring (#1225), as `[{"deviceClass": ..., "modes": [...]}]`. */
internal fun encodeModeRings(rings: Map<HomeLayoutDeviceClass, ModeRing>): JSONArray =
    JSONArray(
        rings.map { (deviceClass, ring) ->
            JSONObject()
                .put("deviceClass", deviceClass.name)
                .put("modes", JSONArray(ring.modes.map(LauncherViewMode::name)))
        },
    )

/**
 * The mode rings stored under [key], or null when there is no such key -- a set written before
 * rings existed, which the caller migrates. An entry that does not name a device class and 2-3
 * distinct known modes is dropped; its device class then falls back to a ring holding its mode.
 */
internal fun JSONObject.optModeRings(key: String): Map<HomeLayoutDeviceClass, ModeRing>? =
    optJSONArray(key)?.let { entries ->
        (0 until entries.length())
            .mapNotNull { index -> entries.optJSONObject(index)?.toModeRingEntry() }
            .toMap()
    }

private fun JSONObject.toModeRingEntry(): Pair<HomeLayoutDeviceClass, ModeRing>? {
    val deviceClass = HomeLayoutDeviceClass.entries.firstOrNull { entry -> entry.name == optString("deviceClass") }
    val ring =
        optJSONArray("modes")
            ?.let { names ->
                (0 until names.length()).mapNotNull { index ->
                    LauncherViewMode.entries.firstOrNull { mode -> mode.name == names.optString(index) }
                }
            }
            ?.let { modes -> ModeRing.of(modes) }

    return if (deviceClass != null && ring != null) deviceClass to ring else null
}
