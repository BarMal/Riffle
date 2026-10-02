package com.riffle.app.launcher.ics

import com.riffle.core.domain.launcher.workspace.sources.ics.IcsFeedId
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsFeedSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CachedIcsFeedRepositoryTest {
    private val id = IcsFeedId("feed-1")

    @Test
    fun loadsStoredFeedsAndDropsCacheOfUnknownFeeds() {
        val cache =
            mapOf(
                id to CachedIcsFeed(listOf(icsEvent("a")), 10L),
                IcsFeedId("gone") to CachedIcsFeed(listOf(icsEvent("b")), 10L),
            )
        val repository =
            loadedRepository(
                FakeIcsStore(
                    IcsStoredText(
                        IcsJsonCodecs.encodeSettings(settingsWithOne()),
                        IcsJsonCodecs.encodeCache(cache),
                    ),
                ),
            )
        assertTrue(repository.isLoaded)
        assertEquals(1, repository.settings().feeds.size)
        assertNotNull(repository.cached(id))
        assertNull(repository.cached(IcsFeedId("gone")))
    }

    @Test
    fun emptyStoreIsLoadedWithNoFeeds() {
        val repository = CachedIcsFeedRepository(FakeIcsStore(), inlineScope())
        assertTrue(repository.isLoaded)
        assertEquals(IcsFeedSettings(), repository.settings())
    }

    @Test
    fun editsBeforeTheLoadFinishesAreRefused() {
        val gate =
            object : IcsStorePort {
                override suspend fun read(): IcsStoredText = kotlinx.coroutines.awaitCancellation()

                override suspend fun writeSettings(json: String) = Unit

                override suspend fun writeCache(json: String) = Unit
            }
        val repository = CachedIcsFeedRepository(gate, inlineScope())
        assertFalse(repository.isLoaded)
        assertNull(repository.updateSettings { it.withAdded("n", SECRET_URL) })
        assertEquals(emptyList<Any>(), repository.settings().feeds)
    }

    @Test
    fun anEditIsPersistedAndAnnounced() {
        val store = FakeIcsStore()
        val repository = CachedIcsFeedRepository(store, inlineScope())
        var changes = 0
        repository.observe { changes++ }

        val next = repository.updateSettings { it.withAdded("Work", SECRET_URL) { "feed-1" } }

        assertNotNull(next)
        assertEquals(1, store.settingsWrites)
        assertEquals(1, changes)
        assertEquals(next, IcsJsonCodecs.decodeSettings(store.text.settings!!))
    }

    @Test
    fun aNoOpEditWritesNothing() {
        val store = FakeIcsStore(IcsStoredText(IcsJsonCodecs.encodeSettings(settingsWithOne()), null))
        val repository = CachedIcsFeedRepository(store, inlineScope())
        assertNull(repository.updateSettings { it })
        assertEquals(0, store.settingsWrites)
    }

    @Test
    fun cacheReplaceAndClearArePersistedSeparately() {
        val store = FakeIcsStore(IcsStoredText(IcsJsonCodecs.encodeSettings(settingsWithOne()), null))
        val repository = CachedIcsFeedRepository(store, inlineScope())
        repository.replaceCache(id, CachedIcsFeed(listOf(icsEvent("a")), 5L))
        assertEquals(1, store.cacheWrites)
        assertEquals(0, store.settingsWrites)
        assertEquals(5L, repository.cached(id)?.fetchedAtEpochMillis)

        repository.clearCache(id)
        assertNull(repository.cached(id))
        assertEquals(2, store.cacheWrites)
        repository.clearCache(id)
        assertEquals(2, store.cacheWrites)
    }

    @Test
    fun aFailedReadNeverOverwritesWhatIsOnDisk() {
        val stored = IcsStoredText(IcsJsonCodecs.encodeSettings(settingsWithOne()), null)
        val store = FakeIcsStore(stored, failRead = true)
        val repository = CachedIcsFeedRepository(store, inlineScope())
        repository.updateSettings { it.withAdded("Other", "https://other.example.com/a.ics") }
        repository.replaceCache(id, CachedIcsFeed(emptyList(), 1L))
        assertEquals(0, store.settingsWrites)
        assertEquals(0, store.cacheWrites)
        assertEquals(stored.settings, store.text.settings)
    }

    @Test
    fun anUnreadableStoredDocumentReadsAsNoFeeds() {
        val repository = CachedIcsFeedRepository(FakeIcsStore(IcsStoredText("garbage", "garbage")), inlineScope())
        assertTrue(repository.isLoaded)
        assertEquals(IcsFeedSettings(), repository.settings())
    }
}
