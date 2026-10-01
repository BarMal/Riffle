package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.GridPlacementEngine
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.PlaceLauncherItemResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ArrangementIngestTest {
    private val engine = GridPlacementEngine()
    private val pool =
        emptyPool()
            .add(poolApp("a"), cell = at(0, 0))
            .add(poolFolder("f", "x"), cell = at(1, 0))
            .add(poolApp("other"), ws = W2, cell = at(0, 0))

    private fun pages(ws: com.riffle.core.domain.launcher.workspace.WorkspaceId = W1): List<LauncherPage> =
        ArrangementAdapter.toLauncherPages(pool.arrangements.getValue(ws), pool)

    @Test
    fun anUnchangedRoundTripIsIdentity() {
        val edit = ArrangementIngest.ingest(pool, W1, pages(), counterIds()).done()
        assertEquals(pool, edit.pool)
    }

    @Test
    fun anEngineMintedIdEqualToAnotherArrangementsIdIsRemappedAsNew() {
        // HomeShortcutEngine mints ids from a per-layout ordinal, so a new id can equal an id of another arrangement.
        val clashing = AppShortcutItem(LauncherItemId("other"), appIdentity("fresh"), "Fresh")
        val placed = engine.placeItemInFirstAvailableCell(pages().first(), clashing) as PlaceLauncherItemResult.Placed
        val edit = ArrangementIngest.ingest(pool, W1, listOf(placed.page, pages()[1]), counterIds()).done()
        assertEquals(
            poolApp("other"),
            edit.pool.items.getValue(PoolItemId("other")),
            "the other arrangement's item is untouched",
        )
        assertEquals(1, edit.pool.refs("other"))
        val created = edit.pool.items.getValue(edit.createdItemIds.single()) as PoolApp
        assertEquals("Fresh", created.label)
        assertNotEquals(PoolItemId("other"), created.id)
        assertInvariants(edit.pool)
    }

    @Test
    fun aVanishedItemLosesItsReferenceAndIsCollectedUnlessShared() {
        val shared = PoolPlacement.placeExisting(pool, W2, P1, PoolItemId("a"), at(1, 1)).done().pool
        val withoutA =
            ArrangementAdapter.toLauncherPages(shared.arrangements.getValue(W1), shared).map { page ->
                engine.removeItem(page, LauncherItemId("a"))
            }
        val edit = ArrangementIngest.ingest(shared, W1, withoutA, counterIds()).done()
        assertEquals(1, edit.pool.refs("a"))
        val withoutF = withoutA.map { engine.removeItem(it, LauncherItemId("f")) }
        val gone = ArrangementIngest.ingest(shared, W1, withoutF, counterIds()).done()
        assertTrue(PoolItemId("f") !in gone.pool.items)
        assertEquals(listOf(poolFolder("f", "x")), gone.removedItems)
    }

    @Test
    fun movingAndFolderEditsWriteBackToThePoolItem() {
        val moved = engine.moveItem(pages().first(), LauncherItemId("a"), at(3, 3)) as PlaceLauncherItemResult.Placed
        val renamed =
            moved.page.items.map {
                if (it is FolderItem) {
                    it.copy(
                        label = "Games",
                        items = emptyList(),
                    )
                } else {
                    it
                }
            }
        val edit =
            ArrangementIngest.ingest(
                pool,
                W1,
                listOf(moved.page.copy(items = renamed), pages()[1]),
                counterIds(),
            ).done()
        val placements = edit.pool.arrangements.getValue(W1).pages.first().placements
        assertEquals(at(3, 3), placements.first { it.item.value == "a" }.at)
        assertEquals(PoolFolder(PoolItemId("f"), "Games", emptyList()), edit.pool.items.getValue(PoolItemId("f")))
        assertInvariants(edit.pool)
    }

    @Test
    fun aDuplicateEngineIdKeepsOnlyTheFirstPlacement() {
        val twin = AppShortcutItem(LauncherItemId("dup"), appIdentity("d"), "D", placement = at(2, 2))
        val twice = pages().first().copy(items = pages().first().items + twin + twin.copy(placement = at(3, 3)))
        val edit = ArrangementIngest.ingest(pool, W1, listOf(twice, pages()[1]), counterIds()).done()
        assertEquals(1, edit.createdItemIds.size)
        assertInvariants(edit.pool)
    }

    @Test
    fun anUnknownWorkspaceIsRejected() {
        assertEquals(
            PoolRejection.UNKNOWN_WORKSPACE,
            ArrangementIngest.ingest(pool, W3, pages(), counterIds()).rejection(),
        )
    }
}
