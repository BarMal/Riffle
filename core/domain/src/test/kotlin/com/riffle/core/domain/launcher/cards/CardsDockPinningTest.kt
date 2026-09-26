package com.riffle.core.domain.launcher.cards

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.WidgetItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pinning a Cards stage means pinning its app to the dock (#XXXX): these pin/unpin the dock's own
 * items, so a stage's live pinned state is always what the dock itself says, in every mode.
 */
class CardsDockPinningTest {
    @Test
    fun aDockAppIdentityBelongsToItsPackageAndProfileStage() {
        assertEquals(AppStageId(mail.packageName, mail.profile.id), mail.toAppStageId())
    }

    @Test
    fun dockPinnedStageIdsReadsOnlyAppShortcutsInTheDocksOwnOrder() {
        val dock =
            DockModel(
                capacity = 3,
                items =
                    listOf(
                        AppShortcutItem(LauncherItemId("mail"), mail, "Mail"),
                        WidgetItem(LauncherItemId("clock"), HostedWidgetId(1), "Clock"),
                        AppShortcutItem(LauncherItemId("chat"), chat, "Chat"),
                    ),
            )

        assertEquals(listOf(mail.toAppStageId(), chat.toAppStageId()), dockPinnedStageIds(dock))
    }

    @Test
    fun dockPinnedStageIdsDeduplicatesMultipleActivitiesOfTheSamePackage() {
        val mailCompose = mail.copy(activityName = AppActivityName(".Compose"))
        val dock =
            DockModel(
                capacity = 2,
                items =
                    listOf(
                        AppShortcutItem(LauncherItemId("mail"), mail, "Mail"),
                        AppShortcutItem(LauncherItemId("mail-compose"), mailCompose, "Mail"),
                    ),
            )

        assertEquals(listOf(mail.toAppStageId()), dockPinnedStageIds(dock))
    }

    @Test
    fun itemIdsForStageFindsEveryPinnedActivityOfThatStagesApp() {
        val mailCompose = mail.copy(activityName = AppActivityName(".Compose"))
        val dock =
            DockModel(
                capacity = 3,
                items =
                    listOf(
                        AppShortcutItem(LauncherItemId("mail"), mail, "Mail"),
                        AppShortcutItem(LauncherItemId("mail-compose"), mailCompose, "Mail"),
                        AppShortcutItem(LauncherItemId("chat"), chat, "Chat"),
                    ),
            )

        assertEquals(
            listOf(LauncherItemId("mail"), LauncherItemId("mail-compose")),
            dock.itemIdsForStage(mail.toAppStageId()),
        )
        assertEquals(emptyList(), dock.itemIdsForStage(AppStageId(AppPackageName("com.riffle.gone"), mail.profile.id)))
    }

    @Test
    fun representativeAppIsTheAlphabeticallyFirstActivityOfAStagesInstalledApps() {
        val mailCompose = InstalledApp(mail.copy(activityName = AppActivityName(".Compose")), "Mail")
        val mailMain = InstalledApp(mail.copy(activityName = AppActivityName(".Main")), "Mail")

        assertEquals(
            mailCompose,
            representativeInstalledAppForStage(mail.toAppStageId(), listOf(mailMain, mailCompose)),
        )
    }

    @Test
    fun representativeAppIsNullWhenTheStagesAppIsNotInstalled() {
        assertNull(representativeInstalledAppForStage(mail.toAppStageId(), emptyList()))
    }

    private companion object {
        private val mail =
            AppIdentity(
                packageName = AppPackageName("com.riffle.mail"),
                activityName = AppActivityName(".MainActivity"),
                profile = AppProfile.personal(),
            )
        private val chat =
            AppIdentity(
                packageName = AppPackageName("com.riffle.chat"),
                activityName = AppActivityName(".MainActivity"),
                profile = AppProfile.personal(),
            )
    }
}
