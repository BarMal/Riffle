package com.riffle.core.domain.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.cards.AppStageId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class LauncherShellStateStageIndexTest {
    @Test
    fun theStageIndexFindsEachAppByPackageAndProfile() {
        val mail = app("com.example.mail", "Inbox")
        val music = app("com.example.music", "Player")
        val state = LauncherShellState(installedApps = listOf(mail, music))

        assertEquals(music, state.installedAppsByStageId[stageId(music)])
        assertEquals(mail, state.installedAppsByStageId[stageId(mail)])
        val uninstalled = AppStageId(AppPackageName("com.example.gone"), mail.identity.profile.id)
        assertNull(state.installedAppsByStageId[uninstalled])
    }

    @Test
    fun theFirstLauncherActivityOfAnAppWinsLikeTheLinearSearchItReplaced() {
        val first = app("com.example.suite", "Mail")
        val second = app("com.example.suite", "Calendar")
        val state = LauncherShellState(installedApps = listOf(first, second))

        assertSame(first, state.installedAppsByStageId[stageId(first)])
    }

    @Test
    fun aNewStateWithDifferentAppsNeverSeesTheOldIndex() {
        val mail = app("com.example.mail", "Inbox")
        val music = app("com.example.music", "Player")
        val before = LauncherShellState(installedApps = listOf(mail))
        assertEquals(mail, before.installedAppsByStageId[stageId(mail)])

        val after = before.copy(installedApps = listOf(music))

        assertNull(after.installedAppsByStageId[stageId(mail)])
        assertEquals(music, after.installedAppsByStageId[stageId(music)])
    }

    private fun app(
        packageName: String,
        activity: String,
    ): InstalledApp =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName(packageName),
                    activityName = AppActivityName("$packageName.$activity"),
                    profile = AppProfile.personal(),
                ),
            label = activity,
        )

    private fun stageId(app: InstalledApp): AppStageId = AppStageId(app.identity.packageName, app.identity.profile.id)
}
