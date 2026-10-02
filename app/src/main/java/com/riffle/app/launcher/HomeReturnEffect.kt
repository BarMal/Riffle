package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.homeReturnTarget

/**
 * Applies the Returning to Home setting (Restore, First page, Start page) to the standard Home pager.
 *
 * Two moments are handled here: the launcher's first open, and coming back from another app (the activity
 * stopped, then started: Back, Recents, a notification). Pressing the Home button keeps its existing path,
 * `OpenDefaultHome`, which always lands on the first page and closes whatever overlay is open; for the
 * standard shell that page is also what Start page means (see `HomeReturn`).
 *
 * The decision is pure (`homeReturnTarget`) and moves nothing at the default Restore or when the drawer, search,
 * Settings or an edit mode is open. The move is an ordinary `SelectHomePage`, so the pager follows the selected
 * page the way it does for every page change, and an instant scroll replaces the animation under reduced motion.
 * While the Workspaces preview is [suppressed] it owns the screen and applies the same setting itself.
 */
@Composable
internal fun HomeReturnEffect(
    state: LauncherShellState,
    suppressed: Boolean,
    onAction: (LauncherShellAction) -> Unit,
) {
    val latestState = rememberUpdatedState(state)
    val latestSuppressed = rememberUpdatedState(suppressed)
    val latestOnAction = rememberUpdatedState(onAction)
    val apply: () -> Unit = {
        if (!latestSuppressed.value) {
            latestState.value.homeReturnTarget()?.let { page ->
                latestOnAction.value(LauncherShellAction.SelectHomePage(page))
            }
        }
    }
    // Survives a rotation, so only a genuinely new launcher process or task counts as a first open.
    var firstOpenHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!firstOpenHandled) {
            firstOpenHandled = true
            apply()
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var stopped = false
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> stopped = true
                    Lifecycle.Event.ON_START -> {
                        if (stopped) apply()
                        stopped = false
                    }
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
