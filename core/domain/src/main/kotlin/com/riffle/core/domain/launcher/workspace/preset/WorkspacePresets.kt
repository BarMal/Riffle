package com.riffle.core.domain.launcher.workspace.preset

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceCopy
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory
import com.riffle.core.domain.launcher.workspace.WorkspaceSet

/**
 * The built-in presets. Plain data: no I/O, no clock, no randomness. Ids inside a preset are stable
 * and deterministic (`preset:<id>:<posture>:...`); they never reach a user's workspaces because every
 * install assigns fresh ones.
 */
object WorkspacePresets {
    const val DEFAULT_ID = "nova"

    val nova: WorkspacePreset = NovaPreset.preset()
    val ios: WorkspacePreset = IosPreset.preset()
    val timeScape: WorkspacePreset = TimeScapePreset.preset()
    val niagara: WorkspacePreset = NiagaraPreset.preset()
    val kvaesitso: WorkspacePreset = KvaesitsoPreset.preset()

    /** Catalog order: the default first. */
    val all: List<WorkspacePreset> = listOf(nova, ios, timeScape, niagara, kvaesitso)

    val default: WorkspacePreset = nova

    fun byId(id: String): WorkspacePreset? = all.firstOrNull { it.id == id }

    /** The Nova-style workspace for [deviceClass]'s posture, with the preset's stable ids. Not for storing as is. */
    fun defaultFor(deviceClass: HomeLayoutDeviceClass): Workspace = default.variant(PresetPosture.of(deviceClass))

    /**
     * A new, fully independent workspace from [preset]'s [posture] variant: fresh workspace and
     * container ids from [ids], everything else equal. The result is an ordinary workspace; nothing
     * links it back to the preset, so cloning and editing it needs no special casing.
     */
    fun installPreset(
        preset: WorkspacePreset,
        posture: PresetPosture,
        ids: WorkspaceIdFactory = WorkspaceIdFactory.Random,
    ): Workspace = WorkspaceCopy.withFreshIds(preset.variant(posture), ids)

    fun installPreset(
        preset: WorkspacePreset,
        deviceClass: HomeLayoutDeviceClass,
        ids: WorkspaceIdFactory = WorkspaceIdFactory.Random,
    ): Workspace = installPreset(preset, PresetPosture.of(deviceClass), ids)
}

/**
 * Minimal helpers for putting installed presets into the existing [WorkspaceSet] APIs. They add no
 * persistence and change no migration behaviour; WS6/WS7 decide when to call them.
 */
object PresetInstaller {
    /** A layout holding only the Nova-style default, for an install with no stored workspaces. */
    fun newInstallLayout(
        deviceClass: HomeLayoutDeviceClass,
        ids: WorkspaceIdFactory = WorkspaceIdFactory.Random,
    ): LayoutWorkspaces =
        LayoutWorkspaces.single(WorkspacePresets.installPreset(WorkspacePresets.default, deviceClass, ids))

    /** [set] with a Nova-style default for every device class that has nothing stored; stored ones are untouched. */
    fun withDefaultsFor(
        set: WorkspaceSet,
        deviceClasses: Collection<HomeLayoutDeviceClass>,
        ids: WorkspaceIdFactory = WorkspaceIdFactory.Random,
    ): WorkspaceSet =
        deviceClasses.fold(set) { acc, deviceClass ->
            if (deviceClass in acc.layouts) acc else acc.withLayout(deviceClass, newInstallLayout(deviceClass, ids))
        }

    /** Adds an installed [preset] to [deviceClass]'s workspaces (materializing the default if none stored). */
    fun addPreset(
        set: WorkspaceSet,
        deviceClass: HomeLayoutDeviceClass,
        preset: WorkspacePreset,
        activate: Boolean = false,
        ids: WorkspaceIdFactory = WorkspaceIdFactory.Random,
    ): WorkspaceSet =
        set.update(deviceClass) { it.add(WorkspacePresets.installPreset(preset, deviceClass, ids), activate) }
}
