package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppDrawerProfileFilter
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.apps.AppSearchContentFilter
import com.riffle.core.domain.launcher.widgets.WidgetProviderClassName
import com.riffle.core.domain.launcher.widgets.WidgetProviderDimensions
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherAppActionHandlerTest {
    @Test
    fun handlesLaunchAppActions() {
        val calls = mutableListOf<LauncherShellAction.LaunchApp>()
        val handler =
            handler(
                callbacks =
                    callbacks(
                        launch =
                            launchCallbacks(
                                launchApp = calls::add,
                            ),
                    ),
            )
        val action = LauncherShellAction.LaunchApp(appIdentity)

        assertTrue(handler.handle(action))

        assertEquals(listOf(action), calls)
    }

    @Test
    fun handlesWidgetRequests() {
        val calls = mutableListOf<LauncherShellAction.RequestAddWidget>()
        val handler =
            handler(
                callbacks =
                    callbacks(
                        requestAddWidget = calls::add,
                    ),
            )
        val action =
            LauncherShellAction.RequestAddWidget(
                provider = widgetProviderIdentity,
                label = "Weather",
                dimensions = widgetProviderDimensions,
            )

        assertTrue(handler.handle(action))

        assertEquals(listOf(action), calls)
    }

    @Test
    fun showsRefreshMessageForRefreshInstalledAppsAction() {
        val appStateActions = mutableListOf<LauncherShellAction>()
        var refreshMessageCount = 0
        val handler =
            handler(
                callbacks =
                    callbacks(
                        applyAppState = appStateActions::add,
                        appListRefreshed = { refreshMessageCount += 1 },
                    ),
            )

        assertTrue(handler.handle(LauncherShellAction.RefreshInstalledApps))

        assertEquals(listOf(LauncherShellAction.RefreshInstalledApps), appStateActions)
        assertEquals(1, refreshMessageCount)
    }

    @Test
    fun delegatesAppStateBucketsWithoutRefreshMessage() {
        val appStateActions = mutableListOf<LauncherShellAction>()
        var refreshMessageCount = 0
        val handler =
            handler(
                callbacks =
                    callbacks(
                        applyAppState = appStateActions::add,
                        appListRefreshed = { refreshMessageCount += 1 },
                    ),
            )
        val actions =
            listOf(
                LauncherShellAction.HideApp(appIdentity),
                LauncherShellAction.UnhideApp(appIdentity),
                LauncherShellAction.AppDrawerQueryChanged("clock"),
                LauncherShellAction.AppDrawerProfileFilterSelected(AppDrawerProfileFilter.WORK),
                LauncherShellAction.SearchQueryChanged("camera"),
                LauncherShellAction.SearchProfileFilterSelected(AppDrawerProfileFilter.PERSONAL),
                LauncherShellAction.ToggleSearchContentFilter(AppSearchContentFilter.SHORTCUTS),
                LauncherShellAction.ToggleSearchProfileFilter(AppProfileType.WORK),
                LauncherShellAction.ResetSearchFilters,
                LauncherShellAction.OpenWidgetPicker,
                LauncherShellAction.CloseWidgetPicker,
            )

        actions.forEach { action -> assertTrue(handler.handle(action)) }

        assertEquals(actions, appStateActions)
        assertEquals(0, refreshMessageCount)
    }

    @Test
    fun ignoresNonAppActions() {
        val handler = handler()

        assertFalse(handler.handle(LauncherShellAction.OpenSettings))
    }

    private fun handler(callbacks: LauncherAppActionCallbacks = callbacks()): LauncherAppActionHandler =
        LauncherAppActionHandler(callbacks = callbacks)

    private fun callbacks(
        launch: LauncherAppLaunchCallbacks = launchCallbacks(),
        addAppToHome: (LauncherShellAction.AddAppToHome) -> Unit = {},
        requestAddWidget: (LauncherShellAction.RequestAddWidget) -> Unit = {},
        applyAppState: (LauncherShellAction) -> Unit = {},
        appListRefreshed: () -> Unit = {},
    ): LauncherAppActionCallbacks =
        LauncherAppActionCallbacks(
            launch = launch,
            addAppToHome = addAppToHome,
            requestAddWidget = requestAddWidget,
            applyAppState = applyAppState,
            appListRefreshed = appListRefreshed,
        )

    private fun launchCallbacks(
        launchApp: (LauncherShellAction.LaunchApp) -> Unit = {},
        launchAppShortcut: (LauncherShellAction.LaunchAppShortcut) -> Unit = {},
        openAppInfo: (LauncherShellAction.OpenAppInfo) -> Unit = {},
        uninstallApp: (LauncherShellAction.UninstallApp) -> Unit = {},
    ): LauncherAppLaunchCallbacks =
        LauncherAppLaunchCallbacks(
            launchApp = launchApp,
            launchAppShortcut = launchAppShortcut,
            openAppInfo = openAppInfo,
            uninstallApp = uninstallApp,
        )

    private companion object {
        val appIdentity =
            AppIdentity(
                packageName = AppPackageName("com.example.app"),
                activityName = AppActivityName(".MainActivity"),
            )
        val widgetProviderIdentity =
            WidgetProviderIdentity(
                packageName = AppPackageName("com.example.widget"),
                className = WidgetProviderClassName(".Widget"),
            )
        val widgetProviderDimensions =
            WidgetProviderDimensions(
                minWidthDp = 80,
                minHeightDp = 80,
            )
    }
}
