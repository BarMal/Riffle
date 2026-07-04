package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutId
import com.riffle.core.domain.launcher.apps.InstalledApp
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
}
