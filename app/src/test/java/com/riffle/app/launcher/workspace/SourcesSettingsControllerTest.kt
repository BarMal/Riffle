package com.riffle.app.launcher.workspace

import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.SourceState
import com.riffle.core.domain.launcher.workspace.container.SharedSourceRegistry
import com.riffle.core.domain.launcher.workspace.settings.InMemoryDisabledSourcesStore
import com.riffle.core.domain.launcher.workspace.settings.SourceStatus
import com.riffle.core.domain.launcher.workspace.settings.SourceStatusMonitor
import com.riffle.core.domain.launcher.workspace.settings.StoredSourceEnablement
import com.riffle.core.domain.launcher.workspace.sources.EnablementSourceRegistry
import com.riffle.core.domain.launcher.workspace.testing.FakeItemSource
import com.riffle.core.domain.launcher.workspace.testing.FakeSourceRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourcesSettingsControllerTest {
    private val apps = FakeItemSource(SourceDescriptor(SourceIds.ALL_APPS), SourceState.Ready(emptyList()))
    private val notifications =
        FakeItemSource(SourceDescriptor(SourceIds.NOTIFICATIONS), SourceState.PermissionRequired)
    private val raw = FakeSourceRegistry(listOf(apps, notifications))
    private val store = InMemoryDisabledSourcesStore()
    private val enablement = StoredSourceEnablement(store)
    private val shared = SharedSourceRegistry(EnablementSourceRegistry(raw, enablement))
    private val controller =
        SourcesSettingsController(
            monitorFor = { onChange ->
                SourceStatusMonitor(shared, raw.descriptors().map { it.id }, onChange)
            },
            enablement = enablement,
            descriptors = { raw.descriptors() },
        )

    @Test
    fun nothingIsSubscribedUntilThePageOpensAndEverythingIsReleasedWhenItCloses() {
        assertEquals(0, apps.observerCount)

        controller.open()
        controller.open()
        assertEquals(1, apps.observerCount)
        assertEquals(1, notifications.observerCount)

        controller.close()
        controller.close()
        assertEquals(0, apps.observerCount)
        assertEquals(0, notifications.observerCount)
        assertTrue(controller.statuses.value.isEmpty())
    }

    @Test
    fun rowsShowEachSourcesRealStatus() {
        controller.open()

        val rows = controller.rows(controller.statuses.value, controller.disabled.value)

        assertEquals(SourceStatus.READY, rows.first { it.id == SourceIds.ALL_APPS }.status)
        assertEquals(SourceStatus.NEEDS_PERMISSION, rows.first { it.id == SourceIds.NOTIFICATIONS }.status)
    }

    @Test
    fun aSourceCanBeSharedWithAContainerWithoutAnExtraUpstream() {
        val container = shared.source(SourceIds.ALL_APPS).subscribe { }

        controller.open()
        assertEquals(1, apps.observerCount)
        controller.close()
        assertEquals(1, apps.observerCount)
        container.cancel()
        assertEquals(0, apps.observerCount)
    }

    @Test
    fun turningASourceOffIsPersistedStopsReadingItAndReadsOff() {
        controller.open()

        controller.setEnabled(SourceIds.ALL_APPS, false)

        assertEquals(setOf(SourceIds.ALL_APPS.value), store.stored)
        assertEquals(setOf(SourceIds.ALL_APPS), controller.disabled.value)
        assertEquals(0, apps.observerCount)
        assertEquals(SourceStatus.OFF, controller.statuses.value[SourceIds.ALL_APPS])
        val row =
            controller.rows(
                controller.statuses.value,
                controller.disabled.value,
            ).first { it.id == SourceIds.ALL_APPS }
        assertEquals(SourceStatus.OFF, row.status)
        assertEquals(false, row.enabled)

        controller.setEnabled(SourceIds.ALL_APPS, true)

        assertEquals(emptySet<String>(), store.stored)
        assertEquals(1, apps.observerCount)
        assertEquals(SourceStatus.READY, controller.statuses.value[SourceIds.ALL_APPS])
    }

    @Test
    fun theDisabledSetIsReadFromTheStoreAtStart() {
        val restored = StoredSourceEnablement(InMemoryDisabledSourcesStore(setOf(SourceIds.RSS.value)))
        val fresh =
            SourcesSettingsController(
                monitorFor = { onChange -> SourceStatusMonitor(shared, emptyList(), onChange) },
                enablement = restored,
                descriptors = { raw.descriptors() },
            )

        assertEquals(setOf(SourceIds.RSS), fresh.disabled.value)
    }
}
