package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeLayoutAppMembershipTest {
    @Test
    fun detectsAppShortcutOnHomePages() {
        val camera = appIdentity("camera")
        val layout = layoutWith(appShortcut(id = "camera", identity = camera))

        assertTrue(layout.containsHomeApp(camera))
        assertFalse(layout.containsHomeApp(appIdentity("calendar")))
    }

    @Test
    fun detectsAppShortcutInsideHomeFolders() {
        val camera = appIdentity("camera")
        val layout =
            layoutWith(
                FolderItem(
                    id = LauncherItemId("folder:tools"),
                    label = "Tools",
                    items = listOf(appShortcut(id = "folder-camera", identity = camera).copy(placement = null)),
                    placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
                ),
            )

        assertTrue(layout.containsHomeApp(camera))
    }

    @Test
    fun detectsAppShortcutInDock() {
        val phone = appIdentity("phone")
        val layout =
            HomeLayoutDefaults.standard().copy(
                dock = DockModel(capacity = 5, items = listOf(appShortcut(id = "dock-phone", identity = phone))),
            )

        assertTrue(layout.dock.containsDockApp(phone))
        assertFalse(layout.dock.containsDockApp(appIdentity("camera")))
    }

    private fun layoutWith(vararg items: LauncherItem): HomeLayout =
        HomeLayoutDefaults.standard().copy(
            pages = listOf(HomeLayoutDefaults.standard().selectedPage.copy(items = items.toList())),
        )

    private fun appShortcut(
        id: String,
        identity: AppIdentity,
    ): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId(id),
            appIdentity = identity,
            label = id,
            placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
        )

    private fun appIdentity(label: String): AppIdentity =
        AppIdentity(
            packageName = AppPackageName("com.riffle.$label"),
            activityName = AppActivityName(".MainActivity"),
        )
}
