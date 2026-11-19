package com.riffle.app.launcher

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.riffle.app.R

/** The check an item wears while it is ticked in Edit page (the multi-select). */
@Composable
internal fun SelectionBadge(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .size(SELECTION_BADGE_DP.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(SELECTION_BADGE_ICON_DP.dp),
        )
    }
}

/** While editing, an item says whether it is ticked (selection state, announced) as well as showing the check. */
@Composable
internal fun Modifier.selectionSemantics(
    isEditing: Boolean,
    isSelected: Boolean,
): Modifier {
    if (!isEditing) return this
    val state =
        stringResource(if (isSelected) R.string.home_selection_selected else R.string.home_selection_not_selected)
    return semantics {
        selected = isSelected
        stateDescription = state
    }
}

/** What a tap does while editing, for assistive technology: ticks or unticks the item. */
@Composable
internal fun selectionClickLabel(
    isEditing: Boolean,
    isSelected: Boolean,
): String? =
    if (isEditing) {
        stringResource(
            if (isSelected) R.string.home_selection_deselect_action else R.string.home_selection_select_action,
        )
    } else {
        null
    }

private const val SELECTION_BADGE_DP = 22
private const val SELECTION_BADGE_ICON_DP = 16

/** Removing the ticked items asks once, however many there are. */
@Composable
internal fun ConfirmRemoveSelectedDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.home_selection_remove_title)) },
        text = {
            Text(
                text = pluralStringResource(R.plurals.home_selection_remove_message, count, count),
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag(HOME_SELECTION_REMOVE_CONFIRM_TEST_TAG),
            ) { Text(text = stringResource(R.string.action_remove)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

internal const val HOME_SELECTION_REMOVE_CONFIRM_TEST_TAG = "home-selection-remove-confirm"
internal const val HOME_SELECTION_BAR_TEST_TAG = "home-selection-bar"
internal const val HOME_SELECTION_COUNT_TEST_TAG = "home-selection-count"
internal const val HOME_SELECTION_SELECT_ALL_TEST_TAG = "home-selection-select-all"
internal const val HOME_SELECTION_CLEAR_TEST_TAG = "home-selection-clear"
internal const val HOME_SELECTION_REMOVE_TEST_TAG = "home-selection-remove"
internal const val HOME_SELECTION_FOLDER_TEST_TAG = "home-selection-folder"

/**
 * The selection row of the edit bar: "Select all on this page" always; with something ticked its count (announced as it
 * changes) at the start, and Clear, Remove from Home and, for apps only, Make a folder grouped at the end. The actions
 * wrap onto further lines on a narrow window (aligned to the end) rather than scrolling out of reach, and the count
 * stays on the first line. Every target is at least 48 dp high.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun HomeSelectionBar(
    selectedCount: Int,
    canMakeFolder: Boolean,
    onAction: (LauncherShellAction) -> Unit,
) {
    val countText = stringResource(R.string.home_selection_count, selectedCount)
    // One flowing row: the count takes the room the first line has left (so the first action stands at the end), and
    // the actions that do not fit wrap onto the next lines, each aligned to the end.
    FlowRow(
        modifier = Modifier.testTag(HOME_SELECTION_BAR_TEST_TAG).fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        if (selectedCount > 0) {
            Text(
                text = countText,
                style = MaterialTheme.typography.labelLarge,
                modifier =
                    Modifier
                        .weight(1f)
                        .testTag(HOME_SELECTION_COUNT_TEST_TAG)
                        .heightIn(min = 48.dp)
                        .wrapContentHeight(Alignment.CenterVertically)
                        .padding(horizontal = 8.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        SelectionBarButton(R.string.home_selection_select_all, HOME_SELECTION_SELECT_ALL_TEST_TAG, true) {
            onAction(LauncherShellAction.SelectAllOnHomePage)
        }
        if (selectedCount > 0) {
            SelectionBarButton(R.string.home_selection_clear, HOME_SELECTION_CLEAR_TEST_TAG, true) {
                onAction(LauncherShellAction.ClearHomeSelection)
            }
            SelectionBarButton(R.string.menu_remove_from_home, HOME_SELECTION_REMOVE_TEST_TAG, true) {
                onAction(LauncherShellAction.ConfirmRemoveSelectedHomeItems)
            }
            SelectionBarButton(R.string.home_selection_make_folder, HOME_SELECTION_FOLDER_TEST_TAG, canMakeFolder) {
                onAction(LauncherShellAction.MakeFolderOfSelected())
            }
        }
    }
}

@Composable
private fun SelectionBarButton(
    @StringRes label: Int,
    tag: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.testTag(tag).heightIn(min = 48.dp),
    ) { Text(text = stringResource(label)) }
}
