package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.workspace.StoredValue
import com.riffle.core.domain.launcher.workspace.WorkspaceId
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

data class PoolDecodeResult(
    val pool: PlacedItemPool,
    /** What decoding dropped (dangling references and the like). Ids only. */
    val issues: List<PoolIssue>,
)

/**
 * Encodes and decodes a [PlacedItemPool] to [StoredValue] (the `pool` entry of a layout in the pool store). The dock
 * is an optional `dock` key: a pool written before the dock moved into the pool decodes with `dock = null`.
 * Decoding never throws: a missing or malformed value is an empty pool, undecodable items and arrangements are
 * dropped, and the result goes through [PoolValidation.repair] so the invariants hold. Dangling references are
 * dropped and reported in [PoolDecodeResult.issues].
 */
object PoolCodec {
    fun encode(pool: PlacedItemPool): StoredValue.Obj =
        obj(
            "items" to arr(pool.items.values.map(PoolItemEncoding::encodeItem)),
            "arrangements" to arr(pool.arrangements.map { (id, arrangement) -> encodeArrangement(id, arrangement) }),
            "dock" to pool.dock?.let(PoolDockCodec::encode),
        )

    fun decode(value: StoredValue?): PoolDecodeResult {
        val root = value as? StoredValue.Obj ?: return PoolDecodeResult(PlacedItemPool(), emptyList())
        val items =
            root.array("items").mapNotNull {
                (it as? StoredValue.Obj)?.let(PoolItemDecoding::decodeItem)
            }.distinctBy { it.id }
        val arrangements =
            root.array("arrangements")
                .mapNotNull { (it as? StoredValue.Obj)?.let(::decodeArrangement) }
                .distinctBy { it.first }
        val dock = root.obj("dock")?.let { guarded { PoolDockCodec.decode(it) } }
        val repaired = PoolValidation.repair(PlacedItemPool(items.associateBy { it.id }, arrangements.toMap(), dock))
        return PoolDecodeResult(repaired.pool, repaired.issues)
    }

    private fun encodeArrangement(
        id: WorkspaceId,
        arrangement: Arrangement,
    ): StoredValue.Obj =
        obj(
            "workspace" to str(id.value),
            "newApps" to str(arrangement.newAppPlacement.name),
            // Absent for every arrangement that is not a fork, so those encode exactly as before.
            "forkedFrom" to arrangement.forkedFrom?.let { str(it.value) },
            "pages" to arr(arrangement.pages.map(::encodePage)),
        )

    internal fun encodePage(page: ArrangementPage): StoredValue.Obj =
        obj(
            "id" to str(page.id.value),
            "columns" to num(page.grid.columns),
            "rows" to num(page.grid.rows),
            "overflow" to num(page.generatedContentOverflowCount),
            "pinned" to StoredValue.Bool(page.isPinned),
            "placements" to
                arr(
                    page.placements.map {
                        obj(
                            "item" to str(it.item.value),
                            "column" to num(it.at.cell.column),
                            "row" to num(it.at.cell.row),
                            "columns" to num(it.at.span.columns),
                            "rows" to num(it.at.span.rows),
                        )
                    },
                ),
        )

    private fun decodeArrangement(root: StoredValue.Obj): Pair<WorkspaceId, Arrangement>? =
        guarded {
            root.string("workspace")?.let { id ->
                WorkspaceId(id) to
                    Arrangement(
                        pages = root.array("pages").mapNotNull { (it as? StoredValue.Obj)?.let(::decodePage) },
                        newAppPlacement =
                            enumOrNull<NewAppPlacement>(root.string("newApps")) ?: NewAppPlacement.FINDER_ONLY,
                        forkedFrom = root.string("forkedFrom")?.takeIf { it.isNotBlank() }?.let(::WorkspaceId),
                    )
            }
        }

    internal fun decodePage(root: StoredValue.Obj): ArrangementPage? {
        val id = root.string("id")
        val columns = root.int("columns")
        val rows = root.int("rows")
        return if (id == null || columns == null || rows == null) {
            null
        } else {
            ArrangementPage(
                id = LauncherPageId(id),
                grid = GridDimensions(columns, rows),
                placements = root.array("placements").mapNotNull { (it as? StoredValue.Obj)?.let(::decodePlacement) },
                generatedContentOverflowCount = root.int("overflow") ?: 0,
                isPinned = root.bool("pinned") ?: false,
            )
        }
    }

    private fun decodePlacement(root: StoredValue.Obj): Placement? =
        guarded {
            val item = root.string("item")?.let(::PoolItemId)
            val column = root.int("column")
            val row = root.int("row")
            if (item == null || column == null || row == null) {
                null
            } else {
                Placement(
                    item,
                    GridPlacement(GridCell(column, row), GridSpan(root.int("columns") ?: 1, root.int("rows") ?: 1)),
                )
            }
        }
}

internal fun StoredValue.Obj.int(key: String): Int? = long(key)?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
