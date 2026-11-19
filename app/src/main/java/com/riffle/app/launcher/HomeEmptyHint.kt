package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.home.HomeArrangement

internal const val EMPTY_HOME_HINT_TEST_TAG = "empty-home-hint"
internal const val EMPTY_HOME_HINT_ADD_TEST_TAG = "empty-home-hint-add"
internal const val EMPTY_HOME_HINT_DISMISS_TEST_TAG = "empty-home-hint-dismiss"

/** Whether Home has nothing placed on any page (the Dock does not count). */
internal val LauncherShellState.homeIsEmpty: Boolean get() = homeLayout.pages.all { it.items.isEmpty() }

/**
 * The empty-Home hint ("Home is empty. Add all apps"): on Home, once, for an existing user whose Home holds nothing
 * and who has apps to add. Never shown again once dismissed or once Home has held an item (the shell records that), in
 * Simple mode (Home is generated) or while the Home-role card is up.
 */
internal fun LauncherShellState.shouldShowEmptyHomeHint(): Boolean =
    destination == ShellDestination.HOME &&
        !shouldShowSetupCard &&
        !launcherSettings.home.emptyHomeHintDismissed &&
        !launcherSettings.features.simpleMode &&
        homeIsEmpty &&
        HomeArrangement.missingApps(homeLayout, installedApps).isNotEmpty()

@Composable
internal fun EmptyHomeHintCard(
    onAddAll: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.testTag(EMPTY_HOME_HINT_TEST_TAG),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.home_empty_hint),
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.bodyMedium,
            )
            FilledTonalButton(modifier = Modifier.testTag(EMPTY_HOME_HINT_ADD_TEST_TAG), onClick = onAddAll) {
                Text(stringResource(R.string.home_empty_hint_add_all))
            }
            IconButton(modifier = Modifier.testTag(EMPTY_HOME_HINT_DISMISS_TEST_TAG), onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.home_empty_hint_dismiss),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * The empty-Home hint and the transient "Added N apps" with Undo that its button leaves behind. Records (once) that
 * Home has held an item, so the hint never comes back after that.
 */
@Composable
internal fun BoxScope.HomeFillOverlay(
    state: LauncherShellState,
    onAction: (LauncherShellAction) -> Unit,
    onHomeHeldAnItem: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalContext.current.resources
    val undo = stringResource(R.string.stream_undo)
    val dismissed = state.launcherSettings.home.emptyHomeHintDismissed
    val hasItems = !state.homeIsEmpty
    LaunchedEffect(hasItems, dismissed) {
        if (hasItems && !dismissed) onHomeHeldAnItem()
    }
    if (state.shouldShowEmptyHomeHint()) {
        val before = state.homeLayout
        val count = HomeArrangement.missingApps(before, state.installedApps).size
        EmptyHomeHintCard(
            modifier = Modifier.align(Alignment.Center).windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp),
            onAddAll = {
                onAction(LauncherShellAction.AddAllAppsToHome)
                scope.announceWithUndo(
                    snackbar = snackbar,
                    message = resources.getQuantityString(R.plurals.home_added_apps, count, count),
                    undoLabel = undo,
                ) { onAction(LauncherShellAction.UndoHomeBulkEdit(before)) }
            },
            onDismiss = { onAction(LauncherShellAction.DismissEmptyHomeHint) },
        )
    }
    SnackbarHost(
        hostState = snackbar,
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = 96.dp),
    )
}
