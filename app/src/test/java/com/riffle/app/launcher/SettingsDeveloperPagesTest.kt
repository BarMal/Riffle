package com.riffle.app.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Workspaces page is a Developer page: it never joins the main page's groups, its search or
 * the launcher search, so with the preview off nothing about Settings differs.
 */
class SettingsDeveloperPagesTest {
    private val developerPages = setOf(SettingsPage.WORKSPACES)

    @Test
    fun theMainPageEntriesDoNotListThem() {
        assertTrue(settingsMainPageEntries().none { it.page in developerPages })
    }

    @Test
    fun settingsSearchCannotFindThem() {
        listOf("workspaces", "preset", "lens").forEach { query ->
            assertTrue(query, settingsMainPageEntriesMatching(query).none { it.page in developerPages })
        }
        val pageIds = developerPages.map { it.name.lowercase() }
        assertTrue(settingsLauncherSearchEntries().none { entry -> entry.id.value in pageIds })
    }

    @Test
    fun itIsNamedForItsTitle() {
        assertEquals("Workspaces", SettingsPage.WORKSPACES.title)
    }
}
