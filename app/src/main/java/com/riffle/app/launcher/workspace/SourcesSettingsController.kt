package com.riffle.app.launcher.workspace

import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceSubscription
import com.riffle.core.domain.launcher.workspace.settings.SourceEnablement
import com.riffle.core.domain.launcher.workspace.settings.SourceRow
import com.riffle.core.domain.launcher.workspace.settings.SourceStatus
import com.riffle.core.domain.launcher.workspace.settings.SourceStatusMonitor
import com.riffle.core.domain.launcher.workspace.settings.SourcesSettingsPlanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The Sources page's state holder. No Android types.
 *
 * While the page is open ([open] to [close]) it holds one status subscription per source through the
 * [monitorFor] factory (the runtime's shared registry, so each source keeps a single upstream however many
 * containers also read it) and follows [enablement]; closing the page releases every one of them. Only
 * statuses are kept, never items. The access requests the rows offer are the existing explicit flows and are
 * the page's callbacks: nothing here asks for a permission.
 */
internal class SourcesSettingsController(
    private val monitorFor: ((Map<SourceId, SourceStatus>) -> Unit) -> SourceStatusMonitor,
    private val enablement: SourceEnablement,
    private val descriptors: () -> List<SourceDescriptor>,
) {
    private val mutableStatuses = MutableStateFlow<Map<SourceId, SourceStatus>>(emptyMap())
    private val mutableDisabled = MutableStateFlow(enablement.disabledIds())
    private var monitor: SourceStatusMonitor? = null
    private var observing: SourceSubscription? = null

    val statuses: StateFlow<Map<SourceId, SourceStatus>> = mutableStatuses.asStateFlow()
    val disabled: StateFlow<Set<SourceId>> = mutableDisabled.asStateFlow()

    /** Starts reading statuses. Idempotent. */
    fun open() {
        if (monitor != null) return
        mutableDisabled.value = enablement.disabledIds()
        observing = enablement.observe { mutableDisabled.value = enablement.disabledIds() }
        monitor = monitorFor { next -> mutableStatuses.value = next }.also { it.start() }
    }

    /** Releases every subscription. Idempotent. */
    fun close() {
        monitor?.stop()
        monitor = null
        observing?.cancel()
        observing = null
        mutableStatuses.value = emptyMap()
    }

    fun rows(
        statuses: Map<SourceId, SourceStatus>,
        disabled: Set<SourceId>,
    ): List<SourceRow> = SourcesSettingsPlanner.plan(descriptors(), statuses, disabled)

    fun setEnabled(
        id: SourceId,
        enabled: Boolean,
    ) = enablement.setEnabled(id, enabled)
}
