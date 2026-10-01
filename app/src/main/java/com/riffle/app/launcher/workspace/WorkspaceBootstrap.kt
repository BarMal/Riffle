package com.riffle.app.launcher.workspace

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.preset.PresetInstaller
import com.riffle.core.domain.launcher.workspace.preset.WorkspacePresets

/**
 * What the workspace repository holds after its first read: whatever is stored, kept exactly as it is,
 * plus the Nova-style default preset for every device class with nothing stored. Nothing here writes; the
 * seeded layouts are persisted with the first save. A freshly seeded default records the preset it came from
 * (so Settings can offer "Reset to preset" on it); stored workspaces are never touched.
 */
internal object WorkspaceBootstrap {
    fun seed(stored: WorkspaceSet?): WorkspaceSet {
        val base = stored ?: WorkspaceSet()
        val seeded = PresetInstaller.withDefaultsFor(base, HomeLayoutDeviceClass.entries)
        return seeded.layouts.keys
            .filterNot { it in base.layouts }
            .fold(seeded) { set, deviceClass ->
                set.update(deviceClass) { layout ->
                    layout.replace(layout.activeId) { it.copy(presetId = WorkspacePresets.DEFAULT_ID) }
                }
            }
    }
}
