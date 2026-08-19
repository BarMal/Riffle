package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockExpandAffordance
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Persistence for the dock's expansion settings, which live on the dock and are therefore already
 * per home layout.
 */
class HomeLayoutJsonCodecDockExpansionTest {
    @Test
    fun roundTripsDockExpandability() {
        val layout =
            HomeLayoutDefaults.standard().copy(
                dock = HomeLayoutDefaults.standard().dock.copy(isExpandable = false),
            )

        val decodedLayout = decodeHomeLayout(encodeHomeLayout(layout))

        assertEquals(false, decodedLayout.dock.isExpandable)
    }

    @Test
    fun roundTripsDockExpandAffordance() {
        val layout =
            HomeLayoutDefaults.standard().copy(
                dock = HomeLayoutDefaults.standard().dock.copy(expandAffordance = DockExpandAffordance.BUTTON),
            )

        val decodedLayout = decodeHomeLayout(encodeHomeLayout(layout))

        assertEquals(DockExpandAffordance.BUTTON, decodedLayout.dock.expandAffordance)
    }

    @Test
    fun roundTripsTheDockPanelAndWhatIsOnIt() {
        val clock =
            AppShortcutItem(
                id = LauncherItemId("clock"),
                appIdentity =
                    AppIdentity(
                        packageName = AppPackageName("com.riffle.clock"),
                        activityName = AppActivityName(".MainActivity"),
                    ),
                label = "Clock",
                placement = GridPlacement(cell = GridCell(column = 1, row = 0)),
            )
        val layout =
            HomeLayoutDefaults.standard().let { standard ->
                standard.copy(
                    dock =
                        standard.dock.copy(
                            panel =
                                LauncherPage(
                                    id = LauncherPageId("dock-panel"),
                                    grid = GridDimensions(columns = 4, rows = 2),
                                    items = listOf(clock),
                                ),
                        ),
                )
            }

        val decodedPanel = decodeHomeLayout(encodeHomeLayout(layout)).dock.panel

        assertEquals(GridDimensions(columns = 4, rows = 2), decodedPanel?.grid)
        assertEquals(listOf(clock.id), decodedPanel?.items?.map { item -> item.id })
        assertEquals(GridCell(column = 1, row = 0), decodedPanel?.items?.single()?.placement?.cell)
    }

    @Test
    fun aLayoutWithNoPanelDecodesWithoutOne() {
        assertEquals(null, decodeHomeLayout(encodeHomeLayout(HomeLayoutDefaults.standard())).dock.panel)
    }

    @Test
    fun aLayoutWrittenBeforeTheseSettingsExistedStillExpandsBySwipe() {
        // Absent keys must not silently turn expansion off or move it to a button on upgrade.
        val encoded = JSONObject(encodeHomeLayout(HomeLayoutDefaults.standard()))
        encoded.getJSONObject("dock").remove("isExpandable")
        encoded.getJSONObject("dock").remove("expandAffordance")

        val decodedLayout = decodeHomeLayout(encoded.toString())

        assertEquals(true, decodedLayout.dock.isExpandable)
        assertEquals(DockExpandAffordance.GESTURE, decodedLayout.dock.expandAffordance)
    }
}
