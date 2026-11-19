package com.riffle.app.launcher.stream

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.stream.NowOptions
import com.riffle.core.domain.launcher.stream.StreamLayout
import com.riffle.core.domain.launcher.stream.StreamLayoutAction
import com.riffle.core.domain.launcher.stream.StreamLayoutReducer
import com.riffle.core.domain.launcher.stream.StreamLayouts
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamSectionOptions
import com.riffle.core.domain.launcher.stream.StreamView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamLayoutRepositoryTest {
    private class FakeStore(var stored: StreamLayouts? = null, var failRead: Boolean = false) : StreamLayoutStorePort {
        val writes = mutableListOf<StreamLayouts>()

        override suspend fun read(): StreamLayouts? = if (failRead) error("disk") else stored

        override suspend fun write(layouts: StreamLayouts) {
            writes += layouts
            stored = layouts
        }
    }

    private val phone = HomeLayoutDeviceClass.PHONE

    private fun repository(store: FakeStore) =
        CachedStreamLayoutRepository(
            store,
            CoroutineScope(Dispatchers.Unconfined + Job()),
        )

    @Test
    fun drawsTheDefaultBeforeAnythingIsLoaded() {
        assertEquals(StreamLayout.DEFAULT, repository(FakeStore()).layoutFor(phone))
    }

    @Test
    fun loadsWhatIsStored() =
        runBlocking {
            val custom =
                StreamLayouts().with(
                    phone,
                    StreamLayout.DEFAULT.copy(options = StreamSectionOptions(now = NowOptions(maxActive = 3))),
                )
            val repository = repository(FakeStore(custom))
            repository.initialize()
            assertEquals(3, repository.layoutFor(phone).options.now.maxActive)
        }

    @Test
    fun anEditIsHeldAtOnceAndWrittenToTheStore() =
        runBlocking {
            val store = FakeStore()
            val repository = repository(store)
            repository.initialize()
            val before = repository.update(phone, StreamLayoutAction.SetView(StreamSection.NOW, StreamView.LIST))

            assertEquals(StreamLayout.DEFAULT, before)
            assertEquals(StreamView.LIST, repository.layoutFor(phone).viewOf(StreamSection.NOW))
            assertEquals(StreamView.LIST, store.stored!!.forPosture(phone).viewOf(StreamSection.NOW))
        }

    @Test
    fun anEditBeforeTheFirstReadIsMergedWithWhatIsStoredNotLostOrOverwritten() =
        runBlocking {
            val tablet = StreamLayout.DEFAULT.copy(options = StreamSectionOptions(now = NowOptions(maxActive = 3)))
            val store = FakeStore(StreamLayouts().with(HomeLayoutDeviceClass.TABLET, tablet))
            val repository = repository(store)

            repository.update(phone, StreamLayoutAction.SetView(StreamSection.NOW, StreamView.LIST))
            assertTrue(store.writes.isEmpty())
            repository.initialize()

            assertEquals(StreamView.LIST, repository.layoutFor(phone).viewOf(StreamSection.NOW))
            assertEquals(3, repository.layoutFor(HomeLayoutDeviceClass.TABLET).options.now.maxActive)
            assertEquals(repository.layouts.value, store.stored)
        }

    @Test
    fun undoPutsTheEarlierLayoutBack() {
        val repository = repository(FakeStore())
        val before = repository.update(phone, StreamLayoutAction.SetView(StreamSection.NOW, StreamView.LIST))
        repository.update(phone, StreamLayoutAction.Restore(before))
        assertEquals(StreamLayout.DEFAULT, repository.layoutFor(phone))
    }

    @Test
    fun posturesAreIndependent() {
        val repository = repository(FakeStore())
        repository.update(HomeLayoutDeviceClass.TABLET, StreamLayoutAction.SetEnabled(StreamSection.FEEDS, false))
        assertTrue(repository.layoutFor(phone).layoutOf(StreamSection.FEEDS).enabled)
        assertFalse(repository.layoutFor(HomeLayoutDeviceClass.TABLET).layoutOf(StreamSection.FEEDS).enabled)
    }

    @Test
    fun onlyAnOptionChangeTellsTheProviderToReEvaluate() {
        val repository = repository(FakeStore())
        var told = 0
        repository.observe { told++ }

        repository.update(phone, StreamLayoutAction.SetView(StreamSection.NOW, StreamView.LIST))
        assertEquals(0, told)
        repository.update(phone, StreamLayoutAction.SetOptions(StreamSectionOptions(now = NowOptions(maxActive = 2))))
        assertEquals(1, told)
    }

    @Test
    fun aNoOpEditWritesNothing() =
        runBlocking {
            val store = FakeStore()
            val repository = repository(store)
            repository.initialize()
            repository.update(
                phone,
                StreamLayoutAction.SetView(StreamSection.NOW, StreamLayout.defaultViewOf(StreamSection.NOW)),
            )
            assertTrue(store.writes.isEmpty())
        }

    @Test
    fun aFailingReadNeverOverwritesWhatIsOnDisk() =
        runBlocking {
            val onDisk = StreamLayouts().with(phone, StreamLayout.DEFAULT)
            val store = FakeStore(onDisk, failRead = true)
            val repository = repository(store)
            repository.initialize()
            repository.update(phone, StreamLayoutAction.SetView(StreamSection.NOW, StreamView.LIST))
            assertTrue(store.writes.isEmpty())
            assertEquals(StreamView.LIST, repository.layoutFor(phone).viewOf(StreamSection.NOW))
        }

    @Test
    fun aRestoreFromBackupReplacesTheHeldLayoutsAndTheStore() =
        runBlocking {
            val store = FakeStore()
            val repository = repository(store)
            val restored =
                StreamLayouts().with(
                    phone,
                    StreamLayout.DEFAULT.copy(options = StreamSectionOptions(now = NowOptions(maxActive = 4))),
                )
            var told = 0
            repository.observe { told++ }

            repository.replaceFromBackup(restored)

            assertEquals(restored, repository.layouts.value)
            assertEquals(restored, store.stored)
            assertEquals(1, told)
        }

    @Test
    fun theBackupFlowReadsTheStoreWhenNothingWasLoaded() =
        runBlocking {
            val stored = StreamLayouts().with(phone, StreamLayout.DEFAULT)
            assertEquals(stored, repository(FakeStore(stored)).currentOrStored())
            assertNull(repository(FakeStore(null)).currentOrStored())
        }

    private fun StreamLayout.then(action: StreamLayoutAction) = StreamLayoutReducer.reduce(this, action)

    private val custom =
        StreamLayouts()
            .with(
                phone,
                StreamLayout.DEFAULT
                    .then(StreamLayoutAction.SetEnabled(StreamSection.RECENTS, false))
                    .then(StreamLayoutAction.Move(StreamSection.FEEDS, -2))
                    .then(StreamLayoutAction.SetView(StreamSection.NOW, StreamView.LIST)),
            )
            .with(
                HomeLayoutDeviceClass.TABLET,
                StreamLayout.DEFAULT.copy(options = StreamSectionOptions(now = NowOptions(showUpNext = false))),
            )

    @Test
    fun resetAllPutsEveryPostureBackToTheDefaultAndWritesIt() =
        runBlocking {
            val store = FakeStore(custom)
            val repository = repository(store)
            var told = 0
            repository.observe { told++ }

            val before = repository.resetAll()

            assertEquals(custom, before)
            assertEquals(StreamLayout.DEFAULT, repository.layoutFor(phone))
            assertEquals(StreamLayout.DEFAULT, repository.layoutFor(HomeLayoutDeviceClass.TABLET))
            assertEquals(StreamLayout.DEFAULT, store.stored!!.forPosture(phone))
            assertTrue(store.stored!!.isDefault)
            assertTrue("observers hear the options change", told > 0)
        }

    @Test
    fun restoreAllPutsThePreviousLayoutsBackExactly() =
        runBlocking {
            val store = FakeStore(custom)
            val repository = repository(store)
            val before = repository.resetAll()

            repository.restoreAll(before)

            assertEquals(custom, repository.layouts.value)
            assertEquals(custom, store.stored)
            assertEquals(false, repository.layoutFor(phone).layoutOf(StreamSection.RECENTS).enabled)
        }

    @Test
    fun resetAllBeforeTheFirstReadStillResetsWhatWasStored() =
        runBlocking {
            val store = FakeStore(custom)
            val repository = repository(store)

            repository.resetAll()
            repository.initialize()

            assertEquals(StreamLayout.DEFAULT, repository.layoutFor(phone))
            assertTrue(store.stored!!.isDefault)
        }
}
