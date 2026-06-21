package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.WallpaperSettings
import com.riffle.core.domain.launcher.home.WallpaperSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutJsonCodecTest {
    @Test
    fun roundTripsFolderItems() {
        val camera = appShortcut(id = "camera")
        val calendar = appShortcut(id = "calendar")
        val folder =
            FolderItem(
                id = LauncherItemId("folder:tools"),
                label = "Tools",
                items = listOf(camera, calendar),
                placement = GridPlacement(cell = GridCell(column = 1, row = 2)),
            )
        val layout =
            HomeLayoutDefaults.standard().let { defaults ->
                defaults.copy(
                    pages = listOf(defaults.selectedPage.copy(items = listOf(folder))),
                )
            }

        val decodedLayout = decodeHomeLayout(encodeHomeLayout(layout))

        val decodedFolder = decodedLayout.selectedPage.items.single() as FolderItem
        assertEquals(folder.id, decodedFolder.id)
        assertEquals(folder.label, decodedFolder.label)
        assertEquals(folder.placement, decodedFolder.placement)
        assertEquals(
            listOf(camera.appIdentity, calendar.appIdentity),
            decodedFolder.items.map { item -> item.appIdentity },
        )
    }

    @Test
    fun decodesLegacyShortcutItemsWithoutType() {
        val decodedLayout =
            decodeHomeLayout(
                """
                {
                  "selectedPageId": "home",
                  "pages": [
                    {
                      "id": "home",
                      "columns": 4,
                      "rows": 5,
                      "items": [
                        {
                          "id": "app:camera:1",
                          "label": "Camera",
                          "packageName": "com.android.camera",
                          "activityName": ".CameraActivity",
                          "column": 1,
                          "row": 2,
                          "columns": 1,
                          "rows": 1
                        }
                      ]
                    }
                  ],
                  "dock": {
                    "capacity": 5,
                    "items": []
                  }
                }
                """.trimIndent(),
            )

        val shortcut = decodedLayout.selectedPage.items.single()
        assertTrue(shortcut is AppShortcutItem)
        assertEquals("Camera", (shortcut as AppShortcutItem).label)
    }

    @Test
    fun roundTripsWallpaperSettings() {
        val layout =
            HomeLayoutDefaults.standard().copy(
                settings =
                    HomeLayoutDefaults.standard().settings.copy(
                        wallpaper = WallpaperSettings(source = WallpaperSource.SOLID_COLOR),
                    ),
            )

        val decodedLayout = decodeHomeLayout(encodeHomeLayout(layout))

        assertEquals(WallpaperSource.SOLID_COLOR, decodedLayout.settings.wallpaper.source)
    }

    @Test
    fun defaultsWallpaperSettingsWhenOlderJsonDoesNotHaveSettings() {
        val decodedLayout =
            decodeHomeLayout(
                """
                {
                  "selectedPageId": "home",
                  "pages": [
                    {
                      "id": "home",
                      "columns": 4,
                      "rows": 5,
                      "items": []
                    }
                  ],
                  "dock": {
                    "capacity": 5,
                    "items": []
                  }
                }
                """.trimIndent(),
            )

        assertEquals(WallpaperSettings.system(), decodedLayout.settings.wallpaper)
    }

    private fun appShortcut(id: String): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId("app:$id:1"),
            appIdentity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.$id"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = id,
        )
}
