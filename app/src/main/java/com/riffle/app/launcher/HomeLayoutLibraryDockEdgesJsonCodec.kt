package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.dockPositionFromStoredName
import com.riffle.core.domain.launcher.home.withLibraryDockEdgesMigrated
import org.json.JSONArray
import org.json.JSONObject

/*
 * The "libraryDockEdges" entry of a stored home layout set: the edge each device class's shared dock
 * takes in Library, as `[{"deviceClass": ..., "edge": ...}]`. Home's edge stays in the shared dock's
 * own "position". A set without the entry was written when one edge served every mode, and is
 * migrated on decode (see withLibraryDockEdgesMigrated).
 */

internal fun encodeLibraryDockEdges(edges: Map<HomeLayoutDeviceClass, DockPosition>): JSONArray =
    JSONArray(
        edges.map { (deviceClass, edge) ->
            JSONObject()
                .put("deviceClass", deviceClass.name)
                .put("edge", edge.name)
        },
    )

/**
 * [layoutSet] with the Library dock edges this JSON stores restored, or migrated from the single
 * shared edge when it stores none. An entry that does not name a known device class and edge is
 * dropped; its device class then uses the default Library edge.
 */
internal fun JSONObject.restoreLibraryDockEdges(layoutSet: HomeLayoutSet): HomeLayoutSet =
    optJSONArray(LIBRARY_DOCK_EDGES_KEY)
        ?.let { entries ->
            layoutSet.copy(
                libraryDockEdgesByDeviceClass =
                    (0 until entries.length())
                        .mapNotNull { index -> entries.optJSONObject(index)?.toLibraryDockEdgeEntry() }
                        .toMap(),
            )
        }
        ?: layoutSet.withLibraryDockEdgesMigrated()

private fun JSONObject.toLibraryDockEdgeEntry(): Pair<HomeLayoutDeviceClass, DockPosition>? {
    val deviceClass = HomeLayoutDeviceClass.entries.firstOrNull { entry -> entry.name == optString("deviceClass") }
    val edge = dockPositionFromStoredName(optString("edge"))
    return if (deviceClass != null && edge != null) deviceClass to edge else null
}

internal const val LIBRARY_DOCK_EDGES_KEY = "libraryDockEdges"
