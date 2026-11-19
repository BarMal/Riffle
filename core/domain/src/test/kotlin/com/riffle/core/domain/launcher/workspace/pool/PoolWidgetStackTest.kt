package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.glance.GlanceWidgetKind
import com.riffle.core.domain.launcher.glance.GlanceWidgets
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.home.WidgetStackEngine
import com.riffle.core.domain.launcher.home.WidgetStackResult
import com.riffle.core.domain.launcher.workspace.StoredValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** A widget stack in the placed-items pool (ADR 0003, Amendment 14): stored, decoded, validated, edited and released. */
class PoolWidgetStackTest {
    private val member2 = PoolStackMember("Second", provider = provider, hostedId = HostedWidgetId(12))
    private val glanceMember =
        PoolStackMember(
            label = "Now playing",
            resizeConstraints = GlanceWidgetKind.NOW_PLAYING.resizeConstraints,
            provider = GlanceWidgets.identity(GlanceWidgetKind.NOW_PLAYING),
        )

    private fun stack(
        id: String = "s",
        lead: Int = 11,
        vararg others: PoolStackMember,
        shown: Int = 0,
    ) = PoolWidget(
        id = PoolItemId(id),
        label = "First",
        provider = provider,
        hostedId = HostedWidgetId(lead),
        stack =
            listOf(PoolStackMember("First", provider = provider, hostedId = HostedWidgetId(lead))) + others.toList(),
        shown = shown,
    )

    private val pool = emptyPool().add(stack(others = arrayOf(member2, glanceMember), shown = 2), cell = at(0, 0, 2, 2))

    @Test
    fun aPoolWithAStackRoundTripsAndKeepsTheShownMember() {
        val decoded = PoolCodec.decode(PoolCodec.encode(pool))

        assertEquals(pool, decoded.pool)
        assertEquals(emptyList(), decoded.issues)
        assertEquals(2, (decoded.pool.items.getValue(PoolItemId("s")) as PoolWidget).shown)
    }

    @Test
    fun aPlainWidgetIsStoredExactlyAsBeforeStacks() {
        val plain = emptyPool().add(poolWidget("w", host = 12), cell = at(0, 0, 2, 1))
        val encoded = PoolCodec.encode(plain)
        val item = (encoded.fields.getValue("items") as StoredValue.Arr).items.single() as StoredValue.Obj

        assertFalse("stack" in item.fields)
        assertFalse("shown" in item.fields)
        assertEquals(plain, PoolCodec.decode(encoded).pool)
    }

    @Test
    fun aStackWrittenWithOneMemberOrNoneReadsAsAPlainWidget() {
        val encoded = PoolCodec.encode(pool)
        val items = (encoded.fields.getValue("items") as StoredValue.Arr).items.map { it as StoredValue.Obj }
        val damaged =
            items.map { item ->
                val members = (item.fields.getValue("stack") as StoredValue.Arr).items.take(1)
                StoredValue.Obj(item.fields + ("stack" to StoredValue.Arr(members)))
            }

        val decoded = PoolCodec.decode(StoredValue.Obj(encoded.fields + ("items" to StoredValue.Arr(damaged))))

        val widget = decoded.pool.items.getValue(PoolItemId("s")) as PoolWidget
        assertEquals(emptyList(), widget.stack)
        assertEquals(0, widget.shown)
    }

    @Test
    fun everyMembersHostIdCountsAsHeldAndOnlyOneWidgetMayHoldIt() {
        val held = (pool.items.getValue(PoolItemId("s")) as PoolWidget).hostIds
        assertEquals(setOf(HostedWidgetId(11), HostedWidgetId(12)), held)
        assertEquals(setOf(HostedWidgetId(11), HostedWidgetId(12)), PoolReferences.hostIds(pool))

        val clash = PoolPlacement.addNew(pool, W1, P1, poolWidget("w", host = 12), at(2, 0))
        assertEquals(PoolRejection.HOST_ID_IN_USE, clash.rejection())

        val repaired = PoolValidation.repair(pool.copy(items = pool.items + (PoolItemId("w") to poolWidget("w", 12))))
        assertInvariants(repaired.pool)
    }

    @Test
    fun repairDropsAWidgetThatHoldsAHostIdAStackOwns() {
        val dup = pool.add(poolWidget("w", host = 13), cell = at(2, 0))
        val raw = dup.copy(items = dup.items + (PoolItemId("w") to poolWidget("w", host = 12)))

        val repaired = PoolValidation.repair(raw)

        assertTrue(repaired.issues.any { it.kind == PoolIssueKind.DUPLICATE_HOST_ID && it.itemId == PoolItemId("w") })
        assertInvariants(repaired.pool)
    }

    @Test
    fun removingTheStackReleasesEveryMembersHostId() {
        val edit = PoolRemoval.remove(pool, W1, PoolItemId("s")).done()

        assertEquals(setOf(HostedWidgetId(11), HostedWidgetId(12)), edit.releasedHostIds)
        assertTrue(edit.pool.items.isEmpty())
    }

    @Test
    fun aCopyOfAStackIsUnboundInEveryMember() {
        val copy = (pool.items.getValue(PoolItemId("s")) as PoolWidget).withoutHosts()

        assertEquals(emptySet(), copy.hostIds)
        assertEquals(3, copy.stack.size)
    }

    @Test
    fun aStackIsNotClonedAsASeparateCopy() {
        val result = PoolWidgets.separateCopy(pool, PoolItemId("s"), W1, P1, counterIds(), HostedWidgetId(50))

        assertEquals(PoolRejection.PROVIDER_UNKNOWN, result.rejection())
    }

    @Test
    fun theAdapterShowsAStackAsOneWidgetItemAndIngestWritesItBack() {
        val pages = ArrangementAdapter.toLauncherPages(pool.arrangements.getValue(W1), pool)
        val item = pages.first().items.single() as WidgetItem

        assertTrue(item.isStack)
        assertEquals(listOf(11, 12, 0), item.members.map { it.appWidgetId.value })
        assertEquals(GlanceWidgetKind.NOW_PLAYING, item.members.last().glance)
        assertEquals(2, item.shownIndex)
        assertEquals(pool, ArrangementIngest.ingest(pool, W1, pages, counterIds()).done().pool)
    }

    @Test
    fun anEditThatTakesAMemberOutLeavesTheProviderOfTheOthersAndReleasesItsHostId() {
        val engine = WidgetStackEngine()
        val pages = ArrangementAdapter.toLauncherPages(pool.arrangements.getValue(W1), pool)
        val layout = homeLayoutOf(pages)
        val removed = assertIs<WidgetStackResult.Updated>(engine.remove(layout, LauncherItemId("s"), 1))

        val edit = ArrangementIngest.ingest(pool, W1, removed.layout.pages, counterIds()).done()

        val widget = edit.pool.items.getValue(PoolItemId("s")) as PoolWidget
        assertEquals(listOf(HostedWidgetId(11), null), widget.stack.map { it.hostedId })
        assertEquals(provider, widget.stack.first().provider)
        assertEquals(setOf(HostedWidgetId(12)), edit.releasedHostIds)
        assertInvariants(edit.pool)
    }

    @Test
    fun aStackReducedToOneWidgetIsAPlainPoolWidgetAgain() {
        val engine = WidgetStackEngine()
        val two = emptyPool().add(stack(others = arrayOf(member2)), cell = at(0, 0, 2, 2))
        val layout = homeLayoutOf(ArrangementAdapter.toLauncherPages(two.arrangements.getValue(W1), two))
        val removed = assertIs<WidgetStackResult.Updated>(engine.remove(layout, LauncherItemId("s"), 0))

        val widget =
            ArrangementIngest.ingest(two, W1, removed.layout.pages, counterIds()).done().pool
                .items.getValue(PoolItemId("s")) as PoolWidget

        assertEquals(emptyList(), widget.stack)
        assertEquals(HostedWidgetId(12), widget.hostedId)
        assertEquals("Second", widget.label)
        assertEquals(0, widget.shown)
        assertEquals(provider, widget.provider)
    }

    @Test
    fun aBackupCarriesTheStackWithoutHostIds() {
        val restored = (pool.items.getValue(PoolItemId("s")) as PoolWidget).withoutHosts()

        assertEquals(listOf("First", "Second", "Now playing"), restored.stack.map { it.label })
        assertEquals(setOf(null), restored.stack.map { it.hostedId }.toSet())
    }

    private fun homeLayoutOf(pages: List<LauncherPage>): HomeLayout =
        HomeLayoutDefaults.standard().copy(pages = pages, selectedPageId = pages.first().id)
}
