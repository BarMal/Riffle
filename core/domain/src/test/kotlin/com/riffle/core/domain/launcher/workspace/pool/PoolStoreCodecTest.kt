package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.StoredValue
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PoolStoreCodecTest {
    private val migrated = PoolCutover.ensureMigrated(null, cutoverLayoutSet()).state

    @Test
    fun roundTripsAMigratedState() {
        val decoded = PoolStoreCodec.decode(PoolStoreCodec.encode(migrated))
        assertEquals(migrated, decoded.state)
        assertEquals(emptyList(), decoded.issues)
    }

    @Test
    fun theFlagSurvivesAnEmptyPoolSet() {
        val state = PoolStoreState(migrated = true)
        assertEquals(state, PoolStoreCodec.decode(PoolStoreCodec.encode(state)).state)
    }

    @Test
    fun nothingOrTheWrongShapeReadsAsNotStored() {
        listOf(null, StoredValue.Str("x"), StoredValue.Arr(emptyList()), StoredValue.Obj(emptyMap())).forEach {
            assertNull(PoolStoreCodec.decode(it).state, "$it")
        }
    }

    @Test
    fun aFutureVersionReadsAsNotStoredSoItIsRederived() {
        val future =
            PoolStoreCodec.encode(
                migrated,
            ).let { StoredValue.Obj(it.fields + ("version" to StoredValue.Num(99))) }
        assertNull(PoolStoreCodec.decode(future).state)
    }

    @Test
    fun anUnknownDeviceClassOrBrokenPoolIsSkippedWithoutLosingTheRest() {
        val good = PoolStoreCodec.encode(migrated)
        val layouts = (good.fields.getValue("layouts") as StoredValue.Arr).items
        val broken =
            listOf(
                StoredValue.Obj(
                    mapOf("deviceClass" to StoredValue.Str("HOLOGRAM"), "pool" to StoredValue.Obj(emptyMap())),
                ),
                StoredValue.Str("junk"),
                StoredValue.Obj(mapOf("deviceClass" to StoredValue.Str("TABLET"), "pool" to StoredValue.Str("nope"))),
            )
        val value = StoredValue.Obj(good.fields + ("layouts" to StoredValue.Arr(broken + layouts)))
        val decoded = PoolStoreCodec.decode(value).state
        assertNotNull(decoded)
        assertEquals(
            migrated.pools.getValue(HomeLayoutDeviceClass.PHONE),
            decoded.pools.getValue(HomeLayoutDeviceClass.PHONE),
        )
        assertEquals(PlacedItemPool(), decoded.pools[HomeLayoutDeviceClass.TABLET])
    }

    @Test
    fun aDanglingReferenceIsDroppedAndReported() {
        val phone = migrated.pools.getValue(HomeLayoutDeviceClass.PHONE)
        val victim = phone.items.keys.first()
        val state = PoolStoreState(true, mapOf(HomeLayoutDeviceClass.PHONE to phone.copy(items = phone.items - victim)))
        val decoded = PoolStoreCodec.decode(PoolStoreCodec.encode(state))
        assertTrue(decoded.issues.any { it.kind == PoolIssueKind.DANGLING_REFERENCE })
        decoded.state!!.pools.values.forEach(::assertInvariants)
    }

    @Test
    fun fuzzedValuesAlwaysDecodeToValidPoolsOrNothing() {
        val encoded = PoolStoreCodec.encode(migrated)
        repeat(300) { seed ->
            val mutated = mutate(encoded, Random(seed)) as? StoredValue
            PoolStoreCodec.decode(mutated).state?.pools?.values?.forEach(::assertInvariants)
        }
    }

    @Test
    fun encodedStateHoldsOnlyLauncherDataNeverNotificationText() {
        val text = PoolStoreCodec.encode(migrated).toString()
        assertTrue("notification" !in text.lowercase())
    }

    private fun mutate(
        value: StoredValue,
        random: Random,
    ): StoredValue? =
        when {
            random.nextInt(25) == 0 -> null
            random.nextInt(40) == 0 -> StoredValue.Str("x")
            random.nextInt(40) == 0 -> StoredValue.Num(random.nextLong())
            value is StoredValue.Obj ->
                StoredValue.Obj(
                    value.fields.mapNotNull { (k, v) -> mutate(v, random)?.let { k to it } }.toMap(),
                )
            value is StoredValue.Arr -> StoredValue.Arr(value.items.mapNotNull { mutate(it, random) })
            else -> value
        }
}
