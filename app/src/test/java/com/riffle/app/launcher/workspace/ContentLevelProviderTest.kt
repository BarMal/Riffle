package com.riffle.app.launcher.workspace

import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.ItemId
import com.riffle.core.domain.launcher.workspace.ItemTarget
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensResult
import com.riffle.core.domain.launcher.workspace.SourceCapability
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.SourceState
import com.riffle.core.domain.launcher.workspace.lens.SourceContentLevel
import com.riffle.core.domain.launcher.workspace.settings.InMemoryContentLevelStore
import com.riffle.core.domain.launcher.workspace.settings.StoredSourceContentPrivacy
import com.riffle.core.domain.launcher.workspace.testing.FakeItemSource
import com.riffle.core.domain.launcher.workspace.testing.FakeSourceRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executor

class ContentLevelProviderTest {
    private val id = SourceIds.NOTIFICATIONS
    private val items =
        listOf(
            Item(ItemId("a"), id, ItemTarget.App("chat", "personal"), title = "Dinner?", body = "At eight"),
            Item(ItemId("b"), id, ItemTarget.App("mail", "personal"), title = "Invoice", body = "Due Friday"),
        )
    private val source =
        FakeItemSource(SourceDescriptor(id, setOf(SourceCapability.PRIVACY_SENSITIVE)), SourceState.Ready(items))
    private val registry = FakeSourceRegistry(listOf(source))
    private val direct = Executor { it.run() }

    @Test
    fun providerShowsEverythingWithoutPrivacyAndAppliesALevelAtOnceWhenChanged() {
        val privacy = StoredSourceContentPrivacy(InMemoryContentLevelStore())
        val provider = workspaceLensProvider(registry, direct, contentPrivacy = privacy)
        val seen = mutableListOf<List<Item>>()
        val handle =
            provider.observe(Lens(sources = listOf(id))) { output ->
                (output.result as? LensResult.Flat)?.let { seen += it.items }
            }
        assertEquals(items, seen.last())

        privacy.setLevel(id, SourceContentLevel.HIDE_BODY)
        assertEquals(listOf("Dinner?", "Invoice"), seen.last().map { it.title })
        assertTrue(seen.last().all { it.body == null })

        privacy.setLevel(id, SourceContentLevel.HIDE_TEXT)
        assertTrue(seen.last().all { it.title == null && it.body == null })
        assertEquals(listOf("a", "b"), seen.last().map { it.id.value })

        handle.cancel()
    }

    @Test
    fun providerWithoutPrivacyKeepsTodaysBehaviour() {
        var out = emptyList<Item>()
        workspaceLensProvider(registry, direct).observe(Lens(sources = listOf(id))) { output ->
            (output.result as? LensResult.Flat)?.let { out = it.items }
        }
        assertEquals(items, out)
    }

    @Test
    fun storedEntriesRoundTripAndMalformedOnesAreDropped() {
        val levels = mapOf("notifications" to "hide_text", "apps.all" to "show")
        assertEquals(levels, decodeContentLevelEntries(encodeContentLevelEntries(levels)))
        assertEquals(
            mapOf("media" to "hide_body"),
            decodeContentLevelEntries(setOf("media=hide_body", "=hide_text", "calendar=", "nonsense")),
        )
    }
}
