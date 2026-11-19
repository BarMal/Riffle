package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame

class LegacyDockPairsTest {
    private fun entry(pkg: String) = FolderEntry("e-$pkg", appIdentity(pkg), pkg)

    private fun pair(
        id: String,
        first: String,
        second: String,
    ) = PoolAppPair(PoolItemId(id), entry(first), entry(second))

    private fun dockPool(vararg pins: PoolItem): PlacedItemPool =
        emptyPool().copy(items = pins.associateBy { it.id }, dock = PoolDock(pins = pins.map { it.id }))

    private val stored =
        PoolStoreState(
            migrated = true,
            pools =
                mapOf(
                    HomeLayoutDeviceClass.PHONE to
                        dockPool(poolApp("a"), pair("p", "notes", "maps"), poolApp("b"), pair("q", "maps", "notes")),
                ),
        )

    @Test
    fun everyPairBecomesALayoutInDockOrderAndLeavesTheDock() {
        val extraction = LegacyDockPairs.extract(stored)

        assertEquals(listOf("p", "q"), extraction.layouts.map { it.id })
        assertEquals(
            listOf("notes" to "maps", "maps" to "notes"),
            extraction.layouts.map { it.first.packageName.value to it.second.packageName.value },
        )
        val pool = extraction.state.pools.getValue(HomeLayoutDeviceClass.PHONE)
        assertEquals(listOf("a", "b"), pool.dock?.pins?.map { it.value })
        assertEquals(setOf("a", "b"), pool.items.keys.map { it.value }.toSet())
    }

    @Test
    fun migratingTwiceChangesNothingTheSecondTime() {
        val first = LegacyDockPairs.extract(stored)
        val second = LegacyDockPairs.extract(first.state)

        assertEquals(emptyList(), second.layouts)
        assertFalse(second.changed)
        assertSame(first.state, second.state)
    }

    @Test
    fun aPoolStoredByTheOldCodeStillDecodesAndThenMigrates() {
        val old = PoolStoreCodec.decode(PoolStoreCodec.encode(stored)).state!!

        val extraction = LegacyDockPairs.extract(old)

        assertEquals(listOf("p", "q"), extraction.layouts.map { it.id })
        assertEquals(
            0,
            extraction.state.pools.values.sumOf { pool -> pool.items.values.count { it is PoolAppPair } },
        )
    }

    @Test
    fun stateWithoutPairsIsReturnedAsIs() {
        val plain = PoolStoreState(true, mapOf(HomeLayoutDeviceClass.PHONE to dockPool(poolApp("a"))))

        val extraction = LegacyDockPairs.extract(plain)

        assertSame(plain, extraction.state)
        assertEquals(emptyList(), extraction.layouts)
    }

    @Test
    fun theSamePairOnTwoDevicesDocksIsOneLayout() {
        val twoDocks =
            PoolStoreState(
                true,
                mapOf(
                    HomeLayoutDeviceClass.PHONE to dockPool(pair("p", "notes", "maps")),
                    HomeLayoutDeviceClass.FOLDABLE to dockPool(pair("p2", "notes", "maps")),
                ),
            )

        assertEquals(1, LegacyDockPairs.extract(twoDocks).layouts.size)
    }
}
