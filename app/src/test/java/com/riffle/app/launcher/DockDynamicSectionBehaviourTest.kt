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
 * What a tap on the dock's dynamic side means, which depends on whether there is a stage behind it.
 */
class DockDynamicSectionBehaviourTest {
    @Test
    fun withoutAStageATapLeavesForTheApp() {
        assertEquals(
            LauncherShellAction.LaunchApp(chatApp.identity),
            DockDynamicSectionBehaviour.LaunchApp.actionFor(entry(app = chatApp)),
        )
    }

    @Test
    fun anAppTheLauncherCannotResolveHasNothingToLaunch() {
        // The entry still stands -- the notification is real even when the app behind it has gone --
        // so this is a tap that does nothing rather than an entry that is missing.
        assertNull(DockDynamicSectionBehaviour.LaunchApp.actionFor(entry(app = null)))
    }

    @Test
    fun withAStageATapBringsItForwardInsteadOfLeaving() {
        assertEquals(
            LauncherShellAction.SelectAppStage(chatStageId),
            DockDynamicSectionBehaviour.SelectStage().actionFor(entry(app = chatApp)),
        )
    }

    @Test
    fun aStageIsStillReachableWhenTheLauncherCannotResolveItsApp() {
        // Unlike launching, this does not need the app: a stage is built from the notifications,
        // which are what the entry is showing in the first place.
        assertEquals(
            LauncherShellAction.SelectAppStage(chatStageId),
            DockDynamicSectionBehaviour.SelectStage().actionFor(entry(app = null)),
        )
    }

    @Test
    fun theEntryWhoseStageIsShowingIsTheSelectedOne() {
        val behaviour = DockDynamicSectionBehaviour.SelectStage(adaptiveStageStageKey(chatStageId))

        assertTrue(behaviour.isSelected(entry(app = chatApp)))
    }

    @Test
    fun anotherStageShowingLeavesThisEntryUnselected() {
        val other = AppStageId(AppPackageName("com.riffle.mail"), AppProfile.personal().id)
        val behaviour = DockDynamicSectionBehaviour.SelectStage(adaptiveStageStageKey(other))

        assertFalse(behaviour.isSelected(entry(app = chatApp)))
    }

    @Test
    fun nothingShowingLeavesEveryEntryUnselected() {
        assertFalse(DockDynamicSectionBehaviour.SelectStage().isSelected(entry(app = chatApp)))
    }

    @Test
    fun launchingHasNoNotionOfASelectedEntry() {
        assertFalse(DockDynamicSectionBehaviour.LaunchApp.isSelected(entry(app = chatApp)))
    }

    private fun entry(app: InstalledApp?): DockNotificationCardState =
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

        private val chatStageId =
            AppStageId(
                packageName = chatApp.identity.packageName,
                profileId = chatApp.identity.profile.id,
            )
    }
}
