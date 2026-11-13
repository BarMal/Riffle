package com.riffle.app.launcher

import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class DockSettingTest {
    @Test
    fun liveAppSlotsSubtitleExplainsNoLiveApps() {
        assertEquals(
            "The dock's second side shows only now playing",
            dockLiveAppSlotsSubtitle(slots = 0, notificationAccessStatus = NotificationAccessStatus.REVOKED),
        )
    }

    @Test
    fun liveAppSlotsSubtitleUsesGrantedCopy() {
        assertEquals(
            "Apps with something new sit beside your pinned ones and open the Stream",
            dockLiveAppSlotsSubtitle(slots = 3, notificationAccessStatus = NotificationAccessStatus.GRANTED),
        )
    }

    @Test
    fun liveAppSlotsSubtitleExplainsNotGrantedAccess() {
        assertEquals(
            "Live apps are on, but notification access is not allowed",
            dockLiveAppSlotsSubtitle(slots = 2, notificationAccessStatus = NotificationAccessStatus.NOT_GRANTED),
        )
    }

    @Test
    fun liveAppSlotsSubtitleExplainsRevokedAccess() {
        assertEquals(
            "Live apps are on, but notification access was revoked",
            dockLiveAppSlotsSubtitle(slots = 2, notificationAccessStatus = NotificationAccessStatus.REVOKED),
        )
    }

    @Test
    fun liveAppSlotsSubtitleExplainsUnknownAccess() {
        assertEquals(
            "Live apps are on, but notification access has not been checked",
            dockLiveAppSlotsSubtitle(slots = 2, notificationAccessStatus = NotificationAccessStatus.UNKNOWN),
        )
    }

    @Test
    fun turningLiveAppsOnWithoutAccessAlsoAsksForNotificationAccess() {
        assertEquals(
            listOf(LauncherShellAction.SelectDockLiveAppSlots(3), LauncherShellAction.RequestNotificationAccess),
            dockLiveAppSlotsActions(3, wasSlots = 0, notificationAccessStatus = NotificationAccessStatus.NOT_GRANTED),
        )
        assertEquals(
            listOf<LauncherShellAction>(LauncherShellAction.SelectDockLiveAppSlots(3)),
            dockLiveAppSlotsActions(3, wasSlots = 0, notificationAccessStatus = NotificationAccessStatus.GRANTED),
        )
        assertEquals(
            listOf<LauncherShellAction>(LauncherShellAction.SelectDockLiveAppSlots(4)),
            dockLiveAppSlotsActions(4, wasSlots = 3, notificationAccessStatus = NotificationAccessStatus.NOT_GRANTED),
        )
        assertEquals(
            listOf<LauncherShellAction>(LauncherShellAction.SelectDockLiveAppSlots(0)),
            dockLiveAppSlotsActions(0, wasSlots = 3, notificationAccessStatus = NotificationAccessStatus.NOT_GRANTED),
        )
    }

    @Test
    fun liveAppSlotsReadAsNoneOneAppOrCountedApps() {
        assertEquals("None", dockLiveAppSlotsLabel(0))
        assertEquals("1 app", dockLiveAppSlotsLabel(1))
        assertEquals("4 apps", dockLiveAppSlotsLabel(4))
    }

    @Test
    fun dockSizeSummaryNamesThePinnedIconsAndTheDynamicSide() {
        assertEquals(
            "Shows up to 7 pinned icons, then now playing",
            dockSizeSummaryText(capacity = 7, liveAppSlots = 0),
        )
        assertEquals(
            "Shows up to 7 pinned icons, then 1 live app and now playing",
            dockSizeSummaryText(capacity = 7, liveAppSlots = 1),
        )
        assertEquals(
            "Shows up to 7 pinned icons, then 3 live apps and now playing",
            dockSizeSummaryText(capacity = 7, liveAppSlots = 3),
        )
    }
}
