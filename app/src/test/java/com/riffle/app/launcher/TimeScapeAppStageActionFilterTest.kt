package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.LauncherViewMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeScapeAppStageActionFilterTest {
    @Test
    fun allowsStageNavigationActions() {
        assertTrue(timeScapeAppStageActionFilter(LauncherShellAction.SelectNextAppStage))
        assertTrue(timeScapeAppStageActionFilter(LauncherShellAction.SelectPreviousAppStage))
    }

    @Test
    fun allowsExitingBackToStandardHome() {
        assertTrue(
            timeScapeAppStageActionFilter(
                LauncherShellAction.SelectLauncherViewMode(LauncherViewMode.STANDARD_APP_DRAWER),
            ),
        )
    }

    @Test
    fun doesNotAllowEnteringCardsModeFromWithinCardsMode() {
        assertFalse(
            timeScapeAppStageActionFilter(
                LauncherShellAction.SelectLauncherViewMode(LauncherViewMode.CARD_INTERFACE),
            ),
        )
    }

    @Test
    fun allowsOpeningTheAppDrawerFromCardsMode() {
        assertTrue(timeScapeAppStageActionFilter(LauncherShellAction.OpenAppDrawer))
    }

    @Test
    fun allowsOpeningSearchFromCardsMode() {
        assertTrue(timeScapeAppStageActionFilter(LauncherShellAction.OpenSearch))
    }

    @Test
    fun blocksUnrelatedActionsFromReachingCardsMode() {
        assertFalse(timeScapeAppStageActionFilter(LauncherShellAction.OpenSettings))
        assertFalse(timeScapeAppStageActionFilter(LauncherShellAction.OpenNotifications))
        assertFalse(
            timeScapeAppStageActionFilter(
                LauncherShellAction.LaunchApp(
                    AppIdentity(
                        packageName = AppPackageName("com.riffle.mail"),
                        activityName = AppActivityName("com.riffle.mail.MainActivity"),
                    ),
                ),
            ),
        )
    }
}
