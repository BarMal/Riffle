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
    fun doesNotAllowEnteringCardsModeFromWithinCardsMode() {
        assertFalse(
            adaptiveStageAppStageActionFilter(
                LauncherShellAction.SelectLauncherViewMode(LauncherViewMode.CARD_INTERFACE),
            ),
        )
    }

    @Test
    fun doesNotLetAGestureOpenTheAppDrawerFromCardsMode() {
        // No gesture can be bound to the drawer any more; the dock pull reaches Library.
        assertFalse(adaptiveStageAppStageActionFilter(LauncherShellAction.OpenAppDrawer))
    }

    @Test
    fun allowsOpeningSearchFromCardsMode() {
        assertTrue(adaptiveStageAppStageActionFilter(LauncherShellAction.OpenSearch))
    }

    @Test
    fun allowsTheSettingsGestureFromCardsModeSoItIsNeverADeadEnd() {
        // #1212: a home gesture bound to Settings used to be filtered out in Cards.
        assertTrue(adaptiveStageAppStageActionFilter(LauncherShellAction.OpenSettings))
    }

    @Test
    fun blocksUnrelatedActionsFromReachingCardsMode() {
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
