package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.withLegacyDocksUnified
import org.json.JSONArray
import org.json.JSONObject

fun encodeHomeLayoutSet(layoutSet: HomeLayoutSet): String =
    JSONObject()
        .put("type", HOME_LAYOUT_SET_TYPE)
        .put("active", encodeLayoutKey(layoutSet.activeKey))
        .put("preferredModes", encodeDeviceClassModes(layoutSet.preferredModesByDeviceClass))
        .put("lastNonCardsModes", encodeDeviceClassModes(layoutSet.lastNonCardsModeByDeviceClass))
        .put("docks", encodeDocks(layoutSet))
        .put(
            "layouts",
            JSONArray(
                layoutSet.layouts.keys.map { key ->
                    // Each layout is written with its device class's shared dock (and the pages
                    // fitted to it), so the per-layout copy never disagrees with "docks" -- which is
                    // what an older build, that only knows per-layout docks, would read.
                    JSONObject()
                        .put("key", encodeLayoutKey(key))
                        .put("layout", encodeHomeLayoutObject(layoutSet.layoutFor(key)))
                },
            ),
        )
        .toString()

fun decodeHomeLayoutSet(value: String): HomeLayoutSet =
    JSONObject(value).let { json ->
        when {
            json.isHomeLayoutSetJson -> json.toHomeLayoutSet()
            else -> HomeLayoutSet.fromLayout(json.toHomeLayout())
        }
    }

internal fun JSONObject.toHomeLayoutSet(): HomeLayoutSet {
    val activeKey = optJSONObject("active")?.toLayoutKey() ?: HomeLayoutKey(HomeLayoutDefaults.standard().viewMode)
    val layouts =
        optJSONArray("layouts")
            ?.toHomeLayoutEntries()
            .orEmpty()
    val preferredModes = optDeviceClassModes("preferredModes")
    val lastNonCardsModes = optDeviceClassModes("lastNonCardsModes")
    val storedDocks = optJSONArray("docks")?.toDocks()

    return HomeLayoutSet(
        activeKey = activeKey,
        layouts = layouts.toMap(),
        preferredModesByDeviceClass =
            preferredModes.ifEmpty { mapOf(activeKey.deviceClass to activeKey.viewMode) },
        lastNonCardsModeByDeviceClass = lastNonCardsModes,
    ).let { layoutSet ->
        // A set written before the dock was shared (#1205) has no "docks": each mode kept its own,
        // and unifying them may have to rescue pins only another mode's dock held.
        storedDocks
            ?.let { docks -> layoutSet.copy(docks = layoutSet.docks + docks) }
            ?: layoutSet.withLegacyDocksUnified()
    }.let { layoutSet ->
        layoutSet.takeIf { set -> set.activeKey in set.layouts }
            ?: layoutSet.copy(layouts = layoutSet.layouts + (activeKey to layoutSet.layoutFor(activeKey)))
    }
}

private fun JSONArray.toHomeLayoutEntries(): List<Pair<HomeLayoutKey, HomeLayout>> =
    (0 until length())
        .mapNotNull { index ->
            optJSONObject(index)?.let { entry ->
                runCatching { entry.toHomeLayoutEntry() }.getOrNull()
            }
        }

private fun JSONObject.toHomeLayoutEntry(): Pair<HomeLayoutKey, HomeLayout> {
    val key = getJSONObject("key").toLayoutKey()
    val layout =
        getJSONObject("layout")
            .toHomeLayout(defaults = HomeLayoutDefaults.standard(key.deviceClass))
            .copy(viewMode = key.viewMode)

    return key to layout
}

private fun encodeLayoutKey(key: HomeLayoutKey): JSONObject =
    JSONObject()
        .put("viewMode", key.viewMode.name)
        .put("deviceClass", key.deviceClass.name)

private fun JSONObject.toLayoutKey(): HomeLayoutKey =
    HomeLayoutKey(
        viewMode = optViewMode(HomeLayoutDefaults.standard().viewMode),
        deviceClass = optDeviceClass(HomeLayoutDeviceClass.PHONE),
    )

private fun JSONObject.optViewMode(default: LauncherViewMode): LauncherViewMode =
    optString("viewMode", "")
        .takeIf(String::isNotBlank)
        ?.let { value -> runCatching { LauncherViewMode.valueOf(value) }.getOrNull() }
        ?: default

private fun JSONObject.optDeviceClass(default: HomeLayoutDeviceClass): HomeLayoutDeviceClass =
    optString("deviceClass", "")
        .takeIf(String::isNotBlank)
        ?.let { value -> runCatching { HomeLayoutDeviceClass.valueOf(value) }.getOrNull() }
        ?: default

internal val JSONObject.isHomeLayoutSetJson: Boolean
    get() = optString("type") == HOME_LAYOUT_SET_TYPE

private const val HOME_LAYOUT_SET_TYPE = "homeLayoutSet"
