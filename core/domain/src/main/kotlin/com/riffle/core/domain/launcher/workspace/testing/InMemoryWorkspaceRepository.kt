package com.riffle.core.domain.launcher.workspace.testing

import com.riffle.core.domain.launcher.workspace.WorkspaceRepository
import com.riffle.core.domain.launcher.workspace.WorkspaceSet

/** In-memory [WorkspaceRepository] for tests and previews. */
class InMemoryWorkspaceRepository(
    var stored: WorkspaceSet? = null,
) : WorkspaceRepository {
    override fun load(): WorkspaceSet? = stored

    override fun save(set: WorkspaceSet) {
        stored = set
    }
}
