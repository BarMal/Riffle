package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppDrawerProfileFilter
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.apps.AppSearchContentFilter
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutId
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.widgets.WidgetProviderClassName
import com.riffle.core.domain.launcher.widgets.WidgetProviderDimensions
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LauncherAppActionRouteTest {
    @Test
    fun routesPlatformAppActions() {
        val launchApp = LauncherShellAction.LaunchApp(appIdentity)
        val launchShortcut = LauncherShellAction.LaunchAppShortcut(appShortcut)
        val openInfo = LauncherShellAction.OpenAppInfo(appIdentity)
        val uninstall = LauncherShellAction.UninstallApp(appIdentity)

        assertEquals(LauncherAppActionRoute.LaunchApp(launchApp), launchApp.launcherAppActionRoute())
        assertEquals(LauncherAppActionRoute.LaunchAppShortcut(launchShortcut), launchShortcut.launcherAppActionRoute())
        assertEquals(LauncherAppActionRoute.OpenAppInfo(openInfo), openInfo.launcherAppActionRoute())
        assertEquals(LauncherAppActionRoute.UninstallApp(uninstall), uninstall.launcherAppActionRoute())
    }

    @Test
    fun routesAddAppToHome() {
        val action = LauncherShellAction.AddAppToHome(installedApp)

        assertEquals(LauncherAppActionRoute.AddAppToHome(action), action.launcherAppActionRoute())
    }

    @Test
    fun routesWidgetRequest() {
        val action =
            LauncherShellAction.RequestAddWidget(
                provider =
                    WidgetProviderIdentity(
                        packageName = AppPackageName("com.example.widgets"),
                        className = WidgetProviderClassName(".Weather"),
                    ),
                label = "Weather",
                dimensions = WidgetProviderDimensions(minWidthDp = 100, minHeightDp = 50),
            )

        assertEquals(LauncherAppActionRoute.RequestAddWidget(action), action.launcherAppActionRoute())
    }

    @Test
    fun routesAppStateActions() {
        assertEquals(
            LauncherAppActionRoute.RefreshInstalledApps,
            LauncherShellAction.RefreshInstalledApps.launcherAppActionRoute(),
        )

        listOf(
            LauncherShellAction.HideApp(appIdentity),
            LauncherShellAction.UnhideApp(appIdentity),
        ).forEach { action ->
            assertEquals(LauncherAppActionRoute.AppVisibilityState(action), action.launcherAppActionRoute())
        }

        listOf(
            LauncherShellAction.AppDrawerQueryChanged("clock"),
            LauncherShellAction.AppDrawerProfileFilterSelected(AppDrawerProfileFilter.WORK),
            LauncherShellAction.SearchQueryChanged("camera"),
            LauncherShellAction.SearchProfileFilterSelected(AppDrawerProfileFilter.PERSONAL),
            LauncherShellAction.ToggleSearchContentFilter(AppSearchContentFilter.SHORTCUTS),
            LauncherShellAction.ToggleSearchProfileFilter(AppProfileType.WORK),
            LauncherShellAction.ResetSearchFilters,
        ).forEach { action ->
            assertEquals(LauncherAppActionRoute.AppListState(action), action.launcherAppActionRoute())
        }

        listOf(
            LauncherShellAction.OpenWidgetPicker,
            LauncherShellAction.CloseWidgetPicker,
        ).forEach { action ->
            assertEquals(LauncherAppActionRoute.WidgetPickerState(action), action.launcherAppActionRoute())
        }
    }

    @Test
    fun ignoresNonAppActions() {
        assertNull(LauncherShellAction.OpenHome.launcherAppActionRoute())
        assertNull(LauncherShellAction.ExportLauncherBackup.launcherAppActionRoute())
        assertNull(LauncherShellAction.RequestDefaultHome.launcherAppActionRoute())
    }

    private companion object {
        val appIdentity =
            AppIdentity(
                packageName = AppPackageName("com.example.app"),
                activityName = AppActivityName(".MainActivity"),
            )
        val installedApp = InstalledApp(identity = appIdentity, label = "Example")
        val appShortcut =
            AppShortcut(
                id = AppShortcutId("shortcut"),
                appIdentity = appIdentity,
                shortLabel = "Shortcut",
            )
    }
}
