package com.riffle.app.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsPageNavigationTest {
    @Test
    fun openSettingsTargetsMainSettingsPage() {
        assertEquals(
            SettingsPageNavigation(
                page = SettingsPage.MAIN,
                action = LauncherShellAction.OpenSettings,
            ),
            LauncherShellAction.OpenSettings.settingsPageNavigation(),
        )
    }

    @Test
    fun openSettingsPageTargetsRequestedPageThroughNormalSettingsNavigation() {
        assertEquals(
            SettingsPageNavigation(
                page = SettingsPage.APPEARANCE,
                action = LauncherShellAction.OpenSettings,
            ),
            LauncherShellAction.OpenSettingsPage(SettingsPage.APPEARANCE).settingsPageNavigation(),
        )
    }

    @Test
    fun nonSettingsNavigationDoesNotChangeSettingsPageTarget() {
        assertNull(LauncherShellAction.OpenSearch.settingsPageNavigation())
    }
}
