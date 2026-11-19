package com.riffle.core.domain.launcher.icons

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppShortcutId
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.LauncherItemId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class AppLabelOverridesTest {
    private val notes = AppIdentity(AppPackageName("com.example.notes"), AppActivityName("com.example.notes.Main"))
    private val maps = AppIdentity(AppPackageName("com.example.maps"), AppActivityName("com.example.maps.Main"))

    private fun state(): LauncherShellState {
        val layout = HomeLayoutDefaults.standard()
        val page = layout.pages.first()
        val items =
            listOf(
                AppShortcutItem(LauncherItemId("a"), notes, "Notes"),
                AppShortcutItem(LauncherItemId("b"), notes, "Quick note", appShortcutId = AppShortcutId("quick")),
                FolderItem(LauncherItemId("f"), "Tools", listOf(AppShortcutItem(LauncherItemId("c"), notes, "Notes"))),
                AppShortcutItem(LauncherItemId("d"), maps, "Maps"),
            )
        return LauncherShellState(
            homeLayout = layout.copy(pages = listOf(page.copy(items = items)) + layout.pages.drop(1)),
            installedApps = listOf(InstalledApp(notes, "Notes"), InstalledApp(maps, "Maps")),
        )
    }

    @Test
    fun withoutNamesTheStateIsUntouched() {
        val state = state()

        assertSame(state, state.withAppLabelOverrides(IconStyleSettings()))
        assertSame(state, state.withAppLabelOverrides(IconStyleSettings().withIconOverride(notes, null)))
    }

    @Test
    fun aNameReplacesTheLabelOnEverySurfaceExceptAShortcutsOwn() {
        val renamed = state().withAppLabelOverrides(IconStyleSettings().withLabelOverride(notes, "Jot"))

        assertEquals(listOf("Jot", "Maps"), renamed.installedApps.map { it.label })
        val items = renamed.homeLayout.pages.first().items
        assertEquals("Jot", (items[0] as AppShortcutItem).label)
        assertEquals("Quick note", (items[1] as AppShortcutItem).label)
        assertEquals("Jot", (items[2] as FolderItem).items.single().label)
        assertEquals("Tools", (items[2] as FolderItem).label)
        assertEquals("Maps", (items[3] as AppShortcutItem).label)
    }

    @Test
    fun aNameIsForTheAppInItsProfileOnly() {
        val work = notes.copy(profile = com.riffle.core.domain.launcher.apps.AppProfile.work())

        val renamed = state().withAppLabelOverrides(IconStyleSettings().withLabelOverride(work, "Jot"))

        assertEquals("Notes", (renamed.homeLayout.pages.first().items[0] as AppShortcutItem).label)
    }
}
