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
 * What the pool store holds: a pool per device class, whether the one-time import of placed items from
 * `HomeLayoutSet` has run ([migrated]), and whether a one-time import of the docks is still owed
 * ([dockImportPending]). The flags are separate because a store migrated before the dock moved into the pool (schema 1)
 * must get its docks imported once without its (possibly edited) arrangements being re-imported. Only decoding a
 * schema 1 value sets [dockImportPending]; a state built in code (a migration, a restore) already carries its docks.
 * Held in its own store, apart from the
 * workspace set blob, so resetting or restoring workspaces cannot lose placed items. Item content (notification text
 * and the like) is never part of it.
 */
data class PoolStoreState(
    val migrated: Boolean = false,
    val pools: Map<HomeLayoutDeviceClass, PlacedItemPool> = emptyMap(),
    val dockImportPending: Boolean = false,
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
 *
 * Version 2 adds the per-layout `dock` (inside each pool) and the `dockImported` flag. A version 1 value that was
 * migrated decodes with `dockImportPending = true`, so `PoolCutover.ensureMigrated` imports its docks once.
 */
object PoolStoreCodec {
    const val VERSION = 2

    fun encode(state: PoolStoreState): StoredValue.Obj =
        obj(
            "version" to num(VERSION),
            "migrated" to StoredValue.Bool(state.migrated),
            "dockImported" to StoredValue.Bool(!state.dockImportPending),
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
        val migrated = root.bool("migrated") ?: false
        val dockImported = version >= VERSION && root.bool("dockImported") != false
        return PoolStoreDecodeResult(PoolStoreState(migrated, pools, migrated && !dockImported), issues)
    }
}
