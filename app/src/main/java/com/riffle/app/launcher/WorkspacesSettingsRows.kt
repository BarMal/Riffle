package com.riffle.app.launcher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.workspace.settings.WorkspaceRow

/**
 * One workspace in the list. Compact: tapping the row switches to it and a menu holds the other actions; the
 * same actions are also TalkBack custom actions on the row. Two-pane ([wide]): tapping selects the row for the
 * detail pane and the radio button switches. The whole row is one focusable element with a spoken summary.
 */
@Composable
@Suppress("LongParameterList")
internal fun WorkspaceListItem(
    row: WorkspaceRow,
    actions: List<WorkspaceRowAction>,
    wide: Boolean,
    highlighted: Boolean,
    onClick: () -> Unit,
    onActivate: () -> Unit,
    onRun: (WorkspaceRowActionKind) -> Unit,
) {
    val spoken = WorkspacesSettingsText.rowSpokenSummary(row)
    val accessibilityActions =
        actions.filter { it.enabled }.map { action ->
            CustomAccessibilityAction(action.label) {
                onRun(action.kind)
                true
            }
        }
    val trailing: (@Composable () -> Unit)? =
        if (wide) {
            null
        } else {
            { WorkspaceOverflowMenu(row = row, actions = actions, onRun = onRun) }
        }
    ListItem(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RiffleShapes.medium)
                .clickable(role = Role.RadioButton, onClick = onClick)
                .semantics(mergeDescendants = true) {
                    contentDescription = spoken
                    selected = row.isActive
                    customActions = accessibilityActions
                },
        colors =
            ListItemDefaults.colors(
                containerColor =
                    if (highlighted) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            ),
        leadingContent = { RadioButton(selected = row.isActive, onClick = if (wide) onActivate else null) },
        headlineContent = { Text(text = row.name, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = {
            Text(
                text = WorkspacesSettingsText.rowSummary(row),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = trailing,
    )
}

@Composable
private fun WorkspaceOverflowMenu(
    row: WorkspaceRow,
    actions: List<WorkspaceRowAction>,
    onRun: (WorkspaceRowActionKind) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier =
                Modifier.semantics {
                    contentDescription = WorkspacesSettingsText.moreActionsDescription(row.name)
                },
        ) {
            Icon(imageVector = Icons.Filled.MoreVert, contentDescription = null)
        }
        RiffleContextMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { WorkspaceActionLabel(action) },
                    enabled = action.enabled,
                    onClick = {
                        expanded = false
                        onRun(action.kind)
                    },
                )
            }
        }
    }
}

/** A menu entry: its label and, when it cannot be used, why. */
@Composable
private fun WorkspaceActionLabel(action: WorkspaceRowAction) {
    Column {
        Text(text = action.label)
        action.disabledReason?.let { reason ->
            Text(
                text = reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The two-pane detail: what the selected workspace is and every action on it, each a 48 dp button. */
@Composable
internal fun WorkspaceDetailPanel(
    row: WorkspaceRow,
    actions: List<WorkspaceRowAction>,
    onActivate: () -> Unit,
    onRun: (WorkspaceRowActionKind) -> Unit,
) {
    SettingsSection(title = row.name) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = RiffleSpacing.l),
            verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xs),
        ) {
            Text(
                text = WorkspacesSettingsText.rowSummary(row),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!row.isActive) {
                TextButton(onClick = onActivate, modifier = Modifier.heightIn(min = RiffleSpacing.xxxl)) {
                    Text(text = WorkspacesSettingsText.SWITCH_TO)
                }
            }
            actions.forEach { action -> WorkspaceDetailAction(action = action, onRun = onRun) }
        }
    }
}

@Composable
private fun WorkspaceDetailAction(
    action: WorkspaceRowAction,
    onRun: (WorkspaceRowActionKind) -> Unit,
) {
    Column {
        TextButton(
            onClick = { onRun(action.kind) },
            enabled = action.enabled,
            modifier = Modifier.heightIn(min = RiffleSpacing.xxxl),
        ) {
            Text(text = action.label)
        }
        action.disabledReason?.let { reason ->
            Text(
                modifier = Modifier.padding(horizontal = RiffleSpacing.m),
                text = reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
