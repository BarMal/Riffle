@file:Suppress("ForbiddenImport")

package com.riffle.app.launcher

import com.riffle.app.launcher.workspace.WorkspaceBootstrap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/** How long the launcher waits for the stored workspaces before its first frame (design 1.2). */
internal const val WORKSPACE_STARTUP_READ_MILLIS = 500L

/**
 * The one bounded blocking read of the workspace store (S9, design 1.2), once per process, beside the home layout's
 * (`openHomeLayoutsAtStartup`): the home is the active workspace, so without it the first frame would show the
 * migrated home for a moment. It waits at most [timeoutMillis] on [Dispatchers.IO]. A read that fails is handled by
 * the repository (it holds the seeded defaults and stops writing); on a timeout the view model's asynchronous load
 * finishes the job, and until then the home shows the migrated workspace. Seeds the Nova default for a layout with
 * nothing stored, never overwriting what is stored ([WorkspaceBootstrap.seed]). This file holds the only sanctioned
 * use of `runBlocking` for workspaces.
 */
internal fun loadWorkspacesAtStartup(
    repository: CachedWorkspaceRepository,
    timeoutMillis: Long = WORKSPACE_STARTUP_READ_MILLIS,
) {
    runBlocking(Dispatchers.IO) { withTimeoutOrNull(timeoutMillis) { repository.initialize(WorkspaceBootstrap::seed) } }
}
