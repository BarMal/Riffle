package com.riffle.core.domain.launcher.workspace.sources

import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceState
import com.riffle.core.domain.launcher.workspace.container.SharedSourceRegistry
import com.riffle.core.domain.launcher.workspace.settings.InMemoryDisabledSourcesStore
import com.riffle.core.domain.launcher.workspace.settings.StoredSourceEnablement
import com.riffle.core.domain.launcher.workspace.testing.FakeItemSource
import com.riffle.core.domain.launcher.workspace.testing.FakeSourceRegistry
import com.riffle.core.domain.launcher.workspace.testing.fakeItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class EnablementSourceRegistryTest {
    private val a = SourceId("a")
    private val b = SourceId("b")
    private val sourceA = FakeItemSource(SourceDescriptor(a), SourceState.Ready(listOf(fakeItem("x", "a"))))
    private val sourceB = FakeItemSource(SourceDescriptor(b), SourceState.PermissionRequired)
    private val store = InMemoryDisabledSourcesStore()
    private val enablement = StoredSourceEnablement(store)
    private val registry = EnablementSourceRegistry(FakeSourceRegistry(listOf(sourceA, sourceB)), enablement)
    private val seen = ArrayList<SourceState>()

    @Test
    fun withNothingDisabledStatesAreTheWrappedSourcesOwn() {
        registry.source(a).subscribe { seen += it }
        assertEquals(sourceA.state, seen.last())
        registry.source(b).subscribe { seen += it }
        assertEquals(SourceState.PermissionRequired, seen.last())

        sourceA.emit(SourceState.Unavailable)
        assertEquals(SourceState.Unavailable, seen.last())
    }

    @Test
    fun aDisabledSourceEmitsOffWithoutEverSubscribingToTheRealSource() {
        enablement.setEnabled(a, false)

        registry.source(a).subscribe { seen += it }

        assertEquals(SourceState.Off, seen.last())
        assertFalse(seen.any { it is SourceState.Ready })
        assertEquals(0, sourceA.observerCount)
    }

    @Test
    fun turningASourceOnConnectsItAndTurningItOffCancelsTheUpstream() {
        enablement.setEnabled(a, false)
        registry.source(a).subscribe { seen += it }

        enablement.setEnabled(a, true)
        assertEquals(1, sourceA.observerCount)
        assertEquals(SourceState.Ready(listOf(fakeItem("x", "a"))), seen.last())

        enablement.setEnabled(a, false)
        assertEquals(0, sourceA.observerCount)
        assertEquals(SourceState.Off, seen.last())
    }

    @Test
    fun otherSourcesAreUnaffectedByTurningOneOff() {
        registry.source(b).subscribe { seen += it }

        enablement.setEnabled(a, false)

        assertEquals(1, sourceB.observerCount)
        assertEquals(SourceState.PermissionRequired, seen.last())
    }

    @Test
    fun observersShareOneUpstreamAndItIsReleasedAfterTheLastLeaves() {
        val first = registry.source(a).subscribe { }
        val second = registry.source(a).subscribe { }
        assertEquals(1, sourceA.observerCount)

        first.cancel()
        assertEquals(1, sourceA.observerCount)
        second.cancel()
        assertEquals(0, sourceA.observerCount)
    }

    @Test
    fun aSourceTheRegistryDoesNotKnowIsUnavailableUnlessTurnedOff() {
        val unknown = SourceId("unknown")
        registry.source(unknown).subscribe { seen += it }
        assertEquals(SourceState.Unavailable, seen.last())

        enablement.setEnabled(unknown, false)
        assertEquals(SourceState.Off, seen.last())
    }

    @Test
    fun theChoiceSurvivesARestart() {
        enablement.setEnabled(a, false)

        val restarted = StoredSourceEnablement(store)

        assertEquals(setOf(a), restarted.disabledIds())
        val fresh = EnablementSourceRegistry(FakeSourceRegistry(listOf(sourceA)), restarted)
        fresh.source(a).subscribe { seen += it }
        assertEquals(SourceState.Off, seen.last())
        assertEquals(0, sourceA.observerCount)
    }

    @Test
    fun sharedRegistryOnTopFollowsEnablementToo() {
        val shared = SharedSourceRegistry(registry)
        shared.source(a).subscribe { seen += it }
        assertEquals(1, sourceA.observerCount)

        enablement.setEnabled(a, false)

        assertEquals(SourceState.Off, seen.last())
        assertEquals(0, sourceA.observerCount)
    }
}
