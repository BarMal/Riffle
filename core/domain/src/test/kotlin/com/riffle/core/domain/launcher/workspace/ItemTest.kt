package com.riffle.core.domain.launcher.workspace

import com.riffle.core.domain.launcher.workspace.testing.FakeItemSource
import com.riffle.core.domain.launcher.workspace.testing.FakeSourceRegistry
import com.riffle.core.domain.launcher.workspace.testing.fakeItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ItemTest {
    @Test
    fun identityValuesRejectBlanks() {
        assertFailsWith<IllegalArgumentException> { ItemId(" ") }
        assertFailsWith<IllegalArgumentException> { SourceId("") }
        assertFailsWith<IllegalArgumentException> { ItemImageHandle("") }
        assertFailsWith<IllegalArgumentException> { Lens(sources = emptyList()) }
        assertFailsWith<IllegalArgumentException> { Lens(sources = listOf(SourceId("a")), limit = 0) }
    }

    @Test
    fun extKeysMustBeNamespaced() {
        ItemExtKey("media.artist")
        assertFailsWith<IllegalArgumentException> { ItemExtKey("artist") }
        assertFailsWith<IllegalArgumentException> { ItemExtKey("Media.Artist") }
    }

    @Test
    fun sensitiveFieldsExcludeIdentityAndLayoutFields() {
        assert(ItemField.ICON !in SENSITIVE_ITEM_FIELDS)
        assert(ItemField.GROUP !in SENSITIVE_ITEM_FIELDS)
        assert(ItemField.TITLE in SENSITIVE_ITEM_FIELDS)
    }

    @Test
    fun fakeSourceReplaysLatestStateAndSharesUpstream() {
        val source = FakeItemSource(SourceDescriptor(SourceId("fake"), setOf(SourceCapability.LIVE)))
        source.emitItems(listOf(fakeItem("a")))

        val seen = mutableListOf<SourceState>()
        val sub = source.subscribe { seen += it }
        assertEquals(SourceState.Ready(listOf(fakeItem("a"))), seen.single())

        source.emit(SourceState.PermissionRequired)
        assertEquals(SourceState.PermissionRequired, seen.last())

        sub.cancel()
        source.emit(SourceState.Unavailable)
        assertEquals(2, seen.size)
        assertEquals(0, source.observerCount)
    }

    @Test
    fun fakeRegistryResolvesSources() {
        val source = FakeItemSource(SourceDescriptor(SourceId("fake")))
        val registry = FakeSourceRegistry(listOf(source))
        assertNotNull(registry.source(SourceId("fake")))
        assertNull(registry.source(SourceId("other")))
        assertEquals(listOf(source.descriptor), registry.descriptors())
    }
}
