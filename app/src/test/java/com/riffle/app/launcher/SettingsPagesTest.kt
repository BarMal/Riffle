package com.riffle.app.launcher

import com.riffle.core.domain.launcher.HomeRoleStatus
import com.riffle.core.domain.launcher.OverlayDockPermissionStatus
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.search.LauncherSearchProvider
import com.riffle.core.domain.launcher.search.LauncherSearchResultType
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsPagesTest {
    @Test
    fun labelsSelectedThemeOptionsClearly() {
        assertEquals("Teal (selected)", themeOptionLabel(optionName = "TEAL", isSelected = true))
        assertEquals("Material", themeOptionLabel(optionName = "MATERIAL", isSelected = false))
    }

    @Test
    fun mainSettingsPageUsesLauncherStyleGroups() {
        assertEquals(
            listOf(
                "Layout" to SettingsPage.LAYOUT,
                "Dock" to SettingsPage.DOCK,
                "Appearance" to SettingsPage.APPEARANCE,
                "Floating dock" to SettingsPage.FLOATING_DOCK,
                "Gestures" to SettingsPage.GESTURES,
                "Contextual" to SettingsPage.CONTEXTUAL,
                "Motion" to SettingsPage.MOTION,
                "Haptics" to SettingsPage.HAPTICS,
                "App drawer" to SettingsPage.APPS,
                "Hidden apps" to SettingsPage.HIDDEN_APPS,
                "Permissions" to SettingsPage.PERMISSIONS,
                "Backup" to SettingsPage.BACKUP,
                "About" to SettingsPage.VERSION,
            ),
            settingsMainPageEntries().map { entry ->
                entry.label to entry.page
            },
        )

        assertEquals(
            listOf(
                SettingsPageGroup.HOME,
                SettingsPageGroup.INTERACTION,
                SettingsPageGroup.APPS,
                SettingsPageGroup.SYSTEM,
            ),
            settingsMainPageGroups(),
        )
    }

    @Test
    fun filtersMainSettingsEntriesByMultipleTokens() {
        assertEquals(
            listOf(SettingsPage.LAYOUT),
            settingsMainPageEntriesMatching("home grid").map { entry -> entry.page },
        )
        assertEquals(
            listOf(SettingsPage.FLOATING_DOCK),
            settingsMainPageEntriesMatching("overlay shortcuts").map { entry -> entry.page },
        )
        assertEquals(
            listOf(SettingsPage.BACKUP),
            settingsMainPageEntriesMatching("system export").map { entry -> entry.page },
        )
        assertEquals(
            listOf(SettingsPage.CONTEXTUAL),
            settingsMainPageEntriesMatching("dynamic actions").map { entry -> entry.page },
        )
    }

    @Test
    fun filtersMainSettingsEntriesByAliasOnlyTerms() {
        assertEquals(
            listOf(SettingsPage.BACKUP),
            settingsMainPageEntriesMatching("restore").map { entry -> entry.page },
        )
        assertEquals(
            listOf(SettingsPage.APPEARANCE),
            settingsMainPageEntriesMatching("fullscreen").map { entry -> entry.page },
        )
    }

    @Test
    fun filtersMainSettingsEntriesByMultiTokenAliases() {
        assertEquals(
            listOf(SettingsPage.APPEARANCE),
            settingsMainPageEntriesMatching("status bar").map { entry -> entry.page },
        )
        assertEquals(
            listOf(SettingsPage.APPEARANCE),
            settingsMainPageEntriesMatching("navigation bar").map { entry -> entry.page },
        )
        assertEquals(
            listOf(SettingsPage.PERMISSIONS),
            settingsMainPageEntriesMatching("default home").map { entry -> entry.page },
        )
    }

    @Test
    fun filtersMainSettingsEntriesByConcreteOptionAliases() {
        assertEquals(
            listOf(SettingsPage.DOCK),
            settingsMainPageEntriesMatching("dock item spacing").map { entry -> entry.page },
        )
        assertEquals(
            listOf(SettingsPage.DOCK),
            settingsMainPageEntriesMatching("notification cards").map { entry -> entry.page },
        )
        assertEquals(
            listOf(SettingsPage.DOCK),
            settingsMainPageEntriesMatching("expanded dock cards").map { entry -> entry.page },
        )
        assertEquals(
            listOf(SettingsPage.LAYOUT),
            settingsMainPageEntriesMatching("label text size").map { entry -> entry.page },
        )
        assertEquals(
            listOf(SettingsPage.FLOATING_DOCK),
            settingsMainPageEntriesMatching("handle opacity").map { entry -> entry.page },
        )
        assertEquals(
            listOf(SettingsPage.APPS),
            settingsMainPageEntriesMatching("refresh apps").map { entry -> entry.page },
        )
    }

    @Test
    fun returnsNoMainSettingsEntriesForUnknownSearch() {
        assertEquals(emptyList<SettingsPageEntry>(), settingsMainPageEntriesMatching("missing setting"))
    }

    @Test
    fun summarizesSettingsSearchResultsOnlyWhenSearching() {
        assertEquals(null, settingsSearchSummaryText(query = "", resultCount = 12))
        assertEquals("1 setting matching \"dock\"", settingsSearchSummaryText(query = " dock ", resultCount = 1))
        assertEquals("2 settings matching \"home\"", settingsSearchSummaryText(query = "home", resultCount = 2))
    }

    @Test
    fun mainSettingsEntriesExposeLiveStatusSummaries() {
        val entries =
            settingsMainPageEntries(
                SettingsOverviewStatus(
                    homeRoleStatus = HomeRoleStatus.DEFAULT_HOME,
                    notificationAccessStatus = NotificationAccessStatus.GRANTED,
                    overlayDockPermissionStatus = OverlayDockPermissionStatus.NOT_GRANTED,
                    hiddenAppCount = 2,
                ),
            )

        assertEquals("2 hidden apps", entries.single { entry -> entry.page == SettingsPage.HIDDEN_APPS }.subtitle)
        assertEquals(
            "Home set",
            entries.single { entry -> entry.page == SettingsPage.PERMISSIONS }.subtitle,
        )
    }

    @Test
    fun filtersMainSettingsEntriesByLiveStatusSummaries() {
        val status =
            SettingsOverviewStatus(
                notificationAccessStatus = NotificationAccessStatus.GRANTED,
                overlayDockPermissionStatus = OverlayDockPermissionStatus.NOT_GRANTED,
                hiddenAppCount = 3,
            )

        assertEquals(
            listOf(SettingsPage.HIDDEN_APPS),
            settingsMainPageEntriesMatching(query = "3 hidden", status = status).map { entry -> entry.page },
        )
        assertEquals(
            emptyList<SettingsPageEntry>(),
            settingsMainPageEntriesMatching(query = "overlay not allowed", status = status),
        )
    }

    @Test
    fun projectsMainSettingsEntriesForLauncherSearch() {
        val entries =
            settingsLauncherSearchEntries(
                SettingsOverviewStatus(
                    hiddenAppCount = 2,
                ),
            )

        val hiddenApps = entries.single { entry -> entry.id.value == "hidden_apps" }
        val permissions = entries.single { entry -> entry.id.value == "permissions" }
        val results =
            LauncherSearchProvider()
                .search(
                    query = "default home",
                    apps = emptyList(),
                    settingsEntries = entries,
                )

        assertEquals("Hidden apps", hiddenApps.title)
        assertEquals("2 hidden apps", hiddenApps.subtitle)
        assertEquals("Apps", hiddenApps.section)
        assertEquals(
            listOf(
                "default home",
                "home app",
                "Permissions",
            ),
            permissions.searchAliases,
        )
        assertEquals(listOf(LauncherSearchResultType.SETTING), results.map { result -> result.type })
        assertEquals(listOf("Permissions"), results.map { result -> result.title })
    }

    @Test
    fun projectsConcreteSettingOptionAliasesForLauncherSearch() {
        val entries = settingsLauncherSearchEntries()

        assertEquals(
            listOf("Dock"),
            LauncherSearchProvider()
                .search(query = "notification shelf", apps = emptyList(), settingsEntries = entries)
                .map { result -> result.title },
        )
        assertEquals(
            listOf("Dock"),
            LauncherSearchProvider()
                .search(query = "expanded dock cards", apps = emptyList(), settingsEntries = entries)
                .map { result -> result.title },
        )
        assertEquals(
            listOf("Dock"),
            LauncherSearchProvider()
                .search(query = "dock slots", apps = emptyList(), settingsEntries = entries)
                .map { result -> result.title },
        )
        assertEquals(
            listOf("Dock"),
            LauncherSearchProvider()
                .search(query = "notification access", apps = emptyList(), settingsEntries = entries)
                .map { result -> result.title },
        )
    }

    @Test
    fun launcherSearchSettingsEntriesResolveBackToSettingsPages() {
        val entries = settingsLauncherSearchEntries()

        assertEquals(
            settingsMainPageEntries().map { entry -> entry.page },
            entries.map { entry -> entry.id.settingsPage() },
        )
    }

    @Test
    fun layoutPageUsesFoldedAndUnfoldedTabsForFoldableDevices() {
        assertEquals(
            listOf("Folded", "Unfolded"),
            settingsLayoutPageTabs(
                availableDeviceClasses =
                    setOf(
                        HomeLayoutDeviceClass.PHONE,
                        HomeLayoutDeviceClass.FOLDABLE,
                    ),
            ).map { tab -> tab.label },
        )
    }

    @Test
    fun layoutPageShowsFoldedAndUnfoldedTabsForSingleLayoutDevices() {
        assertEquals(
            listOf("Folded", "Unfolded"),
            settingsLayoutPageTabs(setOf(HomeLayoutDeviceClass.PHONE)).map { tab -> tab.label },
        )
    }
}
