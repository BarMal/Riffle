package com.riffle.core.domain.launcher.workspace.container

import com.riffle.core.domain.launcher.workspace.ItemSource
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensResult
import com.riffle.core.domain.launcher.workspace.ParameterizedItemSource
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.SourceObserver
import com.riffle.core.domain.launcher.workspace.SourceParameter
import com.riffle.core.domain.launcher.workspace.SourceState
import com.riffle.core.domain.launcher.workspace.SourceSubscription
import com.riffle.core.domain.launcher.workspace.lens.AsyncLensEvaluator
import com.riffle.core.domain.launcher.workspace.lens.LensEvaluationContext
import com.riffle.core.domain.launcher.workspace.testing.FakeItemSource
import com.riffle.core.domain.launcher.workspace.testing.FakeSourceRegistry
import com.riffle.core.domain.launcher.workspace.testing.fakeItem
import java.util.concurrent.Executor
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A search-like source: the default query and every parameter each answer with items titled by the query. */
private class FakeParameterizedSource(
    override val descriptor: SourceDescriptor = SourceDescriptor(SourceIds.SEARCH),
) : ParameterizedItemSource {
    val defaultSource = FakeItemSource(descriptor, SourceState.Ready(listOf(fakeItem("default", "search", "default"))))
    val connects = HashMap<String, Int>()
    val live = HashMap<String, Int>()

    override fun subscribe(observer: SourceObserver): SourceSubscription = defaultSource.subscribe(observer)

    override fun subscribe(
        parameter: SourceParameter,
        observer: SourceObserver,
    ): SourceSubscription {
        val text = parameter.text
        connects[text] = (connects[text] ?: 0) + 1
        live[text] = (live[text] ?: 0) + 1
        observer.onState(SourceState.Ready(listOf(fakeItem("hit:$text", "search", "result for $text"))))
        return SourceSubscription { live[text] = (live[text] ?: 0) - 1 }
    }

    fun liveUpstreams(): Int = live.values.sum()
}

class ParameterizedSourcesTest {
    private val search = SourceIds.SEARCH
    private val source = FakeParameterizedSource()
    private val registry = SharedSourceRegistry(FakeSourceRegistry(listOf(source)), maxParameterStreams = 2)

    private fun q(text: String) = assertNotNull(SourceParameter.query(text))

    private fun states(
        id: SourceId,
        parameter: SourceParameter?,
        into: MutableList<SourceState>,
    ) = registry.source(id, parameter).subscribe { into += it }

    private fun titles(state: SourceState?) = (state as SourceState.Ready).items.map { it.title }

    @Test
    fun differentQueriesGetDifferentResultsFromOneSource() {
        val a = ArrayList<SourceState>()
        val b = ArrayList<SourceState>()
        states(search, q("mail"), a)
        states(search, q("maps"), b)
        assertEquals(listOf("result for mail"), titles(a.last()))
        assertEquals(listOf("result for maps"), titles(b.last()))
        assertEquals(2, registry.liveParameterStreams())
    }

    @Test
    fun theSameQuerySharesOneUpstreamAndReleasesItAfterTheLastObserver() {
        val subs = List(3) { states(search, q("mail"), ArrayList()) }
        assertEquals(1, source.connects["mail"])
        assertEquals(1, source.liveUpstreams())
        subs.dropLast(1).forEach { it.cancel() }
        assertEquals(1, source.liveUpstreams())
        subs.last().cancel()
        subs.last().cancel()
        assertEquals(0, source.liveUpstreams())
        assertEquals(0, registry.liveParameterStreams())
    }

    @Test
    fun noParameterUsesTheDefaultQueryAndSharesTheSingleStream() {
        val a = ArrayList<SourceState>()
        val b = ArrayList<SourceState>()
        states(search, null, a)
        states(search, null, b)
        assertEquals(listOf("default"), titles(a.last()))
        assertEquals(1, source.defaultSource.observerCount)
        assertEquals(0, registry.liveParameterStreams())
    }

    @Test
    fun aSourceWithoutParameterSupportIgnoresTheParameterAndStaysShared() {
        val plain = FakeItemSource(SourceDescriptor(SourceIds.ALL_APPS), SourceState.Ready(emptyList()))
        val shared = SharedSourceRegistry(FakeSourceRegistry(listOf(plain)))
        shared.source(SourceIds.ALL_APPS, q("x")).subscribe { }
        shared.source(SourceIds.ALL_APPS, q("y")).subscribe { }
        shared.source(SourceIds.ALL_APPS, null).subscribe { }
        assertEquals(1, plain.observerCount)
        assertEquals(0, shared.liveParameterStreams())
    }

    @Test
    fun anUnknownSourceWithAParameterReadsAsUnavailable() {
        val out = ArrayList<SourceState>()
        states(SourceId("nope"), q("x"), out)
        assertEquals(SourceState.Unavailable, out.last())
    }

    @Test
    fun beyondTheCapAnObserverReadsUnavailableUntilASlotFrees() {
        val first = states(search, q("a"), ArrayList())
        states(search, q("b"), ArrayList())
        val over = ArrayList<SourceState>()
        states(search, q("c"), over)
        assertEquals(listOf<SourceState>(SourceState.Unavailable), over)
        assertEquals(0, source.connects["c"] ?: 0)
        assertEquals(2, registry.liveParameterStreams())

        first.cancel()
        val retry = ArrayList<SourceState>()
        states(search, q("c"), retry)
        assertEquals(listOf("result for c"), titles(retry.last()))
        // An already shared query is never refused, even at the cap.
        val again = ArrayList<SourceState>()
        states(search, q("b"), again)
        assertEquals(listOf("result for b"), titles(again.last()))
    }

    @Test
    fun lensesWithDifferentQueriesEvaluateIndependentlyThroughTheProvider() {
        val provider =
            SourceBackedLensResultProvider(
                FakeSourceRegistry(listOf(source)),
                AsyncLensEvaluator(Executor { it.run() }),
            ) { LensEvaluationContext(0L) }
        val mail = ArrayList<LensOutput>()
        val maps = ArrayList<LensOutput>()
        val plain = ArrayList<LensOutput>()
        provider.observe(Lens(listOf(search), parameters = mapOf(search to q("mail")))) { mail += it }
        provider.observe(Lens(listOf(search), parameters = mapOf(search to q("maps")))) { maps += it }
        provider.observe(Lens(listOf(search))) { plain += it }

        fun first(outputs: List<LensOutput>) = (outputs.last().result as LensResult.Flat).items.map { it.title }
        assertEquals(listOf("result for mail"), first(mail))
        assertEquals(listOf("result for maps"), first(maps))
        assertEquals(listOf("default"), first(plain))
    }

    @Test
    fun seededRandomSubscribeCancelNeverExceedsTheCapAndLeaksNothing() {
        repeat(SEEDS) { seed ->
            val random = Random(seed)
            val fake = FakeParameterizedSource()
            val reg = SharedSourceRegistry(FakeSourceRegistry(listOf(fake)), maxParameterStreams = CAP)
            val held = ArrayList<Pair<String, SourceSubscription>>()
            repeat(STEPS) {
                if (held.isNotEmpty() && random.nextInt(3) == 0) {
                    held.removeAt(random.nextInt(held.size)).second.cancel()
                } else {
                    val text = "q${random.nextInt(QUERIES)}"
                    held += text to reg.source(SourceIds.SEARCH, q(text)).subscribe { }
                }
                assertTrue(reg.liveParameterStreams() <= CAP, "seed $seed")
                assertTrue(fake.live.values.all { it <= 1 }, "one upstream per query, seed $seed")
                assertEquals(reg.liveParameterStreams(), fake.live.count { it.value == 1 }, "seed $seed")
            }
            held.forEach { it.second.cancel() }
            assertEquals(0, fake.liveUpstreams(), "seed $seed")
            assertEquals(0, reg.liveParameterStreams(), "seed $seed")
        }
    }

    @Test
    fun theQueryIsNotInAnyStringFormOfTheRegistryOrItsStreams() {
        val sentinel = "zq-sentinel-4412"
        val stream: ItemSource = registry.source(search, q(sentinel))
        stream.subscribe { }
        assertFalse(sentinel in registry.toString())
        assertFalse(sentinel in stream.toString())
    }

    private companion object {
        const val SEEDS = 60
        const val STEPS = 40
        const val CAP = 3
        const val QUERIES = 6
    }
}
