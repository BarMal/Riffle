package com.riffle.app.launcher

import com.riffle.core.domain.launcher.ShellNavigationAction
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.DockBackgroundSizing
import com.riffle.core.domain.launcher.home.LauncherItemId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherActivityActionHandlerTest {
    @Test
    fun handlesDefaultHomeRequest() {
        val calls = mutableListOf<String>()
        val handler = handler(requestDefaultHome = { calls += "request-default" })

        assertTrue(handler.handle(LauncherShellAction.RequestDefaultHome))

        assertEquals(listOf("request-default"), calls)
    }

    @Test
    fun handlesNavigationActions() {
        val navigationActions = mutableListOf<ShellNavigationAction>()
        val handler = handler(navigate = navigationActions::add)

        assertTrue(handler.handle(LauncherShellAction.OpenSettings))

        assertEquals(listOf(ShellNavigationAction.OpenSettings), navigationActions)
    }

    @Test
    fun handlesOpenDefaultHomeByEditingThenNavigatingHome() {
        val calls = mutableListOf<String>()
        val handler =
            handler(
                editHomePage = { action -> calls += "edit:$action" },
                navigate = { action -> calls += "navigate:$action" },
            )

        assertTrue(handler.handle(LauncherShellAction.OpenDefaultHome))

        assertEquals(
            listOf(
                "edit:OpenDefaultHome",
                "navigate:OpenHome",
            ),
            calls,
        )
    }

    @Test
    fun routesHomeShortcutRemovalToShortcutEdits() {
        val editedActions = mutableListOf<LauncherShellAction>()
        val handler =
            handler(
                editHomeShortcut = editedActions::add,
            )
        val action = LauncherShellAction.RemoveHomeShortcut(LauncherItemId("widget:42"))

        assertTrue(handler.handle(action))

        assertEquals(listOf(action), editedActions)
    }

    @Test
    fun routesDockShortcutRemovalToDockEdits() {
        val editedActions = mutableListOf<LauncherShellAction>()
        val handler =
            handler(
                editDock = editedActions::add,
            )
        val action = LauncherShellAction.RemoveDockShortcut(LauncherItemId("dock-widget:43"))

        assertTrue(handler.handle(action))

        assertEquals(listOf(action), editedActions)
    }

    @Test
    fun handlesDockEdits() {
        val dockActions = mutableListOf<LauncherShellAction>()
        val handler = handler(editDock = dockActions::add)
        val action = LauncherShellAction.SelectDockBackgroundSizing(DockBackgroundSizing.DYNAMIC)

        assertTrue(handler.handle(action))

        assertEquals(listOf(action), dockActions)
    }

    @Test
    fun routesAddAppToDockToDockEdits() {
        val dockActions = mutableListOf<LauncherShellAction>()
        val homeShortcutActions = mutableListOf<LauncherShellAction>()
        val handler =
            handler(
                editDock = dockActions::add,
                editHomeShortcut = homeShortcutActions::add,
            )
        val action = LauncherShellAction.AddAppToDock(InstalledApp(identity = appIdentity, label = "Example"))

        assertTrue(handler.handle(action))

        assertEquals(listOf(action), dockActions)
        assertEquals(emptyList<LauncherShellAction>(), homeShortcutActions)
    }

    @Test
    fun ignoresActionsHandledByOtherRouters() {
        val handler = handler()

        assertFalse(handler.handle(LauncherShellAction.LaunchApp(appIdentity)))
    }

    private fun handler(
        requestDefaultHome: () -> Unit = {},
        navigate: (ShellNavigationAction) -> Unit = {},
        editHomePage: (LauncherShellAction) -> Unit = {},
        editHomeShortcut: (LauncherShellAction) -> Unit = {},
        editDock: (LauncherShellAction) -> Unit = {},
    ): LauncherActivityActionHandler =
        LauncherActivityActionHandler(
            requestDefaultHome = requestDefaultHome,
            navigate = navigate,
            editHomePage = editHomePage,
            editHomeShortcut = editHomeShortcut,
            editDock = editDock,
        )

    private companion object {
        val appIdentity =
            AppIdentity(
                packageName = AppPackageName("com.example.app"),
                activityName = AppActivityName(".MainActivity"),
            )
    }
}
