package com.riffle.app.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Workspaces and Sources pages are Developer pages: they never join the main page's groups, its search or
 * the launcher search, so with the preview off nothing about Settings differs.
 */
class SettingsDeveloperPagesTest {
    private val developerPages = setOf(SettingsPage.WORKSPACES, SettingsPage.SOURCES)

    @Test
    fun theMainPageEntriesDoNotListThem() {
        assertTrue(settingsMainPageEntries().none { it.page in developerPages })
    }

    @Test
    fun settingsSearchCannotFindThem() {
        listOf("workspaces", "sources", "preset", "source", "lens").forEach { query ->
            assertTrue(query, settingsMainPageEntriesMatching(query).none { it.page in developerPages })
        }
        assertTrue(
            settingsLauncherSearchEntries().none {
                    entry ->
                entry.id.value in developerPages.map { it.name.lowercase() }
            },
        )
    }

    @Test
    fun theyAreNamedForTheirTitles() {
        assertEquals("Workspaces", SettingsPage.WORKSPACES.title)
        assertEquals("Sources", SettingsPage.SOURCES.title)
    }
}
