package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.LauncherViewMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveStageAppStageActionFilterTest {
    @Test
    fun allowsStageNavigationActions() {
        assertTrue(adaptiveStageAppStageActionFilter(LauncherShellAction.SelectNextAppStage))
        assertTrue(adaptiveStageAppStageActionFilter(LauncherShellAction.SelectPreviousAppStage))
    }

    @Test
    fun allowsLeavingCards() {
        assertTrue(adaptiveStageAppStageActionFilter(LauncherShellAction.ExitAdaptiveStage))
    }

    @Test
    fun doesNotAllowEnteringCardsModeFromWithinCardsMode() {
        assertFalse(
            adaptiveStageAppStageActionFilter(
                LauncherShellAction.SelectLauncherViewMode(LauncherViewMode.CARD_INTERFACE),
            ),
        )
    }

    @Test
    fun allowsOpeningTheAppDrawerFromCardsMode() {
        assertTrue(adaptiveStageAppStageActionFilter(LauncherShellAction.OpenAppDrawer))
    }

    @Test
    fun allowsOpeningSearchFromCardsMode() {
        assertTrue(adaptiveStageAppStageActionFilter(LauncherShellAction.OpenSearch))
    }

    @Test
    fun blocksUnrelatedActionsFromReachingCardsMode() {
        assertFalse(adaptiveStageAppStageActionFilter(LauncherShellAction.OpenSettings))
        assertFalse(adaptiveStageAppStageActionFilter(LauncherShellAction.OpenNotifications))
        assertFalse(
            adaptiveStageAppStageActionFilter(
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
