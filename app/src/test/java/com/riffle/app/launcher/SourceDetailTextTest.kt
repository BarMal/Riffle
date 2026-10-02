package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.settings.SourcePlace
import com.riffle.core.domain.launcher.workspace.settings.SourcePlaceKind
import com.riffle.core.domain.launcher.workspace.settings.SourceRow
import com.riffle.core.domain.launcher.workspace.settings.SourceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The detail-page table, Back targets and the Used by wording. All pure. */
class SourceDetailTextTest {
    private fun place(
        kind: SourcePlaceKind = SourcePlaceKind.PAGE,
        page: Int? = 2,
        widget: Int? = null,
        saved: String? = null,
    ) = SourcePlace(
        layout = HomeLayoutDeviceClass.PHONE,
        workspaceId = WorkspaceId("w"),
        workspaceName = "Nova",
        containerId = ContainerId("c"),
        kind = kind,
        pageNumber = page,
        widgetNumber = widget,
        savedLensName = saved,
    )

    @Test
    fun everyBuiltInSourceHasItsOwnDetailPageAndTheTableRoundTrips() {
        SourceIds.BUILT_IN.forEach { id ->
            val page = SourceDetailPages.pageFor(id)
            assertTrue(id.value, page != null)
            assertEquals(id, SourceDetailPages.sourceFor(checkNotNull(page)))
        }
        assertEquals(SourceIds.BUILT_IN.size, SourceDetailPages.pages.size)
        assertNull(SourceDetailPages.pageFor(SourceId("custom")))
        assertNull(SourceDetailPages.sourceFor(SettingsPage.SOURCES))
    }

    @Test
    fun backFromTheSourcePagesReturnsToSourcesAndOtherPagesAreUnchanged() {
        SourceDetailPages.pages.forEach { assertEquals(SettingsPage.SOURCES, settingsBackTarget(it)) }
        assertEquals(SettingsPage.SOURCES, settingsBackTarget(SettingsPage.EXCLUSIONS))
        assertEquals(SettingsPage.SOURCES, settingsBackTarget(SettingsPage.ICS_FEEDS))
        assertEquals(SettingsPage.MAIN, settingsBackTarget(SettingsPage.SOURCES))
        assertEquals(SettingsPage.MAIN, settingsBackTarget(SettingsPage.WORKSPACES))
        assertEquals(SettingsPage.MAIN, settingsBackTarget(SettingsPage.RSS))
        assertEquals(SettingsPage.MAIN, settingsBackTarget(SettingsPage.LAYOUT))
    }

    @Test
    fun usedByCountsInWords() {
        assertEquals("Not used by any page yet", SourceDetailText.usedBy(0))
        assertEquals("Used by 1 place", SourceDetailText.usedBy(1))
        assertEquals("Used by 4 places", SourceDetailText.usedBy(4))
        assertEquals("Used by", SourceDetailText.usedByHeading(0))
        assertEquals("Used by (3)", SourceDetailText.usedByHeading(3))
    }

    @Test
    fun placesReadAsWorkspaceThenContainerAndSpokenWithCommas() {
        assertEquals("Nova > Page 2", SourceDetailText.placeLabel(place(), null))
        assertEquals(
            "Phone (folded) > Nova > Page 2 > Widget 3",
            SourceDetailText.placeLabel(place(SourcePlaceKind.WIDGET, widget = 3), "Phone (folded)"),
        )
        assertEquals("Nova > Dock section", SourceDetailText.placeLabel(place(SourcePlaceKind.DOCK, null), null))
        assertEquals("Nova > Page set (page 2)", SourceDetailText.placeLabel(place(SourcePlaceKind.PAGE_SET), null))
        assertEquals("Nova, Page 2, saved lens Mail", SourceDetailText.placeSpoken(place(saved = "Mail"), null))
        assertFalse(SourceDetailText.placeSpoken(place(), null).contains(">"))
    }

    @Test
    fun theRowSpokenSummaryNamesStatusUsageAndWhatADoubleTapDoes() {
        val row = SourceRow(SourceIds.ALL_APPS, "Apps", "Every app.", SourceStatus.READY, true)

        assertEquals(
            "Apps, Ready. Used by 2 places. Every app. Double tap for details",
            SourceDetailText.rowSpoken(row, 2),
        )
        assertFalse(SourceDetailText.rowSpoken(row, null).contains("Used by"))
    }

    @Test
    fun onlyTheSourcesWithAnExtraStoryHaveANote() {
        assertTrue(SourceDetailText.note(SourceIds.CALENDAR).orEmpty().isNotBlank())
        assertNull(SourceDetailText.note(SourceIds.ALL_APPS))
    }

    @Test
    fun theNotificationContentLevelIsOnlyAComingLaterNote() {
        assertTrue(SourceDetailText.CONTENT_LEVEL_LATER.startsWith("Coming later"))
    }
}
