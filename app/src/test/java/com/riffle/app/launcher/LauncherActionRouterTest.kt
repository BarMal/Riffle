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
                notificationActionHandler =
                    LauncherNotificationActionHandler {
                        calls += "notification"
                        true
                    },
            )

        assertTrue(router.handle(LauncherShellAction.OpenSettings))

        assertEquals(listOf("activity"), calls)
    }

    @Test
    fun dispatchesAppActionsDirectlyToAppRouter() {
        val calls = mutableListOf<String>()
        val router =
            router(
                activityActionHandler =
                    activityHandler(
                        navigate = { calls += "activity" },
                    ),
                notificationActionHandler =
                    LauncherNotificationActionHandler {
                        calls += "notification"
                        false
                    },
                settingsActionHandler =
                    LauncherSettingsActionHandler {
                        calls += "settings"
                        false
                    },
                appActionHandler =
                    appHandler(
                        applyAppState = { calls += "app" },
                    ),
            )

        assertTrue(router.handle(LauncherShellAction.RefreshInstalledApps))

        assertEquals(listOf("app"), calls)
    }

    @Test
    fun dispatchesSettingsActionsDirectlyToSettingsRouter() {
        val calls = mutableListOf<String>()
        val router =
            router(
                notificationActionHandler =
                    LauncherNotificationActionHandler {
                        calls += "notification"
                        false
                    },
                settingsActionHandler =
                    LauncherSettingsActionHandler {
                        calls += "settings"
                        true
                    },
                appActionHandler =
                    appHandler(
                        applyAppState = { calls += "app" },
                    ),
            )

        assertTrue(router.handle(LauncherShellAction.RequestImportLauncherBackup))

        assertEquals(listOf("settings"), calls)
    }

    @Test
    fun dispatchesNotificationActionsDirectlyToNotificationRouter() {
        val calls = mutableListOf<String>()
        val router =
            router(
                notificationActionHandler =
                    LauncherNotificationActionHandler {
                        calls += "notification"
                        true
                    },
                settingsActionHandler =
                    LauncherSettingsActionHandler {
                        calls += "settings"
                        true
                    },
            )

        assertTrue(router.handle(LauncherShellAction.DismissNotifications(emptyList())))

        assertEquals(listOf("notification"), calls)
    }

    @Test
    fun returnsFalseWhenDomainHandlerRejectsAction() {
        val router =
            router(
                settingsActionHandler = LauncherSettingsActionHandler { false },
            )

        assertFalse(router.handle(LauncherShellAction.ExportLauncherBackup))
    }

    private fun router(
        activityActionHandler: LauncherActivityActionHandler = activityHandler(),
        notificationActionHandler: LauncherNotificationActionHandler = notificationHandler(),
        settingsActionHandler: LauncherSettingsActionHandler = settingsHandler(),
        appActionHandler: LauncherAppActionHandler = appHandler(),
    ): LauncherActionRouter =
        LauncherActionRouter(
            activityActionHandler = activityActionHandler,
            notificationActionHandler = notificationActionHandler,
            settingsActionHandler = settingsActionHandler,
            appActionHandler = appActionHandler,
        )

    private fun activityHandler(navigate: (ShellNavigationAction) -> Unit = {}): LauncherActivityActionHandler =
        LauncherActivityActionHandler(
            requestDefaultHome = {},
            navigate = navigate,
            editHomePage = {},
            editHomeShortcut = {},
            editDock = {},
        )

    private fun notificationHandler(): LauncherNotificationActionHandler = LauncherNotificationActionHandler { false }

    private fun settingsHandler(): LauncherSettingsActionHandler = LauncherSettingsActionHandler { false }

    private fun appHandler(applyAppState: (LauncherShellAction) -> Unit = {}): LauncherAppActionHandler =
        LauncherAppActionHandler(
            callbacks =
                LauncherAppActionCallbacks(
                    launch =
                        LauncherAppLaunchCallbacks(
                            launchApp = {},
                            launchAppShortcut = {},
                            searchWeb = {},
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
