package com.riffle.core.domain.launcher.search

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppVisibility
import com.riffle.core.domain.launcher.apps.InstalledApp
import kotlin.test.Test
import kotlin.test.assertEquals

class LauncherSearchProviderTest {
    private val provider = LauncherSearchProvider()

    @Test
    fun searchReturnsMixedAppAndSettingsResults() {
        val camera = app(label = "Camera")
        val settings =
            listOf(
                settingsEntry(
                    id = "appearance",
                    title = "Appearance",
                    subtitle = "Wallpaper and system bars",
                    aliases = listOf("camera style"),
                ),
            )

        val results =
            provider.search(
                query = "cam",
                apps = listOf(camera),
                settingsEntries = settings,
            )

        assertEquals(
            listOf(LauncherSearchResultType.APP, LauncherSearchResultType.SETTING),
            results.map { result -> result.type },
        )
        assertEquals(listOf("Camera", "Appearance"), results.map { result -> result.title })
    }

    @Test
    fun searchMatchesSettingsTitlesSubtitlesSectionsAndAliases() {
        val settings =
            listOf(
                settingsEntry(
                    id = "layout",
                    title = "Layout",
                    subtitle = "Home mode and grid",
                    section = "Home screen",
                ),
                settingsEntry(
                    id = "backup",
                    title = "Backup",
                    subtitle = "Import and export launcher data",
                    section = "System",
                    aliases = listOf("restore"),
                ),
            )

        assertEquals(
            listOf("Layout"),
            provider.search(query = "layout", apps = emptyList(), settingsEntries = settings)
                .map { result -> result.title },
        )
        assertEquals(
            listOf("Layout"),
            provider.search(query = "home grid", apps = emptyList(), settingsEntries = settings)
                .map { result -> result.title },
        )
        assertEquals(
            listOf("Backup"),
            provider.search(query = "restore", apps = emptyList(), settingsEntries = settings)
                .map { result -> result.title },
        )
    }

    @Test
    fun searchExcludesHiddenAppsFromGlobalResults() {
        val visible = app(label = "Camera")
        val hidden = app(label = "Camera Vault", visibility = AppVisibility.HIDDEN)

        val results =
            provider.search(
                query = "camera",
                apps = listOf(visible, hidden),
                settingsEntries = emptyList(),
            )

        assertEquals(listOf("Camera"), results.map { result -> result.title })
    }

    @Test
    fun blankSearchReturnsNoGlobalResults() {
        val results =
            provider.search(
                query = " ",
                apps = listOf(app(label = "Camera")),
                settingsEntries = listOf(settingsEntry(id = "layout", title = "Layout")),
            )

        assertEquals(emptyList<LauncherSearchResult>(), results)
    }

    private fun app(
        label: String,
        visibility: AppVisibility = AppVisibility.VISIBLE,
    ): InstalledApp =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName("com.example.${label.lowercase().replace(" ", ".")}"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = label,
            visibility = visibility,
        )

    private fun settingsEntry(
        id: String,
        title: String,
        subtitle: String = "",
        section: String = "Settings",
        aliases: List<String> = emptyList(),
    ): LauncherSearchSettingsEntry =
        LauncherSearchSettingsEntry(
            id = LauncherSearchSettingsEntryId(id),
            title = title,
            subtitle = subtitle,
            section = section,
            searchAliases = aliases,
        )
}
