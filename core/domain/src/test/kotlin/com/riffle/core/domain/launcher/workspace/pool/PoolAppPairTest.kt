package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.home.AppPairItem
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockEditResult
import com.riffle.core.domain.launcher.home.DockEngine
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.workspace.StoredValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PoolAppPairTest {
    private fun entry(pkg: String) = FolderEntry("e-$pkg", appIdentity(pkg), pkg)

    private fun pair(
        id: String,
        first: String,
        second: String,
    ) = PoolAppPair(PoolItemId(id), entry(first), entry(second))

    private fun dockPool(vararg pins: PoolItem): PlacedItemPool =
        emptyPool().copy(
            items = pins.associateBy { it.id },
            dock = PoolDock(pins = pins.map { it.id }),
        )

    @Test
    fun aStoredPairRoundTripsWithItsOrder() {
        val pool = dockPool(poolApp("a"), pair("p", "notes", "maps"), pair("q", "maps", "notes"))

        val decoded = PoolCodec.decode(PoolCodec.encode(pool))

        assertEquals(pool, decoded.pool)
        assertEquals(emptyList(), decoded.issues)
        val p = decoded.pool.items.getValue(PoolItemId("p")) as PoolAppPair
        assertEquals(listOf("notes", "maps"), listOf(p.first.label, p.second.label))
    }

    @Test
    fun oldDataWithNoPairsDecodesUnchanged() {
        val old = dockPool(poolApp("a"), poolFolder("f", "x", "y"))

        assertEquals(old, PoolCodec.decode(PoolCodec.encode(old)).pool)
    }

    @Test
    fun anUnknownFutureKindInTheDockIsSkippedWithoutFailingTheRest() {
        val encoded = PoolCodec.encode(dockPool(poolApp("a"), pair("p", "notes", "maps"), poolApp("b")))
        val future =
            StoredValue.Obj(mapOf("id" to StoredValue.Str("x"), "kind" to StoredValue.Str("triple")))
        val items = (encoded.fields.getValue("items") as StoredValue.Arr).items
        val withFuture = encoded.copy(fields = encoded.fields + ("items" to StoredValue.Arr(items + future)))

        val decoded = PoolCodec.decode(withFuture).pool

        assertEquals(setOf("a", "p", "b"), decoded.items.keys.map { it.value }.toSet())
        assertEquals(listOf("a", "p", "b"), decoded.dock?.pins?.map { it.value })
    }

    @Test
    fun aPairWithAnUnreadableMemberIsSkippedAndItsPinDropped() {
        val encoded = PoolCodec.encode(dockPool(poolApp("a"), pair("p", "notes", "maps")))
        val items =
            (encoded.fields.getValue("items") as StoredValue.Arr).items.map { item ->
                val obj = item as StoredValue.Obj
                if ((obj.fields["id"] as? StoredValue.Str)?.value == "p") {
                    StoredValue.Obj(obj.fields - "second")
                } else {
                    item
                }
            }

        val decoded = PoolCodec.decode(encoded.copy(fields = encoded.fields + ("items" to StoredValue.Arr(items))))

        assertEquals(listOf("a"), decoded.pool.dock?.pins?.map { it.value })
    }

    @Test
    fun uninstallingOneAppLeavesTheRemainingAppPinnedInTheSamePlace() {
        val pool = dockPool(poolApp("a"), pair("p", "notes", "maps"), poolApp("b"))

        val edit = PoolRemoval.pruneUninstalled(pool, AppPackageName("notes"), AppProfile.personal())

        assertEquals(listOf("a", "p", "b"), edit.pool.dock?.pins?.map { it.value })
        val left = assertIs<PoolApp>(edit.pool.items.getValue(PoolItemId("p")))
        assertEquals("maps", left.label)
        val second = PoolRemoval.pruneUninstalled(pool, AppPackageName("maps"), AppProfile.personal())
        assertEquals("notes", (second.pool.items.getValue(PoolItemId("p")) as PoolApp).label)
        assertInvariants(edit.pool)
    }

    @Test
    fun uninstallingBothAppsRemovesThePair() {
        val pool = dockPool(poolApp("a"), pair("p", "notes", "notes"))

        val edit = PoolRemoval.pruneUninstalled(pool, AppPackageName("notes"), AppProfile.personal())

        assertEquals(listOf("a"), edit.pool.dock?.pins?.map { it.value })
        assertFalse(PoolItemId("p") in edit.pool.items)
    }

    @Test
    fun theDockProjectsAPairAndAnEditedPairIsStored() {
        val pool = dockPool(poolApp("a"), pair("p", "notes", "maps"))
        val base = HomeLayoutBaseForTests.layout()
        val before = PoolHomeProjection.project(pool, W1, base)
        val shown = before.dock.items[1]
        assertIs<AppPairItem>(shown)
        assertEquals("p", shown.id.value)

        val swapped =
            assertIs<DockEditResult.Updated>(DockEngine().swapDockPair(before, shown.id)).layout
        val edit = PoolHomeProjection.ingest(pool, W1, before, swapped, counterIds())

        val stored = edit.pool.items.getValue(PoolItemId("p")) as PoolAppPair
        assertEquals(listOf("maps", "notes"), listOf(stored.first.label, stored.second.label))
        assertEquals(listOf("a", "p"), edit.pool.dock?.pins?.map { it.value })
    }

    @Test
    fun makingAPairInTheLayoutReplacesTwoPoolItemsWithOne() {
        val pool = dockPool(poolApp("a"), poolApp("b"), poolApp("c"))
        val before = PoolHomeProjection.project(pool, W1, HomeLayoutBaseForTests.layout())

        val paired =
            assertIs<DockEditResult.Updated>(
                DockEngine().pairDockApps(before, LauncherItemId("a"), LauncherItemId("c")),
            ).layout
        val edit = PoolHomeProjection.ingest(pool, W1, before, paired, counterIds())

        val pins = edit.pool.dock?.pins.orEmpty()
        assertEquals(2, pins.size)
        val made = edit.pool.items.getValue(pins[0]) as PoolAppPair
        assertEquals(listOf("a", "c"), listOf(made.first.label, made.second.label))
        assertEquals("b", edit.pool.items.getValue(pins[1]).label)
        assertTrue(edit.pool.items.size == 2)
        assertInvariants(edit.pool)
    }

    @Test
    fun anUninstalledAppIsEditedOutOfAPairUnderTheSameId() {
        val pool = dockPool(pair("p", "notes", "maps"))
        val before = PoolHomeProjection.project(pool, W1, HomeLayoutBaseForTests.layout())
        val pair = before.dock.items.single() as AppPairItem
        val remaining: AppShortcutItem = pair.second
        val after = before.copy(dock = before.dock.copy(items = listOf(remaining.copy(id = pair.id))))

        val edit = PoolHomeProjection.ingest(pool, W1, before, after, counterIds())

        assertIs<PoolApp>(edit.pool.items.getValue(PoolItemId("p")))
    }
}

private object HomeLayoutBaseForTests {
    fun layout(): HomeLayout =
        com.riffle.core.domain.launcher.home.HomeLayoutDefaults.standard().copy(dock = DockModel(capacity = 5))
}
