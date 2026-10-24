package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.workspace.StoredValue
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Owner decision N10: rotating a phone never changes which placed items are shown. */
class PoolLayoutFoldTest {
    private val phone = HomeLayoutDeviceClass.PHONE
    private val landscape = HomeLayoutDeviceClass.PHONE_LANDSCAPE
    private val tablet = HomeLayoutDeviceClass.TABLET
    private val phonePool = emptyPool(listOf(W1))
    private val landscapePool = emptyPool(listOf(W2))

    @Test
    fun onlyALandscapePoolBecomesThePhonePool() {
        val folded = PoolLayoutFold.fold(PoolStoreState(true, mapOf(landscape to landscapePool, tablet to phonePool)))
        assertEquals(PoolStoreState(true, mapOf(phone to landscapePool, tablet to phonePool)), folded)
        assertSame(folded, PoolLayoutFold.fold(folded))
    }

    @Test
    fun withBothThePhonePoolWins() {
        val folded = PoolLayoutFold.fold(PoolStoreState(false, mapOf(landscape to landscapePool, phone to phonePool)))
        assertEquals(PoolStoreState(false, mapOf(phone to phonePool)), folded)
    }

    @Test
    fun withNeitherNothingChanges() {
        val state = PoolStoreState(true, mapOf(tablet to phonePool))
        assertSame(state, PoolLayoutFold.fold(state))
        assertEquals(PoolStoreState(), PoolLayoutFold.fold(PoolStoreState()))
    }

    @Test
    fun aDecodedOlderStoreFoldsWithoutThrowing() {
        val random = Random(7)
        val encoded = PoolStoreCodec.encode(PoolStoreState(true, mapOf(landscape to landscapePool, phone to phonePool)))
        val layouts = (encoded.fields.getValue("layouts") as StoredValue.Arr).items
        val junk = layouts + listOf(StoredValue.Num(3), StoredValue.Obj(emptyMap()), StoredValue.Str("PHONE_LANDSCAPE"))
        repeat(200) {
            val shuffled = List(random.nextInt(0, 5)) { junk.random(random) }
            val value = StoredValue.Obj(encoded.fields + ("layouts" to StoredValue.Arr(shuffled)))
            val state = PoolStoreCodec.decode(value).state
            assertNotNull(state)
            val folded = PoolLayoutFold.fold(state)
            assertFalse(landscape in folded.pools)
            if (phone in state.pools) assertEquals(state.pools[phone], folded.pools[phone])
        }
    }

    @Test
    fun theMigrationFromHomeLayoutSetCreatesNoLandscapePool() {
        val key = HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER, landscape)
        val layoutSet = HomeLayoutSet(activeKey = key, layouts = mapOf(key to HomeLayoutDefaults.standard(landscape)))
        val migrated = PoolCutover.ensureMigrated(null, layoutSet).state
        assertTrue(migrated.migrated)
        assertEquals(setOf(phone), migrated.pools.keys)
    }
}
