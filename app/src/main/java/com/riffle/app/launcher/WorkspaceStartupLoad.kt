@file:Suppress("ForbiddenImport")

package com.riffle.app.launcher

import com.riffle.app.launcher.workspace.WorkspaceBootstrap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/** How long the launcher waits for the stored workspaces before its first frame (design 1.2). */
internal const val WORKSPACE_STARTUP_READ_MILLIS = 500L

/**
 * The one bounded blocking read of the workspace store (S9, design 1.2), once per process, beside the home layout's
 * (`openHomeLayoutsAtStartup`): the home is the active workspace, so without it the first frame would show the
 * migrated home for a moment.
 *
 * The read, the decode and the Nova seed ([WorkspaceBootstrap.seed], never overwriting what is stored) run off the
 * calling thread ([Dispatchers.Default]); the caller waits for them at most [timeoutMillis], a hard cap even when
 * the work itself cannot be interrupted. A load still running at the deadline is not cancelled: it completes in the
 * background and fills the repository like any other load (its version changes, and the view model follows it).
 * Returns whether the workspaces were loaded in time. A read that fails is handled by the repository (it holds the
 * seeded defaults and stops writing). This file holds the only sanctioned use of `runBlocking` for workspaces.
 */
internal fun loadWorkspacesAtStartup(
    repository: CachedWorkspaceRepository,
    timeoutMillis: Long = WORKSPACE_STARTUP_READ_MILLIS,
): Boolean {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val load = scope.launch { repository.initialize(WorkspaceBootstrap::seed) }
    // join: a failed load is the repository's business, never a crash at startup.
    runBlocking { withTimeoutOrNull(timeoutMillis) { load.join() } }
    return repository.load() != null
}
