package com.riffle.app.launcher

import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.exclusions.LayoutExclusionRules

/**
 * The backup flow's view of the stored workspace data, kept as an interface so the flow stays testable
 * without a device. The current* functions return null when nothing is stored (the section is then omitted
 * from the backup); the restore* functions replace the stored value and must not throw.
 */
interface WorkspaceBackupPort {
    /** Starts loading stored data without blocking; called when the user taps Export, before the picker opens. */
    fun prepareExport() = Unit

    fun currentWorkspaceSet(): WorkspaceSet?

    fun currentExclusions(): LayoutExclusionRules?

    fun restoreWorkspaceSet(set: WorkspaceSet)

    fun restoreExclusions(rules: LayoutExclusionRules)
}
