package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.cards.AppStageId
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.LauncherItemId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What a tap on a pinned dock app does per mode: open the app, or bring its stage forward where it
 * has one, falling back to opening where it does not.
 */
class DockStaticTapBehaviourTest {
    @Test
    fun launchOpensTheApp() {
        assertEquals(
            LauncherShellAction.LaunchApp(chat.appIdentity),
            DockStaticTapBehaviour.Launch.actionFor(chat),
        )
    }

    @Test
    fun aStageBackedAppBringsItsStageForward() {
        val behaviour = DockStaticTapBehaviour.SelectStageIfBacked(setOf(chatStageId))

        assertEquals(LauncherShellAction.SelectAppStage(chatStageId), behaviour.actionFor(chat))
    }

    @Test
    fun anAppWithNoStageOpensInstead() {
        // No dead tap: a pinned app with nothing waiting has no stage to select, so it opens -- and
        // the absence of a badge on its icon is the signal that this is what a tap will do.
        val behaviour = DockStaticTapBehaviour.SelectStageIfBacked(stageBackedAppIds = emptySet())

        assertEquals(LauncherShellAction.LaunchApp(chat.appIdentity), behaviour.actionFor(chat))
    }

    private companion object {
        private val chat =
            AppShortcutItem(
                id = LauncherItemId("chat"),
                appIdentity =
                    AppIdentity(
                        packageName = AppPackageName("com.riffle.chat"),
                        activityName = AppActivityName(".MainActivity"),
                        profile = AppProfile.personal(),
                    ),
                label = "Chat",
            )

        private val chatStageId = AppStageId(chat.appIdentity.packageName, chat.appIdentity.profile.id)
    }
}
