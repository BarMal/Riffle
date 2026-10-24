package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.LauncherViewModeAvailability
import com.riffle.core.domain.launcher.home.LibraryExitTrigger

/**
 * Library, the app drawer, may have been left by [trigger] (Decision 10, #1243).
 *
 * Retired by S9 (design section 3.2, closes #1323): workspaces replace the Home/Library mode pair, and nothing a
 * return does ever changes the shown mode or the active workspace. Only an explicit choice (the workspace menu,
 * Settings > Workspaces) does; which *page* a return lands on is the Returning to Home setting (`HomeReturn`). The
 * stored "After leaving Library" value is ignored (its codec keeps reading and writing it for backup compatibility),
 * so this returns the state unchanged for every trigger.
 */
@Suppress("UnusedParameter", "UNUSED_PARAMETER") // The call sites keep their shape; the decision is gone.
internal fun LauncherShellState.withLibraryLeft(
    trigger: LibraryExitTrigger,
    homeLayoutRepository: HomeLayoutRepository,
    viewModeAvailability: LauncherViewModeAvailability,
): LauncherShellState = this
