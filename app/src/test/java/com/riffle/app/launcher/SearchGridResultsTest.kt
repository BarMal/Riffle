package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutId
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.search.LauncherSearchResult
import com.riffle.core.domain.launcher.search.LauncherSearchSettingsEntry
import com.riffle.core.domain.launcher.search.LauncherSearchSettingsEntryId
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchGridResultsTest {
    @Test
    fun searchGridAppsAreAlphabetical() {
        assertEquals(
            listOf("Calendar", "Camera", "Maps"),
            searchGridApps(
                listOf(
                    app("Maps"),
                    app("Camera"),
                    app("Calendar"),
                ),
            ).map { app -> app.label },
        )
    }

    @Test
    fun searchGridResultsIncludeAppsAndShortcutsAlphabetically() {
        val camera = app("Camera")
        val maps = app("Maps")
        val shortcut = shortcut(app = maps, label = "Route home")

        val results = searchGridResults(apps = listOf(camera), shortcuts = listOf(shortcut))

        assertEquals(listOf("Camera", "Route home"), results.map { result -> result.label })
        assertEquals(
            listOf(
                LauncherShellAction.LaunchApp(camera.identity),
                LauncherShellAction.LaunchAppShortcut(shortcut),
            ),
            results.map { result -> result.action },
        )
    }

    @Test
    fun searchGridResultsIncludeDistinguishableSettingsResults() {
        val camera = app("Camera")
        val setting = setting("Appearance")

        val results = searchGridResults(apps = listOf(camera), shortcuts = emptyList(), settings = listOf(setting))

        assertEquals(listOf("Appearance", "Camera"), results.map { result -> result.label })
        assertEquals(
            listOf(
                LauncherShellAction.OpenSettingsPage(SettingsPage.APPEARANCE),
                LauncherShellAction.LaunchApp(camera.identity),
            ),
            results.map { result -> result.action },
        )
        assertEquals("setting:appearance", results.first().key)
    }

    @Test
    fun searchGridResultsFallbackToMainSettingsForUnknownSettingsEntryIds() {
        val results =
            searchGridResults(
                apps = emptyList(),
                shortcuts = emptyList(),
                settings = listOf(setting(id = "unknown", title = "Unknown")),
            )

        assertEquals(listOf(LauncherShellAction.OpenSettings), results.map { result -> result.action })
    }

    @Test
    fun searchGridResultsDoNotMixWebSearchWithLauncherResults() {
        val results = searchGridResults(apps = emptyList(), shortcuts = emptyList())

        assertEquals(emptyList<SearchGridResult>(), results)
    }

    @Test
    fun searchWebPreviewUsesTrimmedQueryAndExampleSearches() {
        val preview = searchWebPreview("  weather today  ")

        requireNotNull(preview)
        assertEquals("Search Google", preview.title)
        assertEquals("weather today", preview.subtitle)
        assertEquals(LauncherShellAction.SearchWeb("weather today"), preview.action)
        assertEquals(
            listOf("weather today images", "weather today news", "weather today videos"),
            preview.examples.map { result -> result.query },
        )
        assertEquals(
            listOf(
                LauncherShellAction.SearchWeb("weather today images"),
                LauncherShellAction.SearchWeb("weather today news"),
                LauncherShellAction.SearchWeb("weather today videos"),
            ),
            preview.examples.map { result -> result.action },
        )
    }

    @Test
    fun searchWebPreviewSkipsBlankQuery() {
        assertEquals(null, searchWebPreview("   "))
    }

    private fun app(label: String): InstalledApp =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName("com.example.${label.lowercase()}"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = label,
        )

    private fun shortcut(
        app: InstalledApp,
        label: String,
    ): AppShortcut =
        AppShortcut(
            id = AppShortcutId("${app.identity.packageName.value}:$label"),
            appIdentity = app.identity,
            shortLabel = label,
        )

    private fun setting(
        title: String,
        id: String = title.lowercase(),
    ): LauncherSearchResult.Setting =
        LauncherSearchResult.Setting(
            LauncherSearchSettingsEntry(
                id = LauncherSearchSettingsEntryId(id),
                title = title,
                subtitle = "Launcher settings",
                section = "Settings",
            ),
        )
}
