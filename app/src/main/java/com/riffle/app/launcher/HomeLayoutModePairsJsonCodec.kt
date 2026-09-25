package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.ModePair
import org.json.JSONArray
import org.json.JSONObject

/** Each device class's Home ↔ Library pair (#1241), as `[{"deviceClass": ..., "home": ...}]`. */
internal fun encodeModePairs(pairs: Map<HomeLayoutDeviceClass, ModePair>): JSONArray =
    JSONArray(
        pairs.map { (deviceClass, pair) ->
            JSONObject()
                .put("deviceClass", deviceClass.name)
                .put("home", pair.home.name)
        },
    )

/**
 * The mode pairs stored under [MODE_PAIRS_KEY], or null when there is no such key -- a set written
 * before pairs existed, which the caller migrates. An entry that does not name a device class and a
 * known non-Library Home mode is dropped; its device class then falls back to a pair holding its mode.
 */
internal fun JSONObject.optModePairs(): Map<HomeLayoutDeviceClass, ModePair>? =
    optJSONArray(MODE_PAIRS_KEY)?.let { entries ->
        (0 until entries.length())
            .mapNotNull { index -> entries.optJSONObject(index)?.toModePairEntry() }
            .toMap()
    }

/**
 * The mode rings of #1225 stored under [LEGACY_MODE_RINGS_KEY], only read to migrate a set written
 * before mode pairs; null when there is no such key. Each ring is the known modes it names, in order
 * (see [ModePair.fromLegacyRing]); an entry without a known device class is dropped.
 */
internal fun JSONObject.optLegacyModeRings(): Map<HomeLayoutDeviceClass, List<LauncherViewMode>>? =
    optJSONArray(LEGACY_MODE_RINGS_KEY)?.let { entries ->
        (0 until entries.length())
            .mapNotNull { index -> entries.optJSONObject(index)?.toLegacyModeRingEntry() }
            .toMap()
    }

private fun JSONObject.toModePairEntry(): Pair<HomeLayoutDeviceClass, ModePair>? {
    val deviceClass = optDeviceClassName()
    val pair = ModePair.of(LauncherViewMode.entries.firstOrNull { mode -> mode.name == optString("home") })

    return if (deviceClass != null && pair != null) deviceClass to pair else null
}

private fun JSONObject.toLegacyModeRingEntry(): Pair<HomeLayoutDeviceClass, List<LauncherViewMode>>? {
    val deviceClass = optDeviceClassName() ?: return null
    val modes =
        optJSONArray("modes")
            ?.let { names ->
                (0 until names.length()).mapNotNull { index ->
                    LauncherViewMode.entries.firstOrNull { mode -> mode.name == names.optString(index) }
                }
            }
            .orEmpty()

    return deviceClass to modes
}

private fun JSONObject.optDeviceClassName(): HomeLayoutDeviceClass? =
    HomeLayoutDeviceClass.entries.firstOrNull { entry -> entry.name == optString("deviceClass") }

internal const val MODE_PAIRS_KEY = "modePairs"
internal const val LEGACY_MODE_RINGS_KEY = "modeRings"
