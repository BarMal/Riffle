package com.riffle.app.launcher

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.dock.PairAvailability
import com.riffle.core.domain.launcher.home.AppPairItem
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.withoutAppPairs

/** What [PairAvailability.canSplit] needs to know about the launcher's window. */
internal data class SplitFacts(
    val sdkInt: Int,
    val windowWidthDp: Int,
    val inMultiWindow: Boolean,
) {
    val canSplit: Boolean get() = PairAvailability.canSplit(sdkInt, windowWidthDp, inMultiWindow)
}

/**
 * Overrides the facts read from the device. Null (the default) reads them: the platform SDK, the window's width and
 * whether the launcher is itself in multi-window mode. Tests provide their own to fold and unfold a window.
 */
internal val LocalSplitFacts = compositionLocalOf<SplitFacts?> { null }

/** Whether app pairs are available now (shown, and may be made and launched): read by the pair's menus and drag. */
internal val LocalDockPairsAvailable = compositionLocalOf { false }

@Composable
internal fun currentSplitFacts(): SplitFacts {
    val override = LocalSplitFacts.current
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    return override
        ?: SplitFacts(
            sdkInt = Build.VERSION.SDK_INT,
            windowWidthDp = configuration.screenWidthDp,
            inMultiWindow = context.findActivity()?.isInMultiWindowMode ?: false,
        )
}

/** The state the Dock is drawn from: with the pairs left out when they are not available. */
internal data class DockPairView(
    val available: Boolean,
    val state: LauncherShellState,
    /** The stored pairs that are not drawn; edits that move items by position skip them. */
    val hiddenItemIds: Set<LauncherItemId>,
)

@Composable
internal fun rememberDockPairView(state: LauncherShellState): DockPairView {
    val facts = currentSplitFacts()
    val available = PairAvailability.available(state.launcherSettings.dock.appPairs, facts.canSplit)
    return remember(state, available) {
        if (available) {
            DockPairView(true, state, emptySet())
        } else {
            val dock = state.homeLayout.dock
            DockPairView(
                available = false,
                state = state.copy(homeLayout = state.homeLayout.copy(dock = dock.withoutAppPairs())),
                hiddenItemIds = dock.items.filterIsInstance<AppPairItem>().map { it.id }.toSet(),
            )
        }
    }
}

/** [onAction] for a Dock that hides [hidden] pairs: moves by position count only what the user can see. */
internal fun dockPairAwareActions(
    hidden: Set<LauncherItemId>,
    onAction: (LauncherShellAction) -> Unit,
): (LauncherShellAction) -> Unit =
    if (hidden.isEmpty()) {
        onAction
    } else {
        { action ->
            onAction(
                when (action) {
                    is LauncherShellAction.MoveDockShortcut -> action.copy(hiddenItemIds = hidden)
                    is LauncherShellAction.MoveDockShortcutToIndex -> action.copy(hiddenItemIds = hidden)
                    else -> action
                },
            )
        }
    }
