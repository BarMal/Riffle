package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherViewMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherShellHomePageActionRoutingTest {
    @Test
    fun identifiesEveryHomePageEditAction() {
        val actions =
            listOf(
                LauncherShellAction.EnterHomeEditMode,
                LauncherShellAction.ExitHomeEditMode,
                LauncherShellAction.EnterHomePageOverview,
                LauncherShellAction.AddHomePage,
                LauncherShellAction.DuplicateSelectedHomePage,
                LauncherShellAction.SelectPreviousHomePage,
                LauncherShellAction.SelectNextHomePage,
                LauncherShellAction.SelectHomePage(LauncherPageId("home")),
                LauncherShellAction.MoveSelectedHomePageLeft,
                LauncherShellAction.MoveSelectedHomePageRight,
                LauncherShellAction.DeleteSelectedHomePage,
                LauncherShellAction.SelectHomeGridDimensions(GridDimensions(columns = 5, rows = 6)),
                LauncherShellAction.SelectLibraryPageCompaction(enabled = true),
                LauncherShellAction.SelectHomeLabelBackgroundAlpha(alphaPercent = 75),
                LauncherShellAction.SelectHomeLabelTextSize(textSizeSp = 14),
                LauncherShellAction.SelectHomeLabelTextVisible(visible = false),
                LauncherShellAction.SelectLauncherViewMode(LauncherViewMode.HOME_SCREEN_LIBRARY),
                LauncherShellAction.SelectHomeLayoutDeviceClass(HomeLayoutDeviceClass.FOLDABLE),
            )

        actions.forEach { action ->
            assertTrue("$action should route to home page edits", action.isHomePageEditAction())
        }
    }

    @Test
    fun ignoresNonHomePageEditActions() {
        val actions =
            listOf(
                LauncherShellAction.OpenAppDrawer,
                LauncherShellAction.OpenSettings,
                LauncherShellAction.LaunchApp(
                    AppIdentity(
                        packageName = AppPackageName("com.riffle.camera"),
                        activityName = AppActivityName(".MainActivity"),
                    ),
                ),
            )

        actions.forEach { action ->
            assertFalse("$action should not route to home page edits", action.isHomePageEditAction())
        }
    }
}
