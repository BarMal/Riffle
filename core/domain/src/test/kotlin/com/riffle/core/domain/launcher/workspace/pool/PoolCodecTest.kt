package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.apps.AppShortcutId
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.WidgetResizeConstraints
import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.StoredValue
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.WorkspaceSetCodec
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PoolCodecTest {
    private val rich =
        emptyPool(newApps = NewAppPlacement.HOME_AND_FINDER)
            .add(poolApp("a"), cell = at(0, 0))
            .add(poolApp("s").copy(appShortcutId = AppShortcutId("sc")), cell = at(1, 0))
            .add(poolFolder("f", "x", "y"), cell = at(2, 0))
            .add(
                poolWidget("w", host = 12).copy(
                    resizeConstraints =
                        WidgetResizeConstraints(
                            minSpan = GridSpan(2, 1),
                            maxSpan = GridSpan(4, 2),
                            supportsVerticalResize = false,
                        ),
                ),
                page = P2,
                cell = at(0, 0, 2, 1),
            )
            .add(poolWidget("placeholder", host = null, withProvider = false), page = P2, cell = at(0, 2))
            .let { PoolPlacement.placeExisting(it, W2, P1, PoolItemId("a")).done().pool }

    @Test
    fun roundTripsARichPool() {
        val decoded = PoolCodec.decode(PoolCodec.encode(rich))
        assertEquals(rich, decoded.pool)
        assertEquals(emptyList(), decoded.issues)
    }

    @Test
    fun missingOrUnreadableValuesAreAnEmptyPool() {
        assertEquals(PlacedItemPool(), PoolCodec.decode(null).pool)
        assertEquals(PlacedItemPool(), PoolCodec.decode(StoredValue.Str("x")).pool)
        assertEquals(PlacedItemPool(), PoolCodec.decode(StoredValue.Obj(emptyMap())).pool)
        assertEquals(PlacedItemPool(), PoolCodec.decode(StoredValue.Arr(emptyList())).pool)
    }

    @Test
    fun danglingReferencesAreDroppedAndReportedNeverCrashed() {
        val encoded = PoolCodec.encode(rich)
        val withoutA =
            encoded.copy(
                fields =
                    encoded.fields + (
                        "items" to
                            StoredValue.Arr(
                                items(encoded).filterNot {
                                    id(it) == "a"
                                },
                            )
                    ),
            )
        val decoded = PoolCodec.decode(withoutA)
        assertFalse(PoolItemId("a") in decoded.pool.items)
        assertEquals(2, decoded.issues.count { it.kind == PoolIssueKind.DANGLING_REFERENCE })
        assertInvariants(decoded.pool)
    }

    @Test
    fun duplicateWidgetPlacementsKeepTheFirstOnly() {
        // Re-point W2's placements at the widget: it is then placed twice in the layout.
        val pool =
            rich.copy(
                arrangements =
                    rich.arrangements + (
                        W2 to
                            rich.arrangements.getValue(W2).let { a ->
                                a.copy(
                                    pages =
                                        a.pages.map {
                                                p ->
                                            p.copy(placements = p.placements.map { it.copy(item = PoolItemId("w")) })
                                        },
                                )
                            }
                    ),
            )
        val decoded = PoolCodec.decode(PoolCodec.encode(pool))
        assertEquals(1, decoded.pool.refs("w"))
        assertTrue(decoded.issues.any { it.kind == PoolIssueKind.WIDGET_MULTIPLY_PLACED })
        assertInvariants(decoded.pool)
    }

    @Test
    fun unknownKindsAndBadFieldsAreSkipped() {
        val hologram = StoredValue.Obj(mapOf("id" to StoredValue.Str("q"), "kind" to StoredValue.Str("hologram")))
        val bareWorkspace = StoredValue.Obj(mapOf("workspace" to StoredValue.Str("w1")))
        val value =
            StoredValue.Obj(
                mapOf(
                    "items" to StoredValue.Arr(listOf(hologram, StoredValue.Num(3))),
                    "arrangements" to StoredValue.Arr(listOf(StoredValue.Str("junk"), bareWorkspace)),
                ),
            )
        val decoded = PoolCodec.decode(value)
        assertTrue(decoded.pool.items.isEmpty())
        assertEquals(setOf(W1), decoded.pool.arrangements.keys)
    }

    @Test
    fun randomlyCorruptedBlobsNeverThrowAndAlwaysDecodeToAValidPool() {
        for (seed in 1..200) {
            val rnd = Random(seed)
            val corrupted = corrupt(PoolCodec.encode(rich), rnd)
            val decoded = PoolCodec.decode(corrupted)
            assertInvariants(decoded.pool)
        }
    }

    @Test
    fun aLegacyPoolKeyInTheWorkspaceSetBlobIsIgnored() {
        val workspace =
            Workspace(
                WorkspaceId("w1"),
                "One",
                listOf(
                    PageContainer(
                        ContainerId("c"),
                        PageContent.Bound(LensBinding(Lens(sources = listOf(SourceId("apps"))), ExpressionKind.LIST)),
                    ),
                ),
            )
        val set = WorkspaceSet(mapOf(HomeLayoutDeviceClass.PHONE to LayoutWorkspaces.single(workspace)))
        val encoded = WorkspaceSetCodec.encode(set)
        val layout = (encoded.fields["layouts"] as StoredValue.Arr).items.single() as StoredValue.Obj
        assertFalse("pool" in layout.fields, "placed items live in the pool store, not the workspace blob")
        // Earlier schema 2 blobs could carry a `pool` key; it decodes to the same set.
        val legacyLayout = layout.copy(fields = layout.fields + ("pool" to PoolCodec.encode(rich)))
        val legacy = encoded.copy(fields = encoded.fields + ("layouts" to StoredValue.Arr(listOf(legacyLayout))))
        assertEquals(set, WorkspaceSetCodec.decode(legacy))
    }

    private fun items(root: StoredValue.Obj) = (root.fields.getValue("items") as StoredValue.Arr).items

    private fun id(value: StoredValue) = ((value as StoredValue.Obj).fields["id"] as StoredValue.Str).value

    private fun corrupt(
        value: StoredValue,
        rnd: Random,
    ): StoredValue =
        when {
            rnd.nextInt(12) == 0 ->
                listOf(
                    StoredValue.Str("?"),
                    StoredValue.Num(-1),
                    StoredValue.Bool(true),
                    StoredValue.Arr(emptyList()),
                )[rnd.nextInt(4)]
            value is StoredValue.Obj ->
                StoredValue.Obj(value.fields.filterKeys { rnd.nextInt(15) != 0 }.mapValues { corrupt(it.value, rnd) })
            value is StoredValue.Arr ->
                StoredValue.Arr(
                    value.items.filter { rnd.nextInt(15) != 0 }.map { corrupt(it, rnd) },
                )
            else -> value
        }
}
