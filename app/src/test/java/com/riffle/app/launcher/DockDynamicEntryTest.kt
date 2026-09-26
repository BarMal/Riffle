package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationAgeBucket
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the dock's dynamic side shows in grid mode: the de-duplicated notification list, where a tap
 * opens the app. Cards builds its own entries (the stage selector) -- see CardsDockInterpreterTest.
 */
class DockDynamicEntryTest {
    @Test
    fun inGridAnEntryOpensTheApp() {
        val entries = listOf(notificationCard(chatApp)).launchableDockDynamicEntries()

        assertEquals(
            DockDynamicEntryIntent.Dispatch(LauncherShellAction.LaunchApp(chatApp.identity)),
            entries.single().intent,
        )
        assertEquals(1, entries.single().badgeCount)
    }

    @Test
    fun inGridAnAppTheLauncherCannotResolveHasNothingToOpen() {
        // The entry still stands -- the notification is real even when the app behind it has gone --
        // so this is a tap that does nothing rather than an entry that is missing.
        val entries = listOf(notificationCard(app = null)).launchableDockDynamicEntries()

        assertNull(entries.single().intent)
    }

    @Test
    fun inGridNothingIsEverMarkedAsShowing() {
        val entries = listOf(notificationCard(chatApp, count = 3)).launchableDockDynamicEntries()

        assertTrue(entries.none(DockDynamicEntry::isSelected))
        assertEquals(3, entries.single().badgeCount)
    }

    private fun notificationCard(
        app: InstalledApp?,
        group: InstalledApp = chatApp,
        count: Int = 1,
    ): DockNotificationCardState =
        DockNotificationCardState(
            app = app,
            group =
                AppNotificationGroup(
                    packageName = group.identity.packageName,
                    profileId = group.identity.profile.id,
                    latestCategory = NotificationCategory.MESSAGE,
                    latestAgeBucket = NotificationAgeBucket.RECENT,
                    notifications =
                        (1..count).map { index ->
                            LauncherNotification(
                                key = LauncherNotificationKey("${group.label}-$index"),
                                packageName = group.identity.packageName,
                                profileId = group.identity.profile.id,
                                title = group.label,
                                text = "waiting",
                                postedAtEpochMillis = index.toLong(),
                            )
                        },
                ),
        )

    private companion object {
        private val chatApp =
            InstalledApp(
                identity =
                    AppIdentity(
                        packageName = AppPackageName("com.riffle.chat"),
                        activityName = AppActivityName(".MainActivity"),
                        profile = AppProfile.personal(),
                    ),
                label = "Chat",
            )
    }
}
