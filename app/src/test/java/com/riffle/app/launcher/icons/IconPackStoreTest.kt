package com.riffle.app.launcher.icons

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.compose.runtime.snapshots.Snapshot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IconPackStoreTest {
    private val pack = "com.example.pack.one"
    private val notes = identity("com.example.notes")
    private val filter =
        """<resources><item component="ComponentInfo{com.example.notes/com.example.notes.Main}" drawable="notes"/>
        <item drawable="a"/><item drawable="b"/></resources>"""

    @After
    fun applyPendingSnapshotWrites() = Snapshot.sendApplyNotifications()

    private fun source(version: Long = 1L) =
        FakeIconPackSource(
            mapOf(
                pack to
                    FakeIconPackSource.FakePack(
                        "One",
                        version = version,
                        appFilter = filter,
                        drawables = mapOf("notes" to ColorDrawable(Color.RED)),
                    ),
            ),
        )

    @Test
    fun aPackIsReadOncePerVersion() {
        val source = source()
        val store = IconPackStore(source)

        store.watch(setOf(pack), pack)
        store.watch(setOf(pack), pack)
        store.onCatalogChanged(pack)

        assertEquals(1, source.appFilterReads)
        assertEquals("notes", store.drawableFor(pack, notes))
        assertTrue(store.isAvailable(pack))
    }

    @Test
    fun anUpdatedPackIsReadAgainAndTheCacheKeyChanges() {
        val source = source(version = 1L)
        val store = IconPackStore(source)
        store.watch(setOf(pack), pack)
        val before = store.versionOf(pack)

        source.packs = source(version = 2L).packs
        store.onCatalogChanged(pack)

        assertEquals(2, source.appFilterReads)
        assertNotEquals(before, store.versionOf(pack))
    }

    @Test
    fun aRemovedPackFallsBackAtOnce() {
        val source = source()
        val store = IconPackStore(source)
        store.watch(setOf(pack), pack)
        val catalog = store.catalogVersion

        source.packs = emptyMap()
        store.onCatalogChanged(pack)

        assertFalse(store.isAvailable(pack))
        assertNull(store.drawableFor(pack, notes))
        assertEquals(0L, store.versionOf(pack))
        assertNotEquals(catalog, store.catalogVersion)
    }

    @Test
    fun thePickersListIsTheDrawableListElseWhatTheAppfilterNames() {
        val withList =
            FakeIconPackSource(
                mapOf(
                    pack to
                        FakeIconPackSource.FakePack(
                            "One",
                            appFilter = filter,
                            drawableXml = """<resources><item drawable="x"/><item drawable="y"/></resources>""",
                        ),
                ),
            )
        val store = IconPackStore(withList)
        store.watch(setOf(pack), pack)
        assertEquals(listOf("x", "y"), store.drawableNames(pack))

        val withoutList = IconPackStore(source())
        withoutList.watch(setOf(pack), pack)
        assertEquals(listOf("notes", "a", "b"), withoutList.drawableNames(pack))
    }

    @Test
    fun aPackWithBrokenFilesReadsAsEmptyAndNeverThrows() {
        val broken =
            FakeIconPackSource(mapOf(pack to FakeIconPackSource.FakePack("Broken", appFilter = "<<<not xml")))
        val store = IconPackStore(broken)

        store.watch(setOf(pack), pack)

        assertTrue(store.isAvailable(pack))
        assertNull(store.drawableFor(pack, notes))
        assertEquals(emptyList<String>(), store.drawableNames(pack))
    }

    @Test
    fun aMissingDrawableIsNull() {
        val store = IconPackStore(source())
        store.watch(setOf(pack), pack)

        assertNull(store.drawable(pack, "missing"))
        assertEquals(listOf(InstalledIconPack(pack, "One")), store.installedPacks())
    }
}
