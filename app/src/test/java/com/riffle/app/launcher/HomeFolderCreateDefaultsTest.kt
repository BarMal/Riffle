package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.LauncherItemId
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeFolderCreateDefaultsTest {
    @Test
    fun usesAllPageShortcutIdsForFolderCreation() {
        val camera = shortcut(id = "camera", label = "Camera")
        val calendar = shortcut(id = "calendar", label = "Calendar")
        val maps = shortcut(id = "maps", label = "Maps")

        assertEquals(
            listOf(camera.id, calendar.id, maps.id),
            listOf(camera, calendar, maps).folderCreationItemIds(),
        )
    }

    @Test
    fun labelsTwoShortcutFoldersFromBothApps() {
        val shortcuts =
            listOf(
                shortcut(id = "camera", label = "Camera"),
                shortcut(id = "calendar", label = "Calendar"),
            )

        assertEquals("Camera & Calendar", shortcuts.defaultFolderLabel())
    }

    @Test
    fun labelsLargerFoldersFromTheFirstAppAndCount() {
        val shortcuts =
            listOf(
                shortcut(id = "camera", label = "Camera"),
                shortcut(id = "calendar", label = "Calendar"),
                shortcut(id = "maps", label = "Maps"),
            )

        assertEquals("Camera + 2 more", shortcuts.defaultFolderLabel())
    }

    @Test
    fun fallsBackWhenShortcutLabelsAreBlank() {
        val shortcuts =
            listOf(
                shortcut(id = "blank", label = " "),
                shortcut(id = "empty", label = ""),
            )

        assertEquals("Folder", shortcuts.defaultFolderLabel())
    }

    @Test
    fun truncatesLongGeneratedLabels() {
        val shortcuts =
            listOf(
                shortcut(id = "first", label = "A very very long app label"),
                shortcut(id = "second", label = "Another long app label"),
            )

        assertEquals("A very very long app labe...", shortcuts.defaultFolderLabel())
    }

    private fun shortcut(
        id: String,
        label: String,
    ): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId("app:$id"),
            appIdentity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.$id"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = label,
        )
}
