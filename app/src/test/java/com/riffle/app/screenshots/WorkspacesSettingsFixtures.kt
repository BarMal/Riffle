package com.riffle.app.screenshots

import com.riffle.app.launcher.SettingsLayoutDeviceTab
import com.riffle.app.launcher.settingsLayoutDeviceTabs
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.preset.WorkspacePreset
import com.riffle.core.domain.launcher.workspace.preset.WorkspacePresets
import com.riffle.core.domain.launcher.workspace.settings.LayoutFallbackNotice
import com.riffle.core.domain.launcher.workspace.settings.SourceRow
import com.riffle.core.domain.launcher.workspace.settings.SourceStatus
import com.riffle.core.domain.launcher.workspace.settings.SourcesSettingsPlanner
import com.riffle.core.domain.launcher.workspace.settings.WorkspacesSettingsModel
import com.riffle.core.domain.launcher.workspace.settings.WorkspacesSettingsPlanner

/** Fixed workspaces and sources for the Settings > Workspaces and Settings > Sources screenshots (fakes only). */
internal object WorkspacesSettingsFixtures {
    val phone = HomeLayoutDeviceClass.PHONE
    val foldable = HomeLayoutDeviceClass.FOLDABLE

    val tabs: List<SettingsLayoutDeviceTab> = settingsLayoutDeviceTabs(setOf(phone, foldable))

    private var counter = 0
    private val ids = WorkspaceIdFactory { "fixture-${counter++}" }

    private fun installed(
        preset: WorkspacePreset,
        id: String,
        name: String = preset.name,
        recordPreset: Boolean = true,
    ) = WorkspacePresets.installPreset(preset, phone, ids)
        .copy(id = WorkspaceId(id), name = name, presetId = preset.id.takeIf { recordPreset })

    private val nova = installed(WorkspacePresets.nova, "nova")
    private val timeScape = installed(WorkspacePresets.timeScape, "timescape")
    private val work = installed(WorkspacePresets.niagara, "work", name = "Work", recordPreset = false)

    val workId = work.id
    val timeScapeId = timeScape.id

    /** Three workspaces on the phone layout (TimeScape active, Nova default, Work custom) and one on the foldable. */
    val set =
        WorkspaceSet(
            mapOf(
                phone to LayoutWorkspaces(listOf(nova, timeScape, work), timeScape.id, nova.id),
                foldable to LayoutWorkspaces.single(installed(WorkspacePresets.nova, "fold", name = "Nova (unfolded)")),
            ),
        )

    private val available = listOf(phone, foldable)

    val compact: WorkspacesSettingsModel =
        WorkspacesSettingsPlanner.plan(
            set,
            phone,
            current = phone,
            available = available,
        )

    /** Viewing the foldable layout from the phone: Edit is unavailable and the explanation says why. */
    val otherLayout: WorkspacesSettingsModel =
        WorkspacesSettingsPlanner.plan(set, foldable, current = phone, available = available)

    val withFallback: WorkspacesSettingsModel =
        compact.copy(fallback = LayoutFallbackNotice("Index", "Nova", listOf(ExpressionKind.INDEX), 0))

    val lastWorkspace: WorkspacesSettingsModel =
        WorkspacesSettingsPlanner.plan(
            WorkspaceSet(mapOf(phone to LayoutWorkspaces.single(nova))),
            phone,
            current = phone,
            available = available,
        )

    // ---- Sources ----

    private val statuses =
        mapOf(
            SourceIds.ALL_APPS to SourceStatus.READY,
            SourceIds.RECENT_APPS to SourceStatus.NEEDS_PERMISSION,
            SourceIds.QUICK_ACTIONS to SourceStatus.READY,
            SourceIds.NOTIFICATIONS to SourceStatus.NEEDS_PERMISSION,
            SourceIds.MEDIA to SourceStatus.NEEDS_PERMISSION,
            SourceIds.RSS to SourceStatus.READY,
            SourceIds.SEARCH to SourceStatus.LOADING,
        )

    val sources: List<SourceRow> =
        SourcesSettingsPlanner.plan(
            descriptors = SourceIds.BUILT_IN.map { SourceDescriptor(it) },
            statuses = statuses,
            disabled = setOf(SourceIds.CALENDAR),
        )

    val sourceIds: List<SourceId> = sources.map { it.id }
}
