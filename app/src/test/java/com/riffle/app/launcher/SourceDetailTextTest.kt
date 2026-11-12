package com.riffle.app.launcher

import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.settings.SourceRow
import com.riffle.core.domain.launcher.workspace.settings.SourceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The detail-page table, Back targets and the Sources row wording. All pure. */
class SourceDetailTextTest {
    @Test
    fun everyBuiltInSourceButSearchHasItsOwnDetailPageAndTheTableRoundTrips() {
        // Search only served per-lens queries, which are gone: it has no page and no row.
        val shown = SourceIds.BUILT_IN.filterNot { it == SourceIds.SEARCH }
        shown.forEach { id ->
            val page = SourceDetailPages.pageFor(id)
            assertTrue(id.value, page != null)
            assertEquals(id, SourceDetailPages.sourceFor(checkNotNull(page)))
        }
        assertEquals(shown.size, SourceDetailPages.pages.size)
        assertNull(SourceDetailPages.pageFor(SourceIds.SEARCH))
        assertNull(SourceDetailPages.pageFor(SourceId("custom")))
        assertNull(SourceDetailPages.sourceFor(SettingsPage.SOURCES))
    }

    @Test
    fun backFromTheSourcePagesReturnsToSourcesAndOtherPagesAreUnchanged() {
        SourceDetailPages.pages.forEach { assertEquals(SettingsPage.SOURCES, settingsBackTarget(it)) }
        assertEquals(SettingsPage.SOURCES, settingsBackTarget(SettingsPage.EXCLUSIONS))
        assertEquals(SettingsPage.SOURCES, settingsBackTarget(SettingsPage.ICS_FEEDS))
        assertEquals(SettingsPage.MAIN, settingsBackTarget(SettingsPage.SOURCES))
        assertEquals(SettingsPage.MAIN, settingsBackTarget(SettingsPage.RSS))
        assertEquals(SettingsPage.MAIN, settingsBackTarget(SettingsPage.LAYOUT))
    }

    @Test
    fun theRowSpokenSummaryNamesStatusAndWhatADoubleTapDoes() {
        val row = SourceRow(SourceIds.ALL_APPS, "Apps", "Every app.", SourceStatus.READY, true)

        assertEquals("Apps, Ready. Every app. Double tap for details", SourceDetailText.rowSpoken(row))
    }

    @Test
    fun onlyTheSourcesWithAnExtraStoryHaveANote() {
        assertTrue(SourceDetailText.note(SourceIds.CALENDAR).orEmpty().isNotBlank())
        assertNull(SourceDetailText.note(SourceIds.ALL_APPS))
    }

    @Test
    fun theContentLevelNotePointsAtThePrivacyPage() {
        assertTrue(SourceDetailText.CONTENT_LEVEL_NOTE.contains("Settings > Privacy"))
    }
}
