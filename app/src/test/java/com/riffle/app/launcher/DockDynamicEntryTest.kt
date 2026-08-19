package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.cards.AppStageId
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationAgeBucket
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the dock's dynamic side shows -- the same de-duplicated notification list in both modes, but
 * a tap opens the app (grid) or brings its stage forward (Cards).
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
    fun inCardsAnEntryBringsItsStageForward() {
        val entries = listOf(notificationCard(chatApp)).stageSelectingDockDynamicEntries(selectedStageId = null)

        assertEquals(selectStage(chatStageId), entries.single().intent)
    }

    @Test
    fun inCardsAStageIsStillReachableWhenTheLauncherCannotResolveItsApp() {
        // Selecting a stage does not need the app resolved -- the stage is built from the very
        // notification the entry is showing.
        val entries = listOf(notificationCard(app = null)).stageSelectingDockDynamicEntries(selectedStageId = null)

        assertEquals(selectStage(chatStageId), entries.single().intent)
        assertNull(entries.single().identity)
    }

    @Test
    fun inCardsTheEntryWhoseStageIsShowingIsSelected() {
        val entries =
            listOf(notificationCard(chatApp), notificationCard(mailApp, group = mailApp))
                .stageSelectingDockDynamicEntries(selectedStageId = mailStageId)

        assertFalse(entries.first().isSelected)
        assertTrue(entries.last().isSelected)
    }

    @Test
    fun inCardsNothingShowingLeavesEveryEntryUnselected() {
        val entries =
            listOf(notificationCard(chatApp), notificationCard(mailApp, group = mailApp))
                .stageSelectingDockDynamicEntries(selectedStageId = null)

        assertTrue(entries.none { entry -> entry.isSelected })
    }

    @Test
    fun aCardsEntryIsBadgedWithHowManyAreWaiting() {
        val entries = listOf(notificationCard(chatApp, count = 3)).stageSelectingDockDynamicEntries(null)

        assertEquals(3, entries.single().badgeCount)
        assertTrue(entries.single().contentDescription.contains("3 cards"))
    }

    @Test
    fun theTwoModesKeyTheirEntriesApart() {
        // Both build from the same card, and the two entries mean different things, so they are not
        // interchangeable in a keyed list.
        val launchable = listOf(notificationCard(chatApp)).launchableDockDynamicEntries()
        val selecting = listOf(notificationCard(chatApp)).stageSelectingDockDynamicEntries(null)

        assertFalse(launchable.single().key == selecting.single().key)
    }

    private fun selectStage(id: AppStageId): DockDynamicEntryIntent =
        DockDynamicEntryIntent.Dispatch(LauncherShellAction.SelectAppStage(id))

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
        private val chatApp = installedApp("chat", "Chat")
        private val mailApp = installedApp("mail", "Mail")

        private val chatStageId = AppStageId(chatApp.identity.packageName, chatApp.identity.profile.id)
        private val mailStageId = AppStageId(mailApp.identity.packageName, mailApp.identity.profile.id)

        private fun installedApp(
            name: String,
            label: String,
        ): InstalledApp =
            InstalledApp(
                identity =
                    AppIdentity(
                        packageName = AppPackageName("com.riffle.$name"),
                        activityName = AppActivityName(".MainActivity"),
                        profile = AppProfile.personal(),
                    ),
                label = label,
            )
    }
}
