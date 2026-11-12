package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.glance.GlanceWidgetKind
import com.riffle.core.domain.launcher.glance.GlanceWidgets
import com.riffle.core.domain.launcher.home.GridPlacementEngine
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.WidgetEditResult
import com.riffle.core.domain.launcher.home.WidgetEngine
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.widgets.WidgetProviderClassName
import com.riffle.core.domain.launcher.workspace.StoredValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * P11: a glance widget is a `PoolWidget` with the reserved Riffle provider and no host id, so the codec, validation,
 * garbage collection, copy and removal treat it as the widget it is. These pin the stored shape (an older build must
 * still read a pool that holds one) and the way back from the engines.
 */
class GlanceWidgetPoolTest {
    private fun glance(
        id: String,
        kind: GlanceWidgetKind,
    ) = PoolWidget(
        id = PoolItemId(id),
        label = kind.defaultLabel,
        resizeConstraints = kind.resizeConstraints,
        provider = GlanceWidgets.identity(kind),
        hostedId = null,
    )

    private val pool =
        emptyPool()
            .add(poolApp("a"), cell = at(0, 0))
            .add(glance("g1", GlanceWidgetKind.NOW_PLAYING), cell = at(0, 1, 4, 2))
            .add(glance("g2", GlanceWidgetKind.TOP_STORY), page = P2, cell = at(0, 0, 4, 2))
            .add(poolWidget("hosted", host = 12), page = P2, cell = at(0, 2, 2, 2))

    @Test
    fun aPoolWithGlanceWidgetsRoundTrips() {
        val decoded = PoolCodec.decode(PoolCodec.encode(pool))
        assertEquals(pool, decoded.pool)
        assertEquals(emptyList(), decoded.issues)
        assertEquals(
            GlanceWidgetKind.NOW_PLAYING,
            GlanceWidgets.kindOf((decoded.pool.items.getValue(PoolItemId("g1")) as PoolWidget).provider),
        )
        assertEquals(1, decoded.pool.refs("g1"))
    }

    @Test
    fun theStoredShapeIsTheOneAnOlderBuildAlreadyReads() {
        val encoded = PoolCodec.encode(pool)
        val items = (encoded.fields.getValue("items") as StoredValue.Arr).items.map { it as StoredValue.Obj }
        val widget = items.first { (it.fields["id"] as StoredValue.Str).value == "g1" }
        // Exactly the keys of any widget item: no new key, no new kind, and no host id for the platform to look up.
        assertEquals(setOf("id", "kind", "label", "constraints", "provider"), widget.fields.keys)
        assertEquals(StoredValue.Str("widget"), widget.fields["kind"])
        val provider = widget.fields.getValue("provider") as StoredValue.Obj
        assertEquals(setOf("package", "class", "profile", "profileType"), provider.fields.keys)
        assertEquals(StoredValue.Str(GlanceWidgets.PROVIDER_PACKAGE), provider.fields["package"])
        assertEquals(StoredValue.Str("glance.NowPlaying"), provider.fields["class"])
    }

    @Test
    fun anOldBlobWithPlainWidgetsDecodesHereAsPlatformWidgets() {
        val old = emptyPool().add(poolWidget("w", host = 7), cell = at(0, 0, 2, 2))
        val decoded = PoolCodec.decode(PoolCodec.encode(old)).pool
        val widget = decoded.items.getValue(PoolItemId("w")) as PoolWidget
        assertNull(GlanceWidgets.kindOf(widget.provider))
        val item = ArrangementAdapter.toItem(widget, null) as WidgetItem
        assertNull(item.glance)
        assertEquals(HostedWidgetId(7), item.appWidgetId)
        val placeholder = poolWidget("p", host = null, withProvider = false)
        assertNull((ArrangementAdapter.toItem(placeholder, null) as WidgetItem).glance)
    }

    @Test
    fun anUnknownItemKindOrGlanceClassIsToleratedNeverACrash() {
        // What a build that does not know a kind makes of it: the item is dropped with its placement, the rest stays.
        val encoded = PoolCodec.encode(pool)
        val items =
            (encoded.fields.getValue("items") as StoredValue.Arr).items.map { value ->
                val obj = value as StoredValue.Obj
                if (obj.fields["id"] == StoredValue.Str("g1")) {
                    StoredValue.Obj(obj.fields + ("kind" to StoredValue.Str("glance-from-the-future")))
                } else {
                    obj
                }
            }
        val decoded = PoolCodec.decode(encoded.copy(fields = encoded.fields + ("items" to StoredValue.Arr(items))))
        assertTrue(PoolItemId("g1") !in decoded.pool.items)
        assertNotNull(decoded.pool.items[PoolItemId("g2")])
        assertNotNull(decoded.pool.items[PoolItemId("a")])
        assertEquals(0, decoded.pool.refs("g1"))
        assertInvariants(decoded.pool)

        // A glance class this build does not list is just a widget with a provider and no host: unbound, kept.
        val unlisted =
            GlanceWidgets.identity(
                GlanceWidgetKind.NEXT_EVENT,
            ).copy(className = WidgetProviderClassName("glance.Tasks"))
        val future = glance("f", GlanceWidgetKind.NEXT_EVENT).copy(provider = unlisted)
        val kept = PoolCodec.decode(PoolCodec.encode(emptyPool().add(future, cell = at(0, 0, 2, 1)))).pool
        assertEquals(future, kept.items.getValue(PoolItemId("f")))
        assertNull((ArrangementAdapter.toItem(future, null) as WidgetItem).glance)
    }

    @Test
    fun theAdapterMarksGlanceWidgetsAndTheIngestKeepsThem() {
        val pages = ArrangementAdapter.toLauncherPages(pool.arrangements.getValue(W1), pool)
        val widget = pages.first().items.filterIsInstance<WidgetItem>().single()
        assertEquals(GlanceWidgetKind.NOW_PLAYING, widget.glance)
        assertEquals(ArrangementAdapter.UNBOUND_HOST_ID, widget.appWidgetId)
        val edit = ArrangementIngest.ingest(pool, W1, pages, counterIds()).done()
        assertEquals(pool, edit.pool, "an unchanged round trip is the identity")
        assertEquals(emptySet(), edit.releasedHostIds)
    }

    @Test
    fun aWidgetAddedByTheEngineBecomesAGlancePoolItemWithTheReservedProvider() {
        val layoutPage = ArrangementAdapter.toLauncherPages(pool.arrangements.getValue(W1), pool).first()
        val engine = WidgetEngine(GridPlacementEngine())
        val standard = HomeLayoutDefaults.standard()
        val layout =
            standard.copy(
                pages = listOf(layoutPage.copy(items = layoutPage.items.filter { it.id.value == "a" })),
                selectedPageId = layoutPage.id,
            )
        val updated =
            assertIs<WidgetEditResult.Updated>(
                engine.addGlanceWidgetToPage(layout, GlanceWidgetKind.NEXT_EVENT, "Next event"),
            ).layout
        val edit = ArrangementIngest.ingest(pool, W1, updated.pages + pages(W1).drop(1), counterIds()).done()
        val created = edit.pool.items.getValue(edit.createdItemIds.single()) as PoolWidget
        assertEquals(GlanceWidgets.identity(GlanceWidgetKind.NEXT_EVENT), created.provider)
        assertNull(created.hostedId)
        assertEquals(GlanceWidgetKind.NEXT_EVENT.resizeConstraints, created.resizeConstraints)
        assertInvariants(edit.pool)
    }

    private fun pages(ws: com.riffle.core.domain.launcher.workspace.WorkspaceId) =
        ArrangementAdapter.toLauncherPages(pool.arrangements.getValue(ws), pool)

    @Test
    fun removingAGlanceWidgetReleasesNoHostIdAndIsCollected() {
        val pages = ArrangementAdapter.toLauncherPages(pool.arrangements.getValue(W1), pool)
        val without = pages.map { page -> page.copy(items = page.items.filter { it.id.value != "g1" }) }
        val edit = ArrangementIngest.ingest(pool, W1, without, counterIds()).done()
        assertTrue(PoolItemId("g1") !in edit.pool.items)
        assertEquals(emptySet(), edit.releasedHostIds)
        assertInvariants(edit.pool)
    }

    @Test
    fun aSeparateCopyAndACloneKeepTheProviderButNeverAHostId() {
        // Copying a workspace shares widgets (one instance); a copy to another layout clones them unbound.
        val edit = PoolWidgets.separateCopy(pool, PoolItemId("g1"), W1, P2, counterIds(), newHostedId = null).done()
        val copy = edit.pool.items.getValue(edit.createdItemIds.single()) as PoolWidget
        assertEquals(GlanceWidgetKind.NOW_PLAYING, GlanceWidgets.kindOf(copy.provider))
        assertNull(copy.hostedId)
        assertInvariants(edit.pool)
    }
}
