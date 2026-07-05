package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.OverlayDockSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherAppIconPreloaderTest {
    @Test
    fun includesShortcutsInsideFolders() {
        val installed = identity("installed")
        val topLevel = identity("top-level")
        val folderChild = identity("folder-child")
        val state =
            LauncherShellState(
                installedApps = listOf(installedApp(installed)),
                homeLayout =
                    layoutWithHomeItems(
                        shortcut(id = "top-level", identity = topLevel),
                        folder(
                            id = "tools",
                            shortcut(id = "folder-child", identity = folderChild),
                        ),
                    ),
            )

        assertEquals(
            listOf(installed, topLevel, folderChild),
            state.appIconPreloadIdentities(),
        )
    }

    @Test
    fun includesDockAndFloatingDockItems() {
        val dock = identity("dock")
        val floatingDock = identity("floating-dock")
        val state =
            LauncherShellState(
                homeLayout =
                    layoutWithDockItems(
                        shortcut(id = "dock", identity = dock),
                    ),
                launcherSettings =
                    LauncherSettings(
                        overlayDock =
                            OverlayDockSettings(
                                items = listOf(shortcut(id = "floating-dock", identity = floatingDock)),
                            ),
                    ),
            )

        assertEquals(
            listOf(dock, floatingDock),
            state.appIconPreloadIdentities(),
        )
    }

    @Test
    fun removesDuplicateIdentitiesInStableFirstSeenOrder() {
        val installed = identity("installed")
        val firstHome = identity("first-home")
        val folderChild = identity("folder-child")
        val dock = identity("dock")
        val floatingDock = identity("floating-dock")
        val state =
            LauncherShellState(
                installedApps = listOf(installedApp(installed)),
                homeLayout =
                    layoutWithHomeItems(
                        shortcut(id = "home-duplicate-installed", identity = installed),
                        shortcut(id = "first-home", identity = firstHome),
                        folder(
                            id = "tools",
                            shortcut(id = "folder-child", identity = folderChild),
                            shortcut(id = "folder-duplicate-home", identity = firstHome),
                        ),
                    ).let { layout ->
                        layout.copy(
                            dock =
                                layout.dock.copy(
                                    items =
                                        listOf(
                                            shortcut(id = "dock-duplicate-folder", identity = folderChild),
                                            shortcut(id = "dock", identity = dock),
                                        ),
                                ),
                        )
                    },
                launcherSettings =
                    LauncherSettings(
                        overlayDock =
                            OverlayDockSettings(
                                items =
                                    listOf(
                                        shortcut(id = "floating-dock", identity = floatingDock),
                                        shortcut(id = "floating-duplicate-dock", identity = dock),
                                    ),
                            ),
                    ),
            )

        assertEquals(
            listOf(installed, firstHome, folderChild, dock, floatingDock),
            state.appIconPreloadIdentities(),
        )
    }

    private fun layoutWithHomeItems(vararg items: LauncherItem) =
        HomeLayoutDefaults.standard().let { defaults ->
            defaults.copy(
                pages =
                    listOf(
                        defaults.selectedPage.copy(
                            items = items.toList(),
                        ),
                    ),
            )
        }

    private fun layoutWithDockItems(vararg items: LauncherItem) =
        HomeLayoutDefaults.standard().let { defaults ->
            defaults.copy(
                dock = defaults.dock.copy(items = items.toList()),
            )
        }

    private fun installedApp(identity: AppIdentity): InstalledApp {
        return InstalledApp(
            identity = identity,
            label = identity.packageName.value,
        )
    }

    private fun folder(
        id: String,
        vararg items: AppShortcutItem,
    ): FolderItem =
        FolderItem(
            id = LauncherItemId("folder:$id"),
            label = id,
            items = items.toList(),
        )

    private fun shortcut(
        id: String,
        identity: AppIdentity,
    ): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId("app:$id"),
            appIdentity = identity,
            label = id,
        )

    private fun identity(id: String): AppIdentity =
        AppIdentity(
            packageName = AppPackageName("com.riffle.$id"),
            activityName = AppActivityName(".MainActivity"),
        )
}
