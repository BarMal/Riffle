package com.riffle.app.launcher.workspace

import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.SourceCapability
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceState
import com.riffle.core.domain.launcher.workspace.container.LensAvailability
import com.riffle.core.domain.launcher.workspace.container.LensOutput
import com.riffle.core.domain.launcher.workspace.testing.FakeItemSource
import com.riffle.core.domain.launcher.workspace.testing.FakeSourceRegistry
import com.riffle.core.domain.launcher.workspace.testing.fakeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executor

class WorkspaceRuntimeTest {
    private val id = SourceId("fake")
    private val source =
        FakeItemSource(SourceDescriptor(id, setOf(SourceCapability.LIVE)), SourceState.Ready(listOf(fakeItem("a"))))
    private val registry = FakeSourceRegistry(listOf(source))
    private val direct = Executor { it.run() }

    @Test
    fun buildingTheProviderSubscribesToNothing() {
        workspaceLensProvider(registry, direct)

        assertEquals(0, source.observerCount)
    }

    @Test
    fun observingALensSubscribesOnceAndCancellingDetaches() {
        val provider = workspaceLensProvider(registry, direct)
        val outputs = mutableListOf<LensOutput>()

        val first = provider.observe(Lens(sources = listOf(id))) { outputs += it }
        val second = provider.observe(Lens(sources = listOf(id))) { outputs += it }

        assertEquals(1, source.observerCount)
        assertTrue(outputs.any { it.availability == LensAvailability.READY })
        first.cancel()
        second.cancel()
        assertEquals(0, source.observerCount)
    }
}
