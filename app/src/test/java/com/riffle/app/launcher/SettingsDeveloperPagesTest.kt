package com.riffle.app.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Sources pages are reached from the Set up section, not the main page's groups: they never join its entries, its
 * search or the launcher search.
 */
class SettingsDeveloperPagesTest {
    private val developerPages =
        setOf(
            SettingsPage.SOURCES,
            SettingsPage.EXCLUSIONS,
            SettingsPage.ICS_FEEDS,
        ) + SourceDetailPages.pages

    @Test
    fun theMainPageEntriesDoNotListThem() {
        assertTrue(settingsMainPageEntries().none { it.page in developerPages })
    }

    @Test
    fun settingsSearchCannotFindThem() {
        val queries =
            listOf(
                "workspaces", "sources", "preset", "source", "lens",
                "hidden items", "exclusion", "rules", "calendar feeds", "ics", "saved lenses",
                "notifications source", "rss feeds source", "apps source", "used by",
            )
        queries.forEach { query ->
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
        assertEquals("Sources", SettingsPage.SOURCES.title)
        assertEquals("Hidden items and rules", SettingsPage.EXCLUSIONS.title)
        assertEquals("Calendar feeds (ICS)", SettingsPage.ICS_FEEDS.title)
    }
}
