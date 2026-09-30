package com.riffle.core.domain.launcher.workspace

/**
 * Durable store for the [WorkspaceSet]. Platform-facing: implementations live in the app layer.
 * [load] returns null when nothing is stored yet (callers then run [WorkspaceMigration.ensureMigrated]).
 */
interface WorkspaceRepository {
    fun load(): WorkspaceSet?

    fun save(set: WorkspaceSet)
}
