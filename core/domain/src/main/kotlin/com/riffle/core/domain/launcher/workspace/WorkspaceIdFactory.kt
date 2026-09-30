package com.riffle.core.domain.launcher.workspace

import java.util.UUID

/** Produces fresh, unique id strings for workspaces and containers. Injected so tests are deterministic. */
fun interface WorkspaceIdFactory {
    fun next(): String

    companion object {
        val Random = WorkspaceIdFactory { UUID.randomUUID().toString() }
    }
}
