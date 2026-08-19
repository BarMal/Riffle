package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.cards.AppStage
import com.riffle.core.domain.launcher.cards.AppStageId
import com.riffle.core.domain.launcher.cards.AppStageLifecycle
import com.riffle.core.domain.launcher.cards.AppStageOrigin
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
 * What the dock's dynamic side is showing, which is a different list in each view mode.
 */
class DockDynamicEntryTest {
    @Test
    fun withoutAStageAnEntryLeavesForTheApp() {
        val entries = listOf(notificationCard(app = chatApp)).launchableDockDynamicEntries()

        assertEquals(LauncherShellAction.LaunchApp(chatApp.identity), entries.single().action)
        assertEquals(1, entries.single().badgeCount)
    }

    @Test
    fun anAppTheLauncherCannotResolveHasNothingToLaunch() {
        // The entry still stands -- the notification is real even when the app behind it has gone --
        // so this is a tap that does nothing rather than an entry that is missing.
        val entries = listOf(notificationCard(app = null)).launchableDockDynamicEntries()

        assertNull(entries.single().action)
    }

    @Test
    fun aStageEntryBringsItsStageForwardRatherThanLeaving() {
        val entries = listOf(stage(chatStageId)).stageDockDynamicEntries(state, null, emptyMap())

        assertEquals(LauncherShellAction.SelectAppStage(chatStageId), entries.single().action)
    }

    @Test
    fun aStageIsStillReachableWhenTheLauncherCannotResolveItsApp() {
        // Unlike launching, this does not need the app: a stage is reachable whether or not the
        // launcher currently has an installed app to put an icon to.
        val entries =
            listOf(stage(chatStageId)).stageDockDynamicEntries(LauncherShellState(), null, emptyMap())

        assertEquals(LauncherShellAction.SelectAppStage(chatStageId), entries.single().action)
        assertNull(entries.single().identity)
    }

    @Test
    fun everyStageGetsAnEntryIncludingAPinnedApps() {
        // The dynamic side is how the user moves between stages, so leaving one out would make it
        // the only stage with no way to reach it.
        val entries =
            listOf(stage(chatStageId), stage(mailStageId, pinned = true))
                .stageDockDynamicEntries(state, null, emptyMap())

        assertEquals(listOf(chatStageId, mailStageId), entries.map { entry -> entry.action.stageId() })
    }

    @Test
    fun theStageThatIsShowingIsTheSelectedEntry() {
        val entries =
            listOf(stage(chatStageId), stage(mailStageId))
                .stageDockDynamicEntries(state, mailStageId, emptyMap())

        assertFalse(entries.first().isSelected)
        assertTrue(entries.last().isSelected)
    }

    @Test
    fun nothingShowingLeavesEveryEntryUnselected() {
        val entries =
            listOf(stage(chatStageId), stage(mailStageId)).stageDockDynamicEntries(state, null, emptyMap())

        assertTrue(entries.none { entry -> entry.isSelected })
    }

    @Test
    fun aStageEntryIsBadgedWithWhatItIsCarrying() {
        val entries =
            listOf(stage(chatStageId)).stageDockDynamicEntries(state, null, mapOf(chatStageId to 3))

        assertEquals(3, entries.single().badgeCount)
        assertTrue(entries.single().contentDescription.contains("3 cards"))
    }

    @Test
    fun aStageCarryingNothingIsNotBadged() {
        val entries = listOf(stage(chatStageId)).stageDockDynamicEntries(state, null, emptyMap())

        assertEquals(0, entries.single().badgeCount)
        assertFalse(entries.single().contentDescription.contains("card"))
    }

    @Test
    fun entriesAreKeyedApartAcrossTheTwoSources() {
        // Both sources can describe the same app, and the two entries are not interchangeable.
        val launchable = listOf(notificationCard(app = chatApp)).launchableDockDynamicEntries()
        val stages = listOf(stage(chatStageId)).stageDockDynamicEntries(state, null, emptyMap())

        assertFalse(launchable.single().key == stages.single().key)
    }

    private fun LauncherShellAction?.stageId(): AppStageId? = (this as? LauncherShellAction.SelectAppStage)?.stageId

    private fun stage(
        id: AppStageId,
        pinned: Boolean = false,
    ): AppStage =
        AppStage(
            id = id,
            origins = setOf(if (pinned) AppStageOrigin.PINNED else AppStageOrigin.DYNAMIC),
            lifecycle = AppStageLifecycle.ACTIVE,
        )

    private fun notificationCard(app: InstalledApp?): DockNotificationCardState =
        DockNotificationCardState(
            app = app,
            group =
                AppNotificationGroup(
                    packageName = chatApp.identity.packageName,
                    profileId = chatApp.identity.profile.id,
                    latestCategory = NotificationCategory.MESSAGE,
                    latestAgeBucket = NotificationAgeBucket.RECENT,
                    notifications =
                        listOf(
                            LauncherNotification(
                                key = LauncherNotificationKey("chat-1"),
                                packageName = chatApp.identity.packageName,
                                profileId = chatApp.identity.profile.id,
                                title = "Chat",
                                text = "One waiting",
                                postedAtEpochMillis = 1L,
                            ),
                        ),
                ),
        )

    private companion object {
        private val chatApp = installedApp("chat", "Chat")
        private val mailApp = installedApp("mail", "Mail")

        private val chatStageId =
            AppStageId(chatApp.identity.packageName, chatApp.identity.profile.id)
        private val mailStageId =
            AppStageId(mailApp.identity.packageName, mailApp.identity.profile.id)

        private val state = LauncherShellState(installedApps = listOf(chatApp, mailApp))

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
