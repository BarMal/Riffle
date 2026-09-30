package com.riffle.core.domain.launcher.workspace.sources

import com.riffle.core.domain.launcher.workspace.SourceState
import kotlin.test.Test
import kotlin.test.assertEquals

class CalendarAccessTest {
    @Test
    fun `granted wins over every other fact`() {
        assertEquals(CalendarAccessStatus.GRANTED, calendarAccessStatus(true, false, false))
        assertEquals(CalendarAccessStatus.GRANTED, calendarAccessStatus(true, true, true))
    }

    @Test
    fun `never asked is not granted and can still be requested`() {
        assertEquals(CalendarAccessStatus.NOT_GRANTED, calendarAccessStatus(false, false, false))
    }

    @Test
    fun `denied once with system rationale can be requested again`() {
        assertEquals(CalendarAccessStatus.NOT_GRANTED, calendarAccessStatus(false, true, true))
    }

    @Test
    fun `denied without system rationale after our request is permanent`() {
        assertEquals(CalendarAccessStatus.DENIED_PERMANENTLY, calendarAccessStatus(false, false, true))
    }

    @Test
    fun `only a confirmed grant maps to granted source access`() {
        assertEquals(SourceAccess.GRANTED, CalendarAccessStatus.GRANTED.toSourceAccess())
        listOf(
            CalendarAccessStatus.UNKNOWN,
            CalendarAccessStatus.NOT_GRANTED,
            CalendarAccessStatus.DENIED_PERMANENTLY,
        ).forEach { status -> assertEquals(SourceAccess.REQUIRED, status.toSourceAccess(), status.name) }
    }

    @Test
    fun `gated states never read calendar data`() {
        CalendarAccessStatus.entries.filter { it != CalendarAccessStatus.GRANTED }.forEach { status ->
            val state = sourceStateFor(status.toSourceAccess()) { error("calendar must not be read") }
            assertEquals(SourceState.PermissionRequired, state, status.name)
        }
    }

    @Test
    fun `granted needs nothing`() {
        assertEquals(CalendarAccessStep.NONE, calendarAccessStep(CalendarAccessStatus.GRANTED, false))
        assertEquals(CalendarAccessStep.NONE, calendarAccessStep(CalendarAccessStatus.GRANTED, true))
    }

    @Test
    fun `rationale comes before the system dialog unless already visible`() {
        listOf(CalendarAccessStatus.NOT_GRANTED, CalendarAccessStatus.UNKNOWN).forEach { status ->
            assertEquals(CalendarAccessStep.SHOW_RATIONALE, calendarAccessStep(status, false), status.name)
            assertEquals(CalendarAccessStep.REQUEST_PERMISSION, calendarAccessStep(status, true), status.name)
        }
    }

    @Test
    fun `permanent denial routes to app settings and never to the dialog`() {
        assertEquals(
            CalendarAccessStep.OPEN_APP_SETTINGS,
            calendarAccessStep(CalendarAccessStatus.DENIED_PERMANENTLY, false),
        )
        assertEquals(
            CalendarAccessStep.OPEN_APP_SETTINGS,
            calendarAccessStep(CalendarAccessStatus.DENIED_PERMANENTLY, true),
        )
    }
}
