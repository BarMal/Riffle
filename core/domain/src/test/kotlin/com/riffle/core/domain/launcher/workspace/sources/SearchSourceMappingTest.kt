package com.riffle.core.domain.launcher.workspace.sources

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.search.LauncherSearchProvider
import com.riffle.core.domain.launcher.search.LauncherSearchSettingsEntry
import com.riffle.core.domain.launcher.search.LauncherSearchSettingsEntryId
import com.riffle.core.domain.launcher.workspace.ItemTarget
import com.riffle.core.domain.launcher.workspace.SourceIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchSourceMappingTest {
    private val mapper = SearchItemMapper()

    private val camera =
        InstalledApp(
            AppIdentity(AppPackageName("cam"), AppActivityName("cam.Main"), AppProfile.personal()),
            label = "Camera",
        )
    private val display =
        LauncherSearchSettingsEntry(LauncherSearchSettingsEntryId("display"), "Display", "Theme", "Look")

    private fun search(query: String) =
        mapper.items(LauncherSearchProvider().search(query, listOf(camera), listOf(display)))

    @Test
    fun `results map to grouped items with launch targets and keep the provider order`() {
        val items = search("c")

        val app = items.single { it.groupKey == "apps" }
        assertEquals("search:app:cam/cam.Main/${AppProfile.personal().id.value}", app.id.value)
        assertEquals(SourceIds.SEARCH, app.sourceId)
        assertEquals(ItemTarget.App("cam", AppProfile.personal().id.value), app.target)
        assertEquals("Camera", app.title)
        assertEquals("Apps", app.groupLabel)
        assertEquals(ItemImageKeys.appIcon(camera.identity), app.icon)
    }

    @Test
    fun `settings map to a source-owned intent token and a section extra`() {
        val setting = search("display").single { it.groupKey == "settings" }

        assertEquals(ItemTarget.Intent("launcher-setting:display"), setting.target)
        assertEquals("Settings", setting.groupLabel)
        assertTrue(setting.ext.isNotEmpty())
    }

    @Test
    fun `a blank query has no results and the query never appears in items`() {
        assertTrue(search("  ").isEmpty())
        val items = search("ame")
        assertTrue(items.isNotEmpty())
        assertTrue(items.none { item -> item.id.value.contains("ame") })
    }

    @Test
    fun `holder trims bounds and notifies only on change`() {
        val holder = SearchQueryHolder()
        var changes = 0
        val stop = holder.observe { changes++ }

        holder.set("  cam ")
        holder.set("cam")
        assertEquals("cam", holder.current())
        assertEquals(1, changes)

        holder.set("x".repeat(MAX_SEARCH_QUERY_LENGTH + 50))
        assertEquals(MAX_SEARCH_QUERY_LENGTH, holder.current().length)

        stop()
        holder.clear()
        assertEquals("", holder.current())
        assertEquals(2, changes)
    }

    @Test
    fun `holder never reveals the query through toString`() {
        val holder = SearchQueryHolder()
        holder.set("secret words")
        assertFalse(holder.toString().contains("secret"))
    }
}
