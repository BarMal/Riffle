package com.riffle.app.launcher

import com.riffle.app.launcher.workspace.SourceAccessRoute
import com.riffle.app.launcher.workspace.sourceAccessRouteFor
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.settings.SourceStatus
import com.riffle.core.domain.launcher.workspace.sources.CalendarAccessStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Sources page's permission affordances only ever name the existing explicit flows. */
class SourcesSettingsTextTest {
    private val denied = CalendarAccessStatus.NOT_GRANTED

    @Test
    fun eachGatedSourceRoutesToItsExistingExplicitFlow() {
        assertEquals(SourceAccessRoute.CALENDAR, sourceAccessRouteFor(SourceIds.CALENDAR))
        assertEquals(SourceAccessRoute.NOTIFICATION_ACCESS, sourceAccessRouteFor(SourceIds.NOTIFICATIONS))
        assertEquals(SourceAccessRoute.NOTIFICATION_ACCESS, sourceAccessRouteFor(SourceIds.MEDIA))
        assertEquals(SourceAccessRoute.USAGE_ACCESS, sourceAccessRouteFor(SourceIds.RECENT_APPS))
        listOf(SourceIds.ALL_APPS, SourceIds.QUICK_ACTIONS, SourceIds.RSS, SourceIds.SEARCH).forEach {
            assertEquals(SourceAccessRoute.NONE, sourceAccessRouteFor(it))
        }
    }

    @Test
    fun ungatedSourcesOfferNoAllowButton() {
        assertNull(SourcesSettingsText.allowLabel(SourceAccessRoute.NONE, denied))
        assertNull(SourcesSettingsText.rationale(SourceAccessRoute.NONE, denied))
    }

    @Test
    fun everyGatedRouteHasALabelAndARationaleShownBeforeTheButtonIsUsed() {
        listOf(
            SourceAccessRoute.CALENDAR,
            SourceAccessRoute.NOTIFICATION_ACCESS,
            SourceAccessRoute.USAGE_ACCESS,
        ).forEach { route ->
            assertNotNull(route.name, SourcesSettingsText.allowLabel(route, denied))
            assertTrue(route.name, SourcesSettingsText.rationale(route, denied).orEmpty().isNotBlank())
        }
    }

    @Test
    fun calendarReusesTheExistingPermissionsWording() {
        assertEquals(
            CalendarAccessStatus.NOT_GRANTED.calendarAccessActionLabel(),
            SourcesSettingsText.allowLabel(SourceAccessRoute.CALENDAR, CalendarAccessStatus.NOT_GRANTED),
        )
        assertEquals(
            "Open app settings",
            SourcesSettingsText.allowLabel(SourceAccessRoute.CALENDAR, CalendarAccessStatus.DENIED_PERMANENTLY),
        )
        assertEquals(
            CalendarAccessStatus.NOT_GRANTED.calendarAccessSettingsLabel(),
            SourcesSettingsText.rationale(SourceAccessRoute.CALENDAR, CalendarAccessStatus.NOT_GRANTED),
        )
    }

    @Test
    fun statusIsAlwaysSpokenAsWords() {
        assertEquals(
            "Calendar, Needs permission",
            SourcesSettingsText.statusDescription("Calendar", SourceStatus.NEEDS_PERMISSION),
        )
        assertEquals("RSS feeds, Off", SourcesSettingsText.statusDescription("RSS feeds", SourceStatus.OFF))
    }

    @Test
    fun theExclusionRulesEntryPointIsAClearlyMarkedPlaceholder() {
        assertEquals("Hidden items and rules (coming soon)", SourcesSettingsText.HIDDEN_ITEMS_SOON)
    }

    @Test
    fun twoColumnsSplitRowsEvenlyAndOneColumnKeepsThemTogether() {
        assertEquals(listOf(listOf(1, 2, 3, 4)), (1..4).toList().chunkedBy(1))
        assertEquals(listOf(listOf(1, 2, 3), listOf(4, 5)), (1..5).toList().chunkedBy(2))
        assertEquals(listOf(listOf(1)), listOf(1).chunkedBy(2))
        assertEquals(listOf(emptyList<Int>()), emptyList<Int>().chunkedBy(2))
        assertEquals(SourceId("x"), SourceId("x"))
    }
}
