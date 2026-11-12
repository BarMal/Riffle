package com.riffle.app.launcher.workspace

import com.riffle.core.domain.launcher.workspace.WorkspaceLayoutFold
import com.riffle.core.domain.launcher.workspace.WorkspaceSet

/**
 * What the workspace repository holds after its first read: whatever is stored, kept exactly as it is (the landscape
 * phone layout folded into the phone layout, N10, and nothing else changed). A layout with nothing stored holds
 * nothing: its home is the migrated arrangement ([com.riffle.core.domain.launcher.workspace.WorkspaceHome]). Nothing
 * here writes, and nothing in the shell rewrites the stored workspaces afterwards: they stay as they are, decodable,
 * so switching can return later (ADR 0003, decision 3).
 */
internal object WorkspaceBootstrap {
    fun seed(stored: WorkspaceSet?): WorkspaceSet = stored?.let(WorkspaceLayoutFold::fold) ?: WorkspaceSet()
}
