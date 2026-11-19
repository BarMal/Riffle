package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.StoredValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LegacyDockPairsTest {
    private fun str(value: String) = StoredValue.Str(value)

    private fun identity(pkg: String) =
        StoredValue.Obj(
            mapOf(
                "package" to str(pkg),
                "activity" to str("$pkg.Main"),
                "profile" to str("personal"),
                "profileType" to str("PERSONAL"),
            ),
        )

    private fun entry(pkg: String) =
        StoredValue.Obj(mapOf("id" to str("e-$pkg"), "label" to str(pkg), "app" to identity(pkg)))

    /** What an older build wrote for a pair (kind "pair"), as stored. */
    private fun oldPair(
        id: String,
        first: String,
        second: String,
    ) = StoredValue.Obj(
        mapOf(
            "id" to str(id),
            "kind" to str("pair"),
            "label" to str("$first and $second"),
            "first" to entry(first),
            "second" to entry(second),
        ),
    )

    private val plain =
        PoolStoreState(
            migrated = true,
            pools =
                mapOf(
                    HomeLayoutDeviceClass.PHONE to
                        emptyPool().copy(
                            items = listOf(poolApp("a"), poolApp("b")).associateBy { it.id },
                            dock = PoolDock(pins = listOf(PoolItemId("a"), PoolItemId("b"))),
                        ),
                ),
        )

    /** [plain] as an older build stored it, with [pairs] pinned among its two apps: a, the pairs, b. */
    private fun storedWithPairs(vararg pairs: StoredValue.Obj): StoredValue.Obj {
        val encoded = PoolStoreCodec.encode(plain)
        val layouts =
            (encoded.fields.getValue("layouts") as StoredValue.Arr).items.map { layout ->
                val pool = (layout as StoredValue.Obj).fields.getValue("pool") as StoredValue.Obj
                val items = (pool.fields.getValue("items") as StoredValue.Arr).items
                val dock = pool.fields.getValue("dock") as StoredValue.Obj
                val pins = (dock.fields.getValue("pins") as StoredValue.Arr).items
                val ids = pairs.map { it.fields.getValue("id") }
                val withPins =
                    dock.copy(
                        fields = dock.fields + ("pins" to StoredValue.Arr(listOf(pins.first()) + ids + pins.drop(1))),
                    )
                val withItems =
                    pool.copy(
                        fields = pool.fields + ("items" to StoredValue.Arr(items + pairs)) + ("dock" to withPins),
                    )
                layout.copy(fields = layout.fields + ("pool" to withItems))
            }
        return encoded.copy(fields = encoded.fields + ("layouts" to StoredValue.Arr(layouts)))
    }

    private val old = storedWithPairs(oldPair("p", "notes", "maps"), oldPair("q", "maps", "notes"))

    @Test
    fun aPoolStoredByTheOldCodeStillDecodesWithItsPairsKeptApart() {
        val decoded = PoolStoreCodec.decode(old)
        val pool = decoded.state!!.pools.getValue(HomeLayoutDeviceClass.PHONE)

        assertEquals(listOf("p", "q"), pool.legacyPairs.map { it.id.value })
        assertEquals(setOf("a", "b"), pool.items.keys.map { it.value }.toSet())
        assertEquals(listOf("a", "b"), pool.dock?.pins?.map { it.value })
        assertEquals(listOf("notes", "maps"), pool.legacyPairs.first().let { listOf(it.first.label, it.second.label) })
    }

    @Test
    fun everyPairBecomesALayoutInDockOrderAndLeavesThePool() {
        val extraction = LegacyDockPairs.extract(PoolStoreCodec.decode(old).state!!)

        assertEquals(listOf("p", "q"), extraction.layouts.map { it.id })
        assertEquals(
            listOf("notes" to "maps", "maps" to "notes"),
            extraction.layouts.map { it.first.packageName.value to it.second.packageName.value },
        )
        assertTrue(extraction.state.pools.values.all { it.legacyPairs.isEmpty() })
    }

    @Test
    fun nothingWritesAPairAnyMore() {
        val rewritten = PoolStoreCodec.encode(LegacyDockPairs.extract(PoolStoreCodec.decode(old).state!!).state)

        assertEquals(PoolStoreCodec.encode(plain), rewritten)
    }

    @Test
    fun migratingTwiceChangesNothingTheSecondTime() {
        val first = LegacyDockPairs.extract(PoolStoreCodec.decode(old).state!!)
        val second = LegacyDockPairs.extract(first.state)

        assertEquals(emptyList(), second.layouts)
        assertFalse(second.changed)
        assertSame(first.state, second.state)
    }

    @Test
    fun oldDataWithNoPairsDecodesUnchanged() {
        val decoded = PoolStoreCodec.decode(PoolStoreCodec.encode(plain)).state!!

        assertEquals(plain, decoded)
        assertEquals(emptyList(), LegacyDockPairs.extract(decoded).layouts)
    }

    @Test
    fun aPairWithAnUnreadableSideIsSkippedWithoutFailingTheRest() {
        val broken = oldPair("p", "notes", "maps").let { it.copy(fields = it.fields - "second") }
        val decoded = PoolStoreCodec.decode(storedWithPairs(broken, oldPair("q", "mail", "chat"))).state!!

        assertEquals(listOf("q"), decoded.pools.getValue(HomeLayoutDeviceClass.PHONE).legacyPairs.map { it.id.value })
        assertEquals(listOf("a", "b"), decoded.pools.getValue(HomeLayoutDeviceClass.PHONE).dock?.pins?.map { it.value })
    }

    @Test
    fun anUnknownFutureKindIsStillSkipped() {
        val future = StoredValue.Obj(mapOf("id" to str("x"), "kind" to str("triple")))
        val decoded = PoolStoreCodec.decode(storedWithPairs(future)).state!!

        assertEquals(
            setOf("a", "b"),
            decoded.pools.getValue(HomeLayoutDeviceClass.PHONE).items.keys.map { it.value }.toSet(),
        )
    }

    @Test
    fun theSamePairOnTwoDevicesDocksIsOneLayout() {
        val pair =
            LegacyAppPair(
                PoolItemId("p"),
                FolderEntry("e", appIdentity("notes"), "notes"),
                FolderEntry("f", appIdentity("maps"), "maps"),
            )
        val twice =
            PoolStoreState(
                true,
                mapOf(
                    HomeLayoutDeviceClass.PHONE to emptyPool().copy(legacyPairs = listOf(pair)),
                    HomeLayoutDeviceClass.FOLDABLE to
                        emptyPool().copy(legacyPairs = listOf(pair.copy(id = PoolItemId("p2")))),
                ),
            )

        assertEquals(1, LegacyDockPairs.extract(twice).layouts.size)
    }
}
