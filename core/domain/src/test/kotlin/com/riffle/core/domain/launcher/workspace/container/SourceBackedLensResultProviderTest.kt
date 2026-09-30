package com.riffle.core.domain.launcher.workspace.container

import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensResult
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceState
import com.riffle.core.domain.launcher.workspace.lens.AsyncLensEvaluator
import com.riffle.core.domain.launcher.workspace.lens.LensEvaluationContext
import com.riffle.core.domain.launcher.workspace.testing.FakeItemSource
import com.riffle.core.domain.launcher.workspace.testing.FakeSourceRegistry
import com.riffle.core.domain.launcher.workspace.testing.fakeItem
import java.util.concurrent.Executor
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SourceBackedLensResultProviderTest {
    private class QueueExecutor : Executor {
        val tasks = ArrayDeque<Runnable>()

        override fun execute(command: Runnable) {
            tasks.addLast(command)
        }

        fun runAll() {
            while (tasks.isNotEmpty()) tasks.removeFirst().run()
        }
    }

    private val a = SourceId("a")
    private val b = SourceId("b")
    private val sourceA = FakeItemSource(SourceDescriptor(a))
    private val sourceB = FakeItemSource(SourceDescriptor(b))
    private val registry = FakeSourceRegistry(listOf(sourceA, sourceB))
    private val queue = QueueExecutor()
    private val outputs = ArrayList<LensOutput>()

    private fun provider(executor: Executor = queue) =
        SourceBackedLensResultProvider(registry, AsyncLensEvaluator(executor)) { LensEvaluationContext(0L) }

    private fun titles(output: LensOutput): List<String?> = (output.result as LensResult.Flat).items.map { it.title }

    @Test
    fun loadingSourceYieldsLoadingThenReadyOnceItemsArrive() {
        val handle = provider().observe(Lens(listOf(a))) { outputs += it }
        assertEquals(listOf(LensOutput.Loading), outputs)

        sourceA.emitItems(listOf(fakeItem("x", "a")))
        queue.runAll()

        assertEquals(LensAvailability.READY, outputs.last().availability)
        assertEquals(listOf("x"), titles(outputs.last()))
        handle.cancel()
    }

    @Test
    fun permissionAndUnavailableStatesAreReportedWithoutEvaluating() {
        provider().observe(Lens(listOf(a))) { outputs += it }
        sourceA.emit(SourceState.PermissionRequired)
        assertEquals(LensOutput.PermissionRequired, outputs.last())
        sourceA.emit(SourceState.Unavailable)
        assertEquals(LensOutput.Unavailable, outputs.last())
        assertTrue(queue.tasks.isEmpty())
    }

    @Test
    fun unknownSourceReadsAsUnavailable() {
        provider().observe(Lens(listOf(SourceId("missing")))) { outputs += it }
        assertEquals(LensOutput.Unavailable, outputs.last())
    }

    @Test
    fun oneReadySourceMakesTheLensReadyWhileAnotherNeedsPermission() {
        provider().observe(Lens(listOf(a, b))) { outputs += it }
        sourceB.emit(SourceState.PermissionRequired)
        sourceA.emitItems(listOf(fakeItem("x", "a")))
        queue.runAll()
        assertEquals(listOf("x"), titles(outputs.last()))
    }

    @Test
    fun aNewerChangeSupersedesAnInFlightEvaluation() {
        provider().observe(Lens(listOf(a))) { outputs += it }
        sourceA.emitItems(listOf(fakeItem("old", "a")))
        sourceA.emitItems(listOf(fakeItem("new", "a")))
        queue.runAll()

        val ready = outputs.filter { it.availability == LensAvailability.READY }
        assertEquals(1, ready.size)
        assertEquals(listOf("new"), titles(ready.single()))
    }

    @Test
    fun cancelDetachesFromSourcesAndSuppressesTheInFlightEvaluation() {
        val handle = provider().observe(Lens(listOf(a))) { outputs += it }
        sourceA.emitItems(listOf(fakeItem("x", "a")))
        handle.cancel()
        queue.runAll()
        sourceA.emitItems(listOf(fakeItem("y", "a")))
        queue.runAll()

        assertEquals(0, sourceA.observerCount)
        assertEquals(listOf(LensOutput.Loading), outputs)
    }

    @Test
    fun manyLensesShareOneSubscriptionPerSourceAndReleaseItWhenAllAreCancelled() {
        val provider = provider()
        val handles = List(5) { provider.observe(Lens(listOf(a, b))) { } }
        assertEquals(1, sourceA.observerCount)
        assertEquals(1, sourceB.observerCount)

        handles.dropLast(1).forEach { it.cancel() }
        assertEquals(1, sourceA.observerCount)
        handles.last().cancel()
        assertEquals(0, sourceA.observerCount)
        assertEquals(0, sourceB.observerCount)
    }

    @Test
    fun anEvaluatorFailureBecomesUnavailable() {
        val failing = AsyncLensEvaluator(queue, { _, _, _ -> error("boom") })
        SourceBackedLensResultProvider(registry, failing) { LensEvaluationContext(0L) }
            .observe(Lens(listOf(a))) { outputs += it }
        sourceA.emitItems(listOf(fakeItem("x", "a")))
        queue.runAll()
        assertEquals(LensOutput.Unavailable, outputs.last())
    }

    @Test
    fun seededRandomChangesAlwaysEndOnTheLatestItemsAndNeverDeliverAfterCancel() {
        repeat(SEEDS) { seed ->
            val random = Random(seed)
            outputs.clear()
            val handle = provider().observe(Lens(listOf(a))) { outputs += it }
            var latest: List<String> = emptyList()
            repeat(random.nextInt(1, MAX_STEPS)) {
                latest = List(random.nextInt(0, MAX_ITEMS)) { index -> "i$index-${random.nextInt(100)}" }
                sourceA.emitItems(latest.map { fakeItem(it, "a") })
                if (random.nextBoolean()) queue.runAll()
            }
            queue.runAll()
            val last = outputs.last { it.availability == LensAvailability.READY }
            assertEquals(latest.sorted(), titles(last).map { it!! }.sorted(), "seed $seed")

            handle.cancel()
            val countAtCancel = outputs.size
            sourceA.emitItems(listOf(fakeItem("after", "a")))
            queue.runAll()
            assertEquals(countAtCancel, outputs.size, "seed $seed")
        }
    }

    @Test
    fun staticProviderAnswersImmediatelyWithoutSubscribing() {
        val lens = Lens(listOf(a))
        StaticLensResultProvider(LensOutput.PermissionRequired).observe(lens) { outputs += it }
        assertEquals(listOf(LensOutput.PermissionRequired), outputs)
        assertNull(LensOutput.Loading.result)
    }

    private companion object {
        const val SEEDS = 40
        const val MAX_STEPS = 12
        const val MAX_ITEMS = 6
    }
}
