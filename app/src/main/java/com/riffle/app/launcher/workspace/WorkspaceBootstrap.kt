package com.riffle.app.launcher.workspace

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.preset.PresetInstaller

/**
 * What the workspace repository holds after its first read: whatever is stored, kept exactly as it is,
 * plus the Nova-style default preset for every device class with nothing stored. Nothing here writes; the
 * seeded layouts are persisted with the first save.
 */
internal object WorkspaceBootstrap {
    fun seed(stored: WorkspaceSet?): WorkspaceSet =
        PresetInstaller.withDefaultsFor(stored ?: WorkspaceSet(), HomeLayoutDeviceClass.entries)
}
