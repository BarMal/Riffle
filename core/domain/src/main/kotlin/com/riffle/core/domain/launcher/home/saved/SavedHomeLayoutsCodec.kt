package com.riffle.core.domain.launcher.home.saved

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.StoredValue
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.arr
import com.riffle.core.domain.launcher.workspace.array
import com.riffle.core.domain.launcher.workspace.enumOrNull
import com.riffle.core.domain.launcher.workspace.guarded
import com.riffle.core.domain.launcher.workspace.long
import com.riffle.core.domain.launcher.workspace.num
import com.riffle.core.domain.launcher.workspace.obj
import com.riffle.core.domain.launcher.workspace.pool.Arrangement
import com.riffle.core.domain.launcher.workspace.pool.PlacedItemPool
import com.riffle.core.domain.launcher.workspace.pool.PoolCodec
import com.riffle.core.domain.launcher.workspace.pool.PoolWidget
import com.riffle.core.domain.launcher.workspace.str
import com.riffle.core.domain.launcher.workspace.string

data class SavedHomeLayoutsDecodeResult(
    val layouts: SavedHomeLayouts,
    /** The value was written by a newer build: what this build understood is returned, the value must be kept. */
    val newerThanThisBuild: Boolean = false,
)

/**
 * Stored form of [SavedHomeLayouts] (ADR 0003, Amendment 17): one versioned document, apart from the Home pool, the
 * layout set and the settings, so no reset or restore of those can lose a saved layout.
 *
 * ```
 * { "version": 1, "layouts": [ { "deviceClass": "PHONE", "items": [
 *     { "id": "...", "name": "...", "kind": "saved" | "before", "savedAt": 123, "pool": <PoolCodec> } ] } ] }
 * ```
 * Each layout's pages and items are one [PlacedItemPool] with a single arrangement, so the pool's own safe decoding
 * (dangling references, collisions and out-of-grid placements dropped) applies and a damaged entry loses only what is
 * damaged. A host id is never written and is dropped if read. Decoding never throws: anything that is not an object
 * reads as nothing stored, an undecodable device class or layout is skipped, and a value with a newer version decodes
 * what this build knows and says so.
 */
object SavedHomeLayoutsCodec {
    const val VERSION = 1
    private val ARRANGEMENT = WorkspaceId("saved")

    fun encode(layouts: SavedHomeLayouts): StoredValue.Obj =
        obj(
            "version" to num(VERSION),
            "layouts" to
                arr(
                    layouts.byDeviceClass.entries.sortedBy { it.key.ordinal }.filter { it.value.isNotEmpty() }.map {
                            (deviceClass, list) ->
                        obj("deviceClass" to str(deviceClass.name), "items" to arr(list.map(::encodeLayout)))
                    },
                ),
        )

    fun decode(value: StoredValue?): SavedHomeLayoutsDecodeResult {
        val root = value as? StoredValue.Obj
        val version = root?.long("version")
        if (root == null || version == null) return SavedHomeLayoutsDecodeResult(SavedHomeLayouts())
        val byClass = LinkedHashMap<HomeLayoutDeviceClass, List<SavedHomeLayout>>()
        root.array("layouts").forEach { entry ->
            val section = entry as? StoredValue.Obj
            val deviceClass = section?.let { enumOrNull<HomeLayoutDeviceClass>(it.string("deviceClass")) }
            if (section != null && deviceClass != null && deviceClass !in byClass) {
                byClass[deviceClass] = bounded(section.array("items").mapNotNull { decodeLayout(it) })
            }
        }
        return SavedHomeLayoutsDecodeResult(
            SavedHomeLayouts(byClass.filterValues { it.isNotEmpty() }),
            newerThanThisBuild = version > VERSION,
        )
    }

    /** At most the cap of saved layouts and one automatic layout; ids unique; saved ones first. */
    private fun bounded(decoded: List<SavedHomeLayout>): List<SavedHomeLayout> {
        val unique = decoded.distinctBy { it.id }
        val saved = unique.filter { it.kind == SavedHomeLayoutKind.SAVED }.take(SavedHomeLayouts.MAX_PER_DEVICE_CLASS)
        val automatic = unique.lastOrNull { it.kind == SavedHomeLayoutKind.BEFORE_ACTIVATION }
        return saved + listOfNotNull(automatic)
    }

    private fun encodeLayout(layout: SavedHomeLayout): StoredValue.Obj =
        obj(
            "id" to str(layout.id),
            "name" to str(layout.name),
            "kind" to str(if (layout.kind == SavedHomeLayoutKind.SAVED) KIND_SAVED else KIND_BEFORE),
            "savedAt" to num(layout.savedAtEpochMillis),
            "pool" to
                PoolCodec.encode(
                    PlacedItemPool(
                        items =
                            layout.items.mapValues {
                                    (_, item) ->
                                if (item is PoolWidget) item.withoutHosts() else item
                            },
                        arrangements = mapOf(ARRANGEMENT to Arrangement(pages = layout.pages)),
                    ),
                ),
        )

    private fun decodeLayout(value: StoredValue): SavedHomeLayout? =
        guarded {
            val root = value as? StoredValue.Obj
            val id = root?.string("id")
            val name =
                root?.string(
                    "name",
                )?.let { with(SavedHomeLayouts) { it.cleanName() } }?.takeIf { it.isNotEmpty() }
            if (root == null || id == null || name == null) {
                null
            } else {
                val pool = PoolCodec.decode(root.obj("pool")).pool
                SavedHomeLayout(
                    id = id,
                    name = name,
                    kind = kindOf(root.string("kind")),
                    savedAtEpochMillis = root.long("savedAt") ?: 0L,
                    pages = pool.arrangements[ARRANGEMENT]?.pages.orEmpty(),
                    items = pool.items.mapValues { (_, item) -> if (item is PoolWidget) item.withoutHosts() else item },
                )
            }
        }

    private fun kindOf(stored: String?): SavedHomeLayoutKind =
        if (stored == KIND_BEFORE) SavedHomeLayoutKind.BEFORE_ACTIVATION else SavedHomeLayoutKind.SAVED

    private const val KIND_SAVED = "saved"
    private const val KIND_BEFORE = "before"
}
