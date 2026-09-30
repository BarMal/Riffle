package com.riffle.app.launcher.calendar

import android.content.Intent
import com.riffle.core.domain.launcher.workspace.sources.CalendarAccessStatus
import com.riffle.core.domain.launcher.workspace.sources.CalendarAccessStep
import com.riffle.core.domain.launcher.workspace.sources.SourceAccess
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarAccessTest {
    private class FakeGateway(var status: CalendarAccessStatus) : CalendarAccessGateway {
        override fun status(): CalendarAccessStatus = status

        override fun isGranted(): Boolean = status == CalendarAccessStatus.GRANTED

        override fun recordRequestResult(granted: Boolean) = Unit

        override fun createAppSettingsIntent(): Intent = error("not used in JVM tests")
    }

    private var dialogs = 0
    private var settingsOpens = 0

    private fun coordinator(gateway: CalendarAccessGateway) =
        CalendarAccessCoordinator(
            gateway = gateway,
            launchPermissionRequest = { dialogs++ },
            openAppSettings = { settingsOpens++ },
        )

    @Test
    fun anExplicitRequestLaunchesTheSystemDialogWhenItCanStillAppear() {
        val step = coordinator(FakeGateway(CalendarAccessStatus.NOT_GRANTED)).request()

        assertEquals(CalendarAccessStep.REQUEST_PERMISSION, step)
        assertEquals(1, dialogs)
        assertEquals(0, settingsOpens)
    }

    @Test
    fun aPermanentDenialOpensAppSettingsInsteadOfLoopingTheDialog() {
        val step = coordinator(FakeGateway(CalendarAccessStatus.DENIED_PERMANENTLY)).request()

        assertEquals(CalendarAccessStep.OPEN_APP_SETTINGS, step)
        assertEquals(0, dialogs)
        assertEquals(1, settingsOpens)
    }

    @Test
    fun grantedLaunchesNothing() {
        coordinator(FakeGateway(CalendarAccessStatus.GRANTED)).request()

        assertEquals(0, dialogs)
        assertEquals(0, settingsOpens)
    }

    @Test
    fun aCallerWithoutVisibleRationaleIsToldToShowItAndNothingLaunches() {
        val step = coordinator(FakeGateway(CalendarAccessStatus.NOT_GRANTED)).request(rationaleVisible = false)

        assertEquals(CalendarAccessStep.SHOW_RATIONALE, step)
        assertEquals(0, dialogs)
        assertEquals(0, settingsOpens)
    }

    @Test
    fun sourceAccessIsGrantedOnlyWhenTheGatewayConfirmsIt() {
        assertEquals(SourceAccess.GRANTED, FakeGateway(CalendarAccessStatus.GRANTED).sourceAccess())
        assertEquals(SourceAccess.REQUIRED, FakeGateway(CalendarAccessStatus.UNKNOWN).sourceAccess())
        assertEquals(SourceAccess.REQUIRED, FakeGateway(CalendarAccessStatus.DENIED_PERMANENTLY).sourceAccess())
    }

    @Test
    fun accessChangesNotifyOnlyWhenTheGrantFlips() {
        val changes = CalendarAccessChanges()
        var notified = 0
        val stop = changes.observe { notified++ }

        changes.onStatus(CalendarAccessStatus.NOT_GRANTED)
        changes.onStatus(CalendarAccessStatus.DENIED_PERMANENTLY)
        assertEquals(0, notified)

        changes.onStatus(CalendarAccessStatus.GRANTED)
        changes.onStatus(CalendarAccessStatus.GRANTED)
        assertEquals(1, notified)

        changes.onStatus(CalendarAccessStatus.NOT_GRANTED)
        assertEquals(2, notified)

        stop()
        changes.onStatus(CalendarAccessStatus.GRANTED)
        assertEquals(2, notified)
    }
}
