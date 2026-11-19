package com.riffle.core.domain.launcher.home.saved

import com.riffle.core.domain.launcher.home.GeneratedLauncherPageKind
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.workspace.pool.PoolApp
import com.riffle.core.domain.launcher.workspace.pool.PoolFolder
import com.riffle.core.domain.launcher.workspace.pool.PoolWidget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SavedHomeCaptureTest {
    private fun capture(vararg pages: LauncherPage) =
        SavedHomeCapture.capture(
            pages.toList(),
            providers(10 to "calendar", 11 to "weather"),
            "id",
            "Name",
            SavedHomeLayoutKind.SAVED,
            1L,
        )

    @Test
    fun itHoldsPagesAppsFoldersWidgetsAndStacksAndNothingElse() {
        val generated =
            LauncherPage(LauncherPageId("gen"), LauncherPageType.Generated(GeneratedLauncherPageKind.TODAY), GRID)
        val layout =
            capture(
                page("a", app("mail", 0, 0), folder("f", 1, 0, "maps", "music")),
                generated,
                page("b", widget("cal", 10, 0, 0), stack("s", 0, 1, "calendar" to 10, "weather" to 11)),
            )
        assertEquals(2, layout.pageCount)
        assertEquals(4, layout.itemCount)
        assertTrue(layout.items.values.any { it is PoolApp })
        assertEquals(2, (layout.items.values.filterIsInstance<PoolFolder>().single()).entries.size)
    }

    @Test
    fun itNeverHoldsAHostIdAndNamesTheProviderInstead() {
        val layout = capture(page("a", widget("cal", 10, 0, 0), stack("s", 0, 1, "calendar" to 10, "weather" to 11)))
        val widgets = layout.items.values.filterIsInstance<PoolWidget>()
        assertTrue(widgets.all { it.hostIds.isEmpty() })
        assertEquals(provider("calendar"), widgets.first { it.stack.isEmpty() }.provider)
        val stacked = widgets.first { it.stack.isNotEmpty() }
        assertEquals(listOf(provider("calendar"), provider("weather")), stacked.stack.map { it.provider })
        assertEquals(0, stacked.shown)
    }

    @Test
    fun anUnknownProviderIsKeptAsNull() {
        val layout = capture(page("a", widget("x", 77, 0, 0)))
        assertNull(layout.items.values.filterIsInstance<PoolWidget>().single().provider)
    }

    @Test
    fun anUnboundWidgetIsDescribedWithoutAProvider() {
        val layout = capture(page("a", widget("x", 0, 0, 0)))
        assertNull(layout.items.values.filterIsInstance<PoolWidget>().single().provider)
        assertEquals(HostedWidgetId(0), HostedWidgetId(0))
    }

    @Test
    fun itSkipsItemsWithoutACellAndRepeatedIds() {
        val unplaced = app("mail", 0, 0).copy(placement = null)
        val layout = capture(page("a", unplaced, app("maps", 0, 0), app("maps", 1, 0)))
        assertEquals(1, layout.itemCount)
    }
}
