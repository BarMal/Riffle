package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.ModeSurface
import com.riffle.core.domain.launcher.home.dockEdgeFor
import com.riffle.core.domain.launcher.home.withDockEdge
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dock edge is per surface (Home, Library) while the dock itself is shared: Library's edge is
 * stored as "libraryDockEdges", and a set written when one edge served every mode gives Library that
 * same edge on decode.
 */
class HomeLayoutSetLibraryDockEdgesJsonCodecTest {
    private val phone = HomeLayoutDeviceClass.PHONE
    private val tablet = HomeLayoutDeviceClass.TABLET

    @Test
    fun roundTripsEachSurfacesEdge() {
        val layoutSet =
            HomeLayoutSet.standard()
                .withDockEdge(phone, ModeSurface.HOME, DockPosition.LEFT)
                .withDockEdge(phone, ModeSurface.LIBRARY, DockPosition.BOTTOM)
                .withDockEdge(tablet, ModeSurface.LIBRARY, DockPosition.RIGHT)

        val encoded = encodeHomeLayoutSet(layoutSet)
        val decoded = decodeHomeLayoutSet(encoded)

        assertTrue(JSONObject(encoded).has("libraryDockEdges"))
        assertEquals(layoutSet.libraryDockEdgesByDeviceClass, decoded.libraryDockEdgesByDeviceClass)
        assertEquals(DockPosition.LEFT, decoded.dockEdgeFor(phone, ModeSurface.HOME))
        assertEquals(DockPosition.BOTTOM, decoded.dockEdgeFor(phone, ModeSurface.LIBRARY))
        assertEquals(DockPosition.RIGHT, decoded.dockEdgeFor(tablet, ModeSurface.LIBRARY))
    }

    @Test
    fun aSetWithNoLibraryEdgesRoundTripsUnchanged() {
        val layoutSet = HomeLayoutSet.standard().withDockEdge(phone, ModeSurface.HOME, DockPosition.RIGHT)

        val decoded = decodeHomeLayoutSet(encodeHomeLayoutSet(layoutSet))

        // Stored as an empty entry, so decoding does not mistake it for a set from before per-surface
        // edges and copy Home's edge across.
        assertEquals(emptyMap<HomeLayoutDeviceClass, DockPosition>(), decoded.libraryDockEdgesByDeviceClass)
        assertEquals(DockPosition.BOTTOM, decoded.dockEdgeFor(phone, ModeSurface.LIBRARY))
    }

    @Test
    fun aSetFromBeforePerSurfaceEdgesGivesLibraryTheChosenEdge() {
        val legacy =
            JSONObject(
                encodeHomeLayoutSet(HomeLayoutSet.standard().withDockEdge(phone, ModeSurface.HOME, DockPosition.RIGHT)),
            ).apply { remove("libraryDockEdges") }

        val decoded = decodeHomeLayoutSet(legacy.toString())

        assertEquals(DockPosition.RIGHT, decoded.dockEdgeFor(phone, ModeSurface.HOME))
        assertEquals(DockPosition.RIGHT, decoded.dockEdgeFor(phone, ModeSurface.LIBRARY))
        assertEquals(mapOf(phone to DockPosition.RIGHT), decoded.libraryDockEdgesByDeviceClass)
    }

    @Test
    fun aSetFromBeforePerSurfaceEdgesWithATemplateDockPutsLibraryAtTheBottom() {
        val legacy = JSONObject(encodeHomeLayoutSet(HomeLayoutSet.standard())).apply { remove("libraryDockEdges") }

        val decoded = decodeHomeLayoutSet(legacy.toString())

        assertEquals(emptyMap<HomeLayoutDeviceClass, DockPosition>(), decoded.libraryDockEdgesByDeviceClass)
        assertEquals(DockPosition.BOTTOM, decoded.dockEdgeFor(phone, ModeSurface.LIBRARY))
    }

    @Test
    fun aMigratedSetSavesAndReloadsUnchanged() {
        val legacy =
            JSONObject(
                encodeHomeLayoutSet(HomeLayoutSet.standard().withDockEdge(phone, ModeSurface.HOME, DockPosition.LEFT)),
            ).apply { remove("libraryDockEdges") }
        val migrated = decodeHomeLayoutSet(legacy.toString())

        val reloaded = decodeHomeLayoutSet(encodeHomeLayoutSet(migrated))

        assertEquals(migrated.libraryDockEdgesByDeviceClass, reloaded.libraryDockEdgesByDeviceClass)
        // Moving Home afterwards no longer drags Library with it.
        val moved = reloaded.withDockEdge(phone, ModeSurface.HOME, DockPosition.BOTTOM)
        val movedAndReloaded = decodeHomeLayoutSet(encodeHomeLayoutSet(moved))
        assertEquals(DockPosition.BOTTOM, movedAndReloaded.dockEdgeFor(phone, ModeSurface.HOME))
        assertEquals(DockPosition.LEFT, movedAndReloaded.dockEdgeFor(phone, ModeSurface.LIBRARY))
    }

    @Test
    fun malformedEntriesAreDroppedToTheDefault() {
        val json =
            JSONObject(encodeHomeLayoutSet(HomeLayoutSet.standard())).put(
                "libraryDockEdges",
                JSONArray()
                    .put(JSONObject().put("deviceClass", "WATCH").put("edge", "LEFT"))
                    .put(JSONObject().put("deviceClass", phone.name).put("edge", "DIAGONAL"))
                    .put(JSONObject().put("deviceClass", tablet.name).put("edge", "TRAILING"))
                    .put("not an object"),
            )

        val decoded = decodeHomeLayoutSet(json.toString())

        // The legacy direction-relative name still maps to its absolute edge.
        assertEquals(mapOf(tablet to DockPosition.RIGHT), decoded.libraryDockEdgesByDeviceClass)
        assertEquals(DockPosition.BOTTOM, decoded.dockEdgeFor(phone, ModeSurface.LIBRARY))
    }

    @Test
    fun aLegacySingleLayoutGivesLibraryItsChosenEdge() {
        val layout = HomeLayoutSet.standard().withDockEdge(phone, ModeSurface.HOME, DockPosition.RIGHT).activeLayout

        val decoded = decodeHomeLayoutSet(encodeHomeLayout(layout))

        assertEquals(DockPosition.RIGHT, decoded.dockEdgeFor(phone, ModeSurface.LIBRARY))
    }
}
