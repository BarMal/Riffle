package com.riffle.app.launcher

import com.riffle.core.domain.launcher.ShellNavigationAction
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.DockBackgroundSizing
import com.riffle.core.domain.launcher.home.HostedWidgetId
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
    fun deletesHostedWidgetBeforeRemovingShortcut() {
        val editedActions = mutableListOf<LauncherShellAction>()
        val deletedWidgets = mutableListOf<HostedWidgetId>()
        val handler =
            handler(
                hostedWidgetIdForRemovedShortcut = { itemId ->
                    HostedWidgetId(42).takeIf { itemId == LauncherItemId("widget:42") }
                },
                deleteHostedWidget = deletedWidgets::add,
                editHomeShortcut = editedActions::add,
            )
        val action = LauncherShellAction.RemoveHomeShortcut(LauncherItemId("widget:42"))

        assertTrue(handler.handle(action))

        assertEquals(listOf(HostedWidgetId(42)), deletedWidgets)
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
        hostedWidgetIdForRemovedShortcut: (LauncherItemId) -> HostedWidgetId? = { null },
        deleteHostedWidget: (HostedWidgetId) -> Unit = {},
    ): LauncherActivityActionHandler =
        LauncherActivityActionHandler(
            requestDefaultHome = requestDefaultHome,
            navigate = navigate,
            editHomePage = editHomePage,
            editHomeShortcut = editHomeShortcut,
            editDock = editDock,
            hostedWidgetIdForRemovedShortcut = hostedWidgetIdForRemovedShortcut,
            deleteHostedWidget = deleteHostedWidget,
        )

    private companion object {
        val appIdentity =
            AppIdentity(
                packageName = AppPackageName("com.example.app"),
                activityName = AppActivityName(".MainActivity"),
            )
    }
}
