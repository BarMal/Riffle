package com.riffle.app.launcher

import com.riffle.core.domain.launcher.ShellNavigationAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherActionRouterTest {
    @Test
    fun handlesActivityActionsBeforeOtherRouters() {
        val calls = mutableListOf<String>()
        val router =
            router(
                activityActionHandler =
                    activityHandler(
                        navigate = { calls += "activity" },
                    ),
                handleNotificationAction = {
                    calls += "notification"
                    true
                },
            )

        assertTrue(router.handle(LauncherShellAction.OpenSettings))

        assertEquals(listOf("activity"), calls)
    }

    @Test
    fun fallsThroughToNotificationSettingsThenAppActions() {
        val calls = mutableListOf<String>()
        val router =
            router(
                handleNotificationAction = {
                    calls += "notification"
                    false
                },
                handleSettingsAction = {
                    calls += "settings"
                    false
                },
                appActionHandler =
                    appHandler(
                        applyAppState = { calls += "app" },
                    ),
            )

        assertTrue(router.handle(LauncherShellAction.RefreshInstalledApps))

        assertEquals(listOf("notification", "settings", "app"), calls)
    }

    @Test
    fun returnsFalseWhenNoHandlerAcceptsAction() {
        val router = router()

        assertFalse(router.handle(LauncherShellAction.ExportLauncherBackup))
    }

    private fun router(
        activityActionHandler: LauncherActivityActionHandler = activityHandler(),
        handleNotificationAction: (LauncherShellAction) -> Boolean = { false },
        handleSettingsAction: (LauncherShellAction) -> Boolean = { false },
        appActionHandler: LauncherAppActionHandler = appHandler(),
    ): LauncherActionRouter =
        LauncherActionRouter(
            activityActionHandler = activityActionHandler,
            handleNotificationAction = handleNotificationAction,
            handleSettingsAction = handleSettingsAction,
            appActionHandler = appActionHandler,
        )

    private fun activityHandler(navigate: (ShellNavigationAction) -> Unit = {}): LauncherActivityActionHandler =
        LauncherActivityActionHandler(
            requestDefaultHome = {},
            navigate = navigate,
            editHomePage = {},
            editHomeShortcut = {},
            editDock = {},
            hostedWidgetIdForRemovedShortcut = { null },
            deleteHostedWidget = {},
        )

    private fun appHandler(applyAppState: (LauncherShellAction) -> Unit = {}): LauncherAppActionHandler =
        LauncherAppActionHandler(
            callbacks =
                LauncherAppActionCallbacks(
                    launch =
                        LauncherAppLaunchCallbacks(
                            launchApp = {},
                            launchAppShortcut = {},
                            openAppInfo = {},
                            uninstallApp = {},
                        ),
                    addAppToHome = {},
                    requestAddWidget = {},
                    applyAppState = applyAppState,
                    appListRefreshed = {},
                ),
        )
}
