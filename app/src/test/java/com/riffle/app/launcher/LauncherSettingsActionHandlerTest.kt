package com.riffle.app.launcher

import com.riffle.core.domain.launcher.settings.HapticFeedbackStrength
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherSettingsActionHandlerTest {
    @Test
    fun handlesSettingsStateActions() {
        val calls = mutableListOf<LauncherShellAction>()
        val handler =
            handler(
                callbacks =
                    callbacks(
                        applySettingsState = calls::add,
                    ),
            )
        val action = LauncherShellAction.SelectHapticFeedbackStrength(HapticFeedbackStrength.LIGHT)

        assertTrue(handler.handle(action))

        assertEquals(listOf(action), calls)
    }

    @Test
    fun handlesSettingsSideEffects() {
        val calls = mutableListOf<String>()
        val handler =
            handler(
                callbacks =
                    callbacks(
                        requestNotificationAccess = { calls += "notifications" },
                        requestOverlayDockPermission = { calls += "overlay" },
                        exportBackup = { calls += "export" },
                        importBackup = { calls += "import" },
                    ),
            )

        assertTrue(handler.handle(LauncherShellAction.RequestNotificationAccess))
        assertTrue(handler.handle(LauncherShellAction.RequestOverlayDockPermission))
        assertTrue(handler.handle(LauncherShellAction.ExportLauncherBackup))
        assertTrue(handler.handle(LauncherShellAction.RequestImportLauncherBackup))

        assertEquals(listOf("notifications", "overlay", "export", "import"), calls)
    }

    @Test
    fun ignoresNonSettingsActions() {
        val handler = handler()

        assertFalse(handler.handle(LauncherShellAction.RefreshInstalledApps))
    }

    private fun handler(callbacks: LauncherSettingsActionCallbacks = callbacks()): LauncherSettingsActionHandler =
        DefaultLauncherSettingsActionHandler(callbacks = callbacks)

    private fun callbacks(
        applySettingsState: (LauncherShellAction) -> Unit = {},
        requestNotificationAccess: () -> Unit = {},
        requestOverlayDockPermission: () -> Unit = {},
        exportBackup: () -> Unit = {},
        importBackup: () -> Unit = {},
    ): LauncherSettingsActionCallbacks =
        LauncherSettingsActionCallbacks(
            applySettingsState = applySettingsState,
            requestNotificationAccess = requestNotificationAccess,
            requestOverlayDockPermission = requestOverlayDockPermission,
            exportBackup = exportBackup,
            importBackup = importBackup,
        )
}
