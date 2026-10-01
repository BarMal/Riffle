package com.riffle.core.domain.launcher.workspace.settings

import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.SourceState
import com.riffle.core.domain.launcher.workspace.container.SharedSourceRegistry
import com.riffle.core.domain.launcher.workspace.sources.EnablementSourceRegistry
import com.riffle.core.domain.launcher.workspace.testing.FakeItemSource
import com.riffle.core.domain.launcher.workspace.testing.FakeSourceRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourcesSettingsTest {
    private val descriptors = SourceIds.BUILT_IN.map { SourceDescriptor(it) }

    @Test
    fun everyBuiltInSourceHasANameAndADescription() {
        SourceIds.BUILT_IN.forEach { id ->
            assertTrue(SourceCatalog.isKnown(id), id.value)
            val info = SourceCatalog.infoFor(id)
            assertTrue(info.title.isNotBlank() && info.description.isNotBlank(), id.value)
        }
        assertEquals("custom", SourceCatalog.infoFor(SourceId("custom")).title)
    }

    @Test
    fun statusFollowsTheSourceState() {
        assertEquals(SourceStatus.LOADING, SourceStatus.of(SourceState.Loading))
        assertEquals(SourceStatus.READY, SourceStatus.of(SourceState.Ready(emptyList())))
        assertEquals(SourceStatus.NEEDS_PERMISSION, SourceStatus.of(SourceState.PermissionRequired))
        assertEquals(SourceStatus.OFF, SourceStatus.of(SourceState.Off))
        assertEquals(SourceStatus.UNAVAILABLE, SourceStatus.of(SourceState.Unavailable))
    }

    @Test
    fun plannerListsEveryBuiltInSourceInOrderWithStatusAndEnabledFlag() {
        val rows =
            SourcesSettingsPlanner.plan(
                descriptors = descriptors.reversed(),
                statuses = mapOf(SourceIds.ALL_APPS to SourceStatus.READY),
                disabled = setOf(SourceIds.CALENDAR),
            )

        assertEquals(SourceIds.BUILT_IN, rows.map { it.id })
        assertEquals(SourceStatus.READY, rows.first { it.id == SourceIds.ALL_APPS }.status)
        assertEquals(SourceStatus.LOADING, rows.first { it.id == SourceIds.MEDIA }.status)
        val calendar = rows.first { it.id == SourceIds.CALENDAR }
        assertEquals(SourceStatus.OFF, calendar.status)
        assertFalse(calendar.enabled)
    }

    @Test
    fun aDisabledSourceReadsOffEvenIfAStaleStatusRemains() {
        val rows =
            SourcesSettingsPlanner.plan(
                descriptors,
                mapOf(SourceIds.RSS to SourceStatus.READY),
                setOf(SourceIds.RSS),
            )

        assertEquals(SourceStatus.OFF, rows.first { it.id == SourceIds.RSS }.status)
    }

    @Test
    fun aSourceTheRegistryLacksHasNoRowAndUnknownOnesFollowTheBuiltIns() {
        val rows =
            SourcesSettingsPlanner.plan(
                listOf(SourceDescriptor(SourceId("x.custom")), SourceDescriptor(SourceIds.SEARCH)),
                emptyMap(),
                emptySet(),
            )

        assertEquals(listOf(SourceIds.SEARCH, SourceId("x.custom")), rows.map { it.id })
    }

    @Test
    fun monitorKeepsOneSubscriptionPerSourceAndReleasesAllOnStop() {
        val a = FakeItemSource(SourceDescriptor(SourceIds.ALL_APPS), SourceState.Ready(emptyList()))
        val n = FakeItemSource(SourceDescriptor(SourceIds.NOTIFICATIONS), SourceState.PermissionRequired)
        val registry = FakeSourceRegistry(listOf(a, n))
        val seen = ArrayList<Map<SourceId, SourceStatus>>()
        val monitor =
            SourceStatusMonitor(registry, listOf(SourceIds.ALL_APPS, SourceIds.NOTIFICATIONS, SourceIds.ALL_APPS)) {
                seen += it
            }

        monitor.start()
        monitor.start()

        assertEquals(1, a.observerCount)
        assertEquals(1, n.observerCount)
        assertEquals(SourceStatus.READY, seen.last()[SourceIds.ALL_APPS])
        assertEquals(SourceStatus.NEEDS_PERMISSION, seen.last()[SourceIds.NOTIFICATIONS])

        n.emit(SourceState.Ready(emptyList()))
        assertEquals(SourceStatus.READY, seen.last()[SourceIds.NOTIFICATIONS])

        monitor.stop()
        val count = seen.size
        assertEquals(0, a.observerCount)
        assertEquals(0, n.observerCount)
        n.emit(SourceState.Unavailable)
        assertEquals(count, seen.size)
    }

    @Test
    fun monitorMarksSourcesTheRegistryLacksUnavailable() {
        val seen = ArrayList<Map<SourceId, SourceStatus>>()
        val monitor = SourceStatusMonitor(FakeSourceRegistry(emptyList()), listOf(SourceIds.RSS)) { seen += it }

        monitor.start()

        assertEquals(SourceStatus.UNAVAILABLE, seen.last()[SourceIds.RSS])
    }

    @Test
    fun monitorThroughTheSharedRegistryAddsNoUpstreamOfItsOwn() {
        val apps = FakeItemSource(SourceDescriptor(SourceIds.ALL_APPS), SourceState.Ready(emptyList()))
        val enablement = StoredSourceEnablement(InMemoryDisabledSourcesStore())
        val shared = SharedSourceRegistry(EnablementSourceRegistry(FakeSourceRegistry(listOf(apps)), enablement))
        val container = shared.source(SourceIds.ALL_APPS).subscribe { }
        val monitor = SourceStatusMonitor(shared, listOf(SourceIds.ALL_APPS)) { }

        monitor.start()
        assertEquals(1, apps.observerCount)

        monitor.stop()
        assertEquals(1, apps.observerCount)
        container.cancel()
        assertEquals(0, apps.observerCount)
    }

    @Test
    fun monitorSeesATurnedOffSourceAsOffWithoutTouchingIt() {
        val apps = FakeItemSource(SourceDescriptor(SourceIds.ALL_APPS), SourceState.Ready(emptyList()))
        val enablement = StoredSourceEnablement(InMemoryDisabledSourcesStore(setOf(SourceIds.ALL_APPS.value)))
        val shared = SharedSourceRegistry(EnablementSourceRegistry(FakeSourceRegistry(listOf(apps)), enablement))
        val seen = ArrayList<Map<SourceId, SourceStatus>>()

        SourceStatusMonitor(shared, listOf(SourceIds.ALL_APPS)) { seen += it }.start()

        assertEquals(SourceStatus.OFF, seen.last()[SourceIds.ALL_APPS])
        assertEquals(0, apps.observerCount)
    }
}
