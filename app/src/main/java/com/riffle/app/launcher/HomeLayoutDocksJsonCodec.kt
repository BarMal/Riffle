package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.dockFor
import org.json.JSONArray
import org.json.JSONObject

/*
 * The "docks" entry of a stored home layout set: one dock per device class, shared by every mode
 * (#1205). A set without it was written when each mode kept its own dock, and is migrated on decode.
 */

internal fun encodeDocks(layoutSet: HomeLayoutSet): JSONArray =
    JSONArray(
        layoutSet.layouts.keys
            .map { key -> key.deviceClass }
            .distinct()
            .map { deviceClass ->
                JSONObject()
                    .put("deviceClass", deviceClass.name)
                    .put("dock", encodeDock(layoutSet.dockFor(deviceClass)))
            },
    )

internal fun JSONArray.toDocks(): Map<HomeLayoutDeviceClass, DockModel> =
    (0 until length())
        .mapNotNull { index ->
            optJSONObject(index)?.let { entry ->
                runCatching { entry.toDockEntry() }.getOrNull()
            }
        }.toMap()

private fun JSONObject.toDockEntry(): Pair<HomeLayoutDeviceClass, DockModel> {
    val deviceClass = HomeLayoutDeviceClass.valueOf(getString("deviceClass"))
    val defaults = HomeLayoutDefaults.standard(deviceClass)
    return deviceClass to
        getJSONObject("dock").toDock(defaults = defaults.dock, defaultGrid = defaults.settings.grid.dimensions)
}
