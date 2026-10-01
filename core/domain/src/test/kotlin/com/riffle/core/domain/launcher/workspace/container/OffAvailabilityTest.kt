package com.riffle.core.domain.launcher.workspace.container

import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceState
import com.riffle.core.domain.launcher.workspace.lens.AsyncLensEvaluator
import com.riffle.core.domain.launcher.workspace.lens.LensEvaluationContext
import com.riffle.core.domain.launcher.workspace.settings.InMemoryDisabledSourcesStore
import com.riffle.core.domain.launcher.workspace.settings.StoredSourceEnablement
import com.riffle.core.domain.launcher.workspace.sources.EnablementSourceRegistry
import com.riffle.core.domain.launcher.workspace.testing.FakeItemSource
import com.riffle.core.domain.launcher.workspace.testing.FakeSourceRegistry
import com.riffle.core.domain.launcher.workspace.testing.fakeItem
import java.util.concurrent.Executor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The OFF status (a source the user turned off) through availability and the real lens provider. */
class OffAvailabilityTest {
    private val ready = SourceState.Ready(emptyList())

    @Test
    fun offRanksAfterLoadingAndPermissionButBeforeUnavailable() {
        val expected =
            listOf(
                listOf(ready, SourceState.Off) to LensAvailability.READY,
                listOf(SourceState.Loading, SourceState.Off) to LensAvailability.LOADING,
                listOf(SourceState.PermissionRequired, SourceState.Off) to LensAvailability.PERMISSION_REQUIRED,
                listOf(SourceState.Unavailable, SourceState.Off) to LensAvailability.OFF,
                listOf(SourceState.Off) to LensAvailability.OFF,
                listOf(SourceState.Unavailable) to LensAvailability.UNAVAILABLE,
            )
        expected.forEach { (states, availability) ->
            assertEquals(availability, LensOutput.availabilityOf(states), states.toString())
        }
    }

    @Test
    fun offOutputHasNoResult() {
        assertEquals(LensAvailability.OFF, LensOutput.Off.availability)
        assertEquals(null, LensOutput.Off.result)
    }

    @Test
    fun aLensOverADisabledSourceIsOffAndNeverEvaluates() {
        val id = SourceId("a")
        val source = FakeItemSource(SourceDescriptor(id), SourceState.Ready(listOf(fakeItem("x", "a"))))
        val enablement = StoredSourceEnablement(InMemoryDisabledSourcesStore(setOf("a")))
        val registry = EnablementSourceRegistry(FakeSourceRegistry(listOf(source)), enablement)
        val evaluations = ArrayList<Runnable>()
        val executor = Executor { evaluations += it }
        val provider =
            SourceBackedLensResultProvider(registry, AsyncLensEvaluator(executor)) { LensEvaluationContext(0L) }
        val outputs = ArrayList<LensOutput>()

        val handle = provider.observe(Lens(listOf(id))) { outputs += it }

        assertEquals(LensOutput.Off, outputs.last())
        assertEquals(0, source.observerCount)
        assertTrue(evaluations.isEmpty())

        enablement.setEnabled(id, true)
        evaluations.toList().forEach { it.run() }
        assertEquals(LensAvailability.READY, outputs.last().availability)

        enablement.setEnabled(id, false)
        assertEquals(LensOutput.Off, outputs.last())
        handle.cancel()
        assertEquals(0, source.observerCount)
    }
}
