package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherViewMode
import org.json.JSONArray
import org.json.JSONObject

fun encodeHomeLayoutSet(layoutSet: HomeLayoutSet): String =
    JSONObject()
        .put("type", HOME_LAYOUT_SET_TYPE)
        .put("active", encodeLayoutKey(layoutSet.activeKey))
        .put("preferredModes", encodePreferredModes(layoutSet.preferredModesByDeviceClass))
        .put(
            "layouts",
            JSONArray(
                layoutSet.layouts.map { (key, layout) ->
                    JSONObject()
                        .put("key", encodeLayoutKey(key))
                        .put("layout", encodeHomeLayoutObject(layout.copy(viewMode = key.viewMode)))
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
    val preferredModes = optPreferredModes()

    return HomeLayoutSet(
        activeKey = activeKey,
        layouts = layouts.toMap(),
        preferredModesByDeviceClass =
            preferredModes.ifEmpty { mapOf(activeKey.deviceClass to activeKey.viewMode) },
    ).let { layoutSet ->
        layoutSet.takeIf { set -> set.activeKey in set.layouts }
            ?: layoutSet.copy(layouts = layoutSet.layouts + (activeKey to layoutSet.layoutFor(activeKey)))
    }
}

private fun JSONArray.toHomeLayoutEntries(): List<Pair<HomeLayoutKey, HomeLayout>> =
    (0 until length())
        .map { index -> getJSONObject(index) }
        .map { entry -> entry.toHomeLayoutEntry() }

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
