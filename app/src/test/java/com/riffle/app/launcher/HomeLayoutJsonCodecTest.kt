package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppShortcutId
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockBackgroundSizing
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridInsets
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridSettings
import com.riffle.core.domain.launcher.home.GridSpacing
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HomeLabelSettings
import com.riffle.core.domain.launcher.home.HomeLabelSizing
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.WallpaperScrollMode
import com.riffle.core.domain.launcher.home.WallpaperSettings
import com.riffle.core.domain.launcher.home.WallpaperSource
import com.riffle.core.domain.launcher.home.WidgetItem
import org.json.JSONObject
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
    fun roundTripsWidgetItems() {
        val widget =
            WidgetItem(
                id = LauncherItemId("widget:weather"),
                appWidgetId = HostedWidgetId(42),
                label = "Weather",
                placement =
                    GridPlacement(
                        cell = GridCell(column = 1, row = 2),
                        span = GridSpan(columns = 2, rows = 2),
                    ),
            )
        val layout =
            HomeLayoutDefaults.standard().let { defaults ->
                defaults.copy(
                    pages = listOf(defaults.selectedPage.copy(items = listOf(widget))),
                )
            }

        val decodedLayout = decodeHomeLayout(encodeHomeLayout(layout))
        val decodedWidget = decodedLayout.selectedPage.items.single() as WidgetItem

        assertEquals(widget, decodedWidget)
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

    fun roundTripsPlatformAppShortcutItems() {
        val shortcut =
            appShortcut(id = "compose")
                .copy(
                    label = "Compose",
                    appShortcutId = AppShortcutId("dynamic-compose"),
                    placement = GridPlacement(cell = GridCell(column = 2, row = 3)),
                )
        val layout =
            HomeLayoutDefaults.standard().let { defaults ->
                defaults.copy(
                    pages = listOf(defaults.selectedPage.copy(items = listOf(shortcut))),
                )
            }

        val decodedLayout = decodeHomeLayout(encodeHomeLayout(layout))

        val decodedShortcut = decodedLayout.selectedPage.items.single() as AppShortcutItem
        assertEquals(shortcut.id, decodedShortcut.id)
        assertEquals(shortcut.appShortcutId, decodedShortcut.appShortcutId)
        assertEquals(shortcut.label, decodedShortcut.label)
        assertEquals(shortcut.placement, decodedShortcut.placement)
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
        assertEquals(WallpaperScrollMode.STATIC, decodedLayout.settings.wallpaper.scrollMode)
    }

    @Test
    fun roundTripsHomeLabelSettings() {
        val labelSettings =
            HomeLabelSettings(
                backgroundAlphaPercent = 75,
                textSizeSp = 14,
                showText = false,
                maxWidthDp = 112,
                maxLines = 2,
                sizing = HomeLabelSizing.DYNAMIC,
            )
        val layout =
            HomeLayoutDefaults.standard().copy(
                settings =
                    HomeLayoutDefaults.standard().settings.copy(
                        labels = labelSettings,
                    ),
            )

        val decodedLayout = decodeHomeLayout(encodeHomeLayout(layout))

        assertEquals(labelSettings, decodedLayout.settings.labels)
    }

    @Test
    fun roundTripsGridSettings() {
        val gridSettings =
            GridSettings(
                dimensions = GridDimensions(columns = 5, rows = 6),
                margin = GridInsets(start = 1, top = 2, end = 3, bottom = 4),
                padding = GridInsets(start = 5, top = 6, end = 7, bottom = 8),
                cellSpacing = GridSpacing(horizontal = 9, vertical = 10),
                compactLibraryPages = true,
            )
        val layout =
            HomeLayoutDefaults.standard().copy(
                settings =
                    HomeLayoutDefaults.standard().settings.copy(
                        grid = gridSettings,
                    ),
            )

        val decodedLayout = decodeHomeLayout(encodeHomeLayout(layout))

        assertEquals(gridSettings, decodedLayout.settings.grid)
    }

    @Test
    fun roundTripsDockVisibility() {
        val layout =
            HomeLayoutDefaults.standard().copy(
                dock = HomeLayoutDefaults.standard().dock.copy(isEnabled = false),
            )

        val decodedLayout = decodeHomeLayout(encodeHomeLayout(layout))

        assertEquals(false, decodedLayout.dock.isEnabled)
    }

    @Test
    fun roundTripsDockIconSize() {
        val layout =
            HomeLayoutDefaults.standard().copy(
                dock = HomeLayoutDefaults.standard().dock.copy(iconSizeDp = 52),
            )

        val decodedLayout = decodeHomeLayout(encodeHomeLayout(layout))

        assertEquals(52, decodedLayout.dock.iconSizeDp)
    }

    @Test
    fun roundTripsDockBackgroundAlpha() {
        val layout =
            HomeLayoutDefaults.standard().copy(
                dock = HomeLayoutDefaults.standard().dock.copy(backgroundAlphaPercent = 85),
            )

        val decodedLayout = decodeHomeLayout(encodeHomeLayout(layout))

        assertEquals(85, decodedLayout.dock.backgroundAlphaPercent)
    }

    @Test
    fun roundTripsDockBackgroundSizing() {
        val layout =
            HomeLayoutDefaults.standard().copy(
                dock = HomeLayoutDefaults.standard().dock.copy(backgroundSizing = DockBackgroundSizing.FIXED),
            )

        val decodedLayout = decodeHomeLayout(encodeHomeLayout(layout))

        assertEquals(DockBackgroundSizing.FIXED, decodedLayout.dock.backgroundSizing)
    }

    @Test
    fun roundTripsDockItemSpacing() {
        val layout =
            HomeLayoutDefaults.standard().copy(
                dock = HomeLayoutDefaults.standard().dock.copy(itemSpacingDp = 14),
            )

        val decodedLayout = decodeHomeLayout(encodeHomeLayout(layout))

        assertEquals(14, decodedLayout.dock.itemSpacingDp)
    }

    @Test
    fun roundTripsLauncherViewMode() {
        val layout = HomeLayoutDefaults.standard().copy(viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY)

        val decodedLayout = decodeHomeLayout(encodeHomeLayout(layout))

        assertEquals(LauncherViewMode.HOME_SCREEN_LIBRARY, decodedLayout.viewMode)
    }

    @Test
    fun roundTripsGeneratedLibraryPageType() {
        val layout =
            HomeLayoutDefaults.standard().copy(
                viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY,
                pages =
                    listOf(
                        HomeLayoutDefaults.standard().selectedPage,
                        LauncherPage(
                            id = LauncherPageId("library:1"),
                            type = LauncherPageType.AllApps,
                            grid = GridDimensions(columns = 4, rows = 5),
                        ),
                    ),
            )

        val decodedLayout = decodeHomeLayout(encodeHomeLayout(layout))

        assertEquals(LauncherPageType.AllApps, decodedLayout.pages[1].type)
    }

    @Test
    fun infersGeneratedLibraryPageTypeForLegacyJson() {
        val decodedLayout =
            decodeHomeLayout(
                """
                {
                  "viewMode": "HOME_SCREEN_LIBRARY",
                  "selectedPageId": "library:1",
                  "pages": [
                    {
                      "id": "home",
                      "columns": 4,
                      "rows": 5,
                      "items": []
                    },
                    {
                      "id": "library:1",
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

        assertEquals(LauncherPageType.AllApps, decodedLayout.pages[1].type)
    }

    @Test
    fun roundTripsHomeLayoutSet() {
        val standard = HomeLayoutDefaults.standard()
        val cards =
            HomeLayoutDefaults.standard().copy(
                viewMode = LauncherViewMode.CARD_INTERFACE,
                dock = HomeLayoutDefaults.standard().dock.copy(capacity = 4),
            )
        val layoutSet =
            HomeLayoutSet(
                activeKey = HomeLayoutKey(LauncherViewMode.CARD_INTERFACE),
                layouts =
                    mapOf(
                        HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER) to standard,
                        HomeLayoutKey(LauncherViewMode.CARD_INTERFACE) to cards,
                    ),
                preferredModesByDeviceClass =
                    mapOf(
                        HomeLayoutDeviceClass.PHONE to LauncherViewMode.CARD_INTERFACE,
                    ),
            )

        val decodedLayoutSet = decodeHomeLayoutSet(encodeHomeLayoutSet(layoutSet))

        assertEquals(HomeLayoutKey(LauncherViewMode.CARD_INTERFACE), decodedLayoutSet.activeKey)
        assertEquals(4, decodedLayoutSet.activeLayout.dock.capacity)
        assertEquals(5, decodedLayoutSet.layoutFor(HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER)).dock.capacity)
        assertEquals(
            mapOf(HomeLayoutDeviceClass.PHONE to LauncherViewMode.CARD_INTERFACE),
            decodedLayoutSet.preferredModesByDeviceClass,
        )
    }

    @Test
    fun decodesLegacySingleLayoutAsHomeLayoutSet() {
        val layout = HomeLayoutDefaults.standard().copy(viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY)

        val decodedLayoutSet = decodeHomeLayoutSet(encodeHomeLayout(layout))

        assertEquals(HomeLayoutKey(LauncherViewMode.HOME_SCREEN_LIBRARY), decodedLayoutSet.activeKey)
        assertEquals(LauncherViewMode.HOME_SCREEN_LIBRARY, decodedLayoutSet.activeLayout.viewMode)
    }

    @Test
    fun defaultsLauncherViewModeWhenOlderJsonDoesNotHaveViewMode() {
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

        assertEquals(HomeLayoutDefaults.standard().viewMode, decodedLayout.viewMode)
    }

    @Test
    fun defaultsGridSettingsWhenOlderJsonOnlyHasWallpaperSettings() {
        val decodedLayout =
            decodeHomeLayout(
                """
                {
                  "selectedPageId": "home",
                  "settings": {
                    "wallpaper": {
                      "source": "SOLID_COLOR"
                    }
                  },
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

        assertEquals(HomeLayoutDefaults.standard().settings.grid, decodedLayout.settings.grid)
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

    @Test
    fun defaultsHomeLabelSettingsWhenOlderJsonDoesNotHaveSettings() {
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

        assertEquals(HomeLabelSettings.standard(), decodedLayout.settings.labels)
    }

    @Test
    fun defaultsHomeLabelSizingWhenOlderJsonDoesNotHaveIt() {
        val decodedLayout =
            decodeHomeLayout(
                """
                {
                  "selectedPageId": "home",
                  "settings": {
                    "labels": {
                      "backgroundAlphaPercent": 75,
                      "textSizeSp": 14,
                      "showText": true,
                      "maxWidthDp": 112,
                      "maxLines": 2
                    }
                  },
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

        assertEquals(HomeLabelSizing.FIXED, decodedLayout.settings.labels.sizing)
    }

    @Test
    fun defaultsDockVisibilityWhenOlderJsonDoesNotHaveIt() {
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

        assertEquals(true, decodedLayout.dock.isEnabled)
    }

    @Test
    fun defaultsDockIconSizeWhenOlderJsonDoesNotHaveIt() {
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

        assertEquals(HomeLayoutDefaults.standard().dock.iconSizeDp, decodedLayout.dock.iconSizeDp)
    }

    @Test
    fun defaultsMissingDockFieldsFromTargetDeviceClassDefaults() {
        val foldableDefaults = HomeLayoutDefaults.standard(HomeLayoutDeviceClass.FOLDABLE)
        val decodedLayout =
            JSONObject(
                """
                {
                  "selectedPageId": "home",
                  "pages": [
                    {
                      "id": "home",
                      "columns": 6,
                      "rows": 6,
                      "items": []
                    }
                  ],
                  "dock": {
                    "capacity": 6,
                    "items": []
                  }
                }
                """.trimIndent(),
            ).toHomeLayout(defaults = foldableDefaults)

        assertEquals(foldableDefaults.dock.iconSizeDp, decodedLayout.dock.iconSizeDp)
        assertEquals(foldableDefaults.dock.itemSpacingDp, decodedLayout.dock.itemSpacingDp)
    }

    @Test
    fun defaultsDockBackgroundAlphaWhenOlderJsonDoesNotHaveIt() {
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

        assertEquals(
            HomeLayoutDefaults.standard().dock.backgroundAlphaPercent,
            decodedLayout.dock.backgroundAlphaPercent,
        )
    }

    @Test
    fun defaultsDockBackgroundSizingWhenOlderJsonDoesNotHaveIt() {
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

        assertEquals(
            HomeLayoutDefaults.standard().dock.backgroundSizing,
            decodedLayout.dock.backgroundSizing,
        )
    }

    @Test
    fun defaultsDockItemSpacingWhenOlderJsonDoesNotHaveIt() {
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

        assertEquals(HomeLayoutDefaults.standard().dock.itemSpacingDp, decodedLayout.dock.itemSpacingDp)
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
