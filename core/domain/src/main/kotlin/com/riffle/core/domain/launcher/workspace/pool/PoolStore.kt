package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.StoredValue
import com.riffle.core.domain.launcher.workspace.arr
import com.riffle.core.domain.launcher.workspace.array
import com.riffle.core.domain.launcher.workspace.bool
import com.riffle.core.domain.launcher.workspace.enumOrNull
import com.riffle.core.domain.launcher.workspace.guarded
import com.riffle.core.domain.launcher.workspace.long
import com.riffle.core.domain.launcher.workspace.num
import com.riffle.core.domain.launcher.workspace.obj
import com.riffle.core.domain.launcher.workspace.str
import com.riffle.core.domain.launcher.workspace.string

/**
 * What the pool store holds: a pool per device class and whether the one-time import from `HomeLayoutSet` has
 * run ([migrated]). Held in its own store, apart from the workspace set blob, so resetting or restoring
 * workspaces cannot lose placed items. Item content (notification text and the like) is never part of it.
 */
data class PoolStoreState(
    val migrated: Boolean = false,
    val pools: Map<HomeLayoutDeviceClass, PlacedItemPool> = emptyMap(),
) {
    fun poolFor(deviceClass: HomeLayoutDeviceClass): PlacedItemPool? = pools[deviceClass]
}

data class PoolStoreDecodeResult(
    val state: PoolStoreState?,
    /** What decoding dropped, by kind and id only. */
    val issues: List<PoolIssue>,
)

/**
 * Codec of [PoolStoreState] over [StoredValue]. Decoding never throws: anything that is not an object, or
 * carries a future schema version, reads as null (nothing stored, so the migration runs again and the data
 * is re-derived from `HomeLayoutSet`, which is never modified); an undecodable device class entry is
 * skipped; each pool goes through [PoolCodec.decode], which repairs it.
 */
object PoolStoreCodec {
    const val VERSION = 1

    fun encode(state: PoolStoreState): StoredValue.Obj =
        obj(
            "version" to num(VERSION),
            "migrated" to StoredValue.Bool(state.migrated),
            "layouts" to
                arr(
                    state.pools.entries.sortedBy { it.key.ordinal }.map { (deviceClass, pool) ->
                        obj("deviceClass" to str(deviceClass.name), "pool" to PoolCodec.encode(pool))
                    },
                ),
        )

    fun decode(value: StoredValue?): PoolStoreDecodeResult {
        val root = value as? StoredValue.Obj
        val version = root?.long("version")
        if (root == null || version == null || version > VERSION) return PoolStoreDecodeResult(null, emptyList())
        val issues = mutableListOf<PoolIssue>()
        val pools = LinkedHashMap<HomeLayoutDeviceClass, PlacedItemPool>()
        root.array("layouts").forEach { entry ->
            val layout = entry as? StoredValue.Obj
            val deviceClass = layout?.let { enumOrNull<HomeLayoutDeviceClass>(it.string("deviceClass")) }
            if (layout != null && deviceClass != null && deviceClass !in pools) {
                val decoded = guarded { PoolCodec.decode(layout.obj("pool")) }
                if (decoded != null) {
                    issues += decoded.issues
                    pools[deviceClass] = decoded.pool
                }
            }
        }
        return PoolStoreDecodeResult(PoolStoreState(root.bool("migrated") ?: false, pools), issues)
    }
}
