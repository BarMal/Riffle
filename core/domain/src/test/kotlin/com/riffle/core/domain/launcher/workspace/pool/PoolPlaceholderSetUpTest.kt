package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.home.WidgetStackEngine
import com.riffle.core.domain.launcher.home.WidgetStackRejection
import com.riffle.core.domain.launcher.home.WidgetStackResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A widget that could not be bound keeps its provider on Home and is set up in place (ADR 0003, Amendment 18). */
class PoolPlaceholderSetUpTest {
    private val placeholder = poolWidget("p", host = null)
    private val pool = emptyPool().add(placeholder, cell = at(1, 1, 2, 2))

    private fun pages() = ArrangementAdapter.toLauncherPages(pool.arrangements.getValue(W1), pool)

    private fun layoutOf(pages: List<com.riffle.core.domain.launcher.home.LauncherPage>): HomeLayout =
        HomeLayoutDefaults.standard().let { it.copy(pages = pages, selectedPageId = pages.first().id) }

    @Test
    fun theAdapterShowsAPlaceholderWithItsProvider() {
        val item = pages().first().items.single() as WidgetItem

        assertTrue(item.needsSetUp)
        assertEquals(provider, item.provider)
        assertNull(item.glance)
    }

    @Test
    fun aPlaceholderWithoutAProviderStaysWithoutOne() {
        val unknown = emptyPool().add(poolWidget("p", host = null, withProvider = false), cell = at(0, 0, 2, 2))
        val item = ArrangementAdapter.toLauncherPages(unknown.arrangements.getValue(W1), unknown).first().items.single()

        assertNull((item as WidgetItem).provider)
        assertTrue(item.needsSetUp)
    }

    @Test
    fun ingestingAnEditedPlaceholderKeepsItsProviderAndItsPlace() {
        val written = ArrangementIngest.ingest(pool, W1, pages(), counterIds()).done().pool

        assertEquals(pool, written)
    }

    @Test
    fun settingAPlaceholderUpWritesTheHostIdIntoTheSamePoolItem() {
        val pages = pages()
        val bound =
            WidgetStackEngine().bindPlaceholder(layoutOf(pages), LauncherItemId("p"), 0, HostedWidgetId(42))
        val edited = assertIs<WidgetStackResult.Updated>(bound).layout.pages

        val written = ArrangementIngest.ingest(pool, W1, edited, counterIds()).done().pool

        val item = written.items.getValue(PoolItemId("p")) as PoolWidget
        assertEquals(HostedWidgetId(42), item.hostedId)
        assertEquals(provider, item.provider)
        assertEquals(pool.arrangements, written.arrangements)
        assertInvariants(written)
    }

    @Test
    fun aMemberOfAStackIsSetUpWithoutMovingTheOthers() {
        val stack =
            PoolWidget(
                id = PoolItemId("s"),
                label = "First",
                provider = provider,
                hostedId = HostedWidgetId(11),
                stack =
                    listOf(
                        PoolStackMember("First", provider = provider, hostedId = HostedWidgetId(11)),
                        PoolStackMember("Second", provider = provider, hostedId = null),
                    ),
                shown = 1,
            )
        val two = emptyPool().add(stack, cell = at(0, 0, 2, 2))
        val pages = ArrangementAdapter.toLauncherPages(two.arrangements.getValue(W1), two)
        val item = pages.first().items.single() as WidgetItem
        assertFalse(item.members[0].needsSetUp)
        assertTrue(item.members[1].needsSetUp)
        assertEquals(provider, item.members[1].provider)

        val bound = WidgetStackEngine().bindPlaceholder(layoutOf(pages), LauncherItemId("s"), 1, HostedWidgetId(12))
        val written =
            ArrangementIngest.ingest(two, W1, assertIs<WidgetStackResult.Updated>(bound).layout.pages, counterIds())
                .done().pool

        val result = written.items.getValue(PoolItemId("s")) as PoolWidget
        assertEquals(listOf(11, 12), result.stack.map { it.hostedId?.value })
        assertEquals(listOf(provider, provider), result.stack.map { it.provider })
        assertEquals(1, result.shown)
        assertEquals(HostedWidgetId(11), result.hostedId)
    }

    private fun WidgetStackEngine.rejection(
        layout: HomeLayout,
        item: String,
        member: Int,
        host: Int,
    ): WidgetStackRejection =
        bindPlaceholder(layout, LauncherItemId(item), member, HostedWidgetId(host))
            .let { assertIs<WidgetStackResult.Rejected>(it).reason }

    @Test
    fun onlyAPlaceholderWithAFreshHostIdCanBeSetUp() {
        val engine = WidgetStackEngine()
        val layout = layoutOf(pages())
        val bound = engine.bindPlaceholder(layout, LauncherItemId("p"), 0, HostedWidgetId(7))
        val after = assertIs<WidgetStackResult.Updated>(bound).layout

        assertEquals(WidgetStackRejection.NOT_A_PLACEHOLDER, engine.rejection(after, "p", 0, 8))
        assertEquals(WidgetStackRejection.NOT_A_PLACEHOLDER, engine.rejection(layout, "p", 0, 0))
        assertEquals(WidgetStackRejection.INDEX_OUT_OF_RANGE, engine.rejection(layout, "p", 3, 9))
        assertEquals(WidgetStackRejection.ITEM_NOT_FOUND, engine.rejection(layout, "zz", 0, 9))
    }
}
