package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.exclusions.ExclusionsSettingsText
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRuleId
import com.riffle.core.domain.launcher.workspace.settings.ExclusionRuleRow
import com.riffle.core.domain.launcher.workspace.settings.ExclusionsSettingsAction
import com.riffle.core.domain.launcher.workspace.settings.TextRuleDraft
import com.riffle.core.domain.launcher.workspace.settings.TextRuleProblem

internal enum class ExclusionRowActionKind {
    TOGGLE,
    APPLY_TO_ALL,
    DELETE,
}

/** One action on a rule row. */
internal data class ExclusionRowAction(
    val kind: ExclusionRowActionKind,
    val label: String,
)

/**
 * The actions a rule row offers, in menu order. Turn on/off is also what tapping the row does; Apply to all layouts
 * is offered only when there is another layout to apply to.
 */
internal fun exclusionRowActions(
    row: ExclusionRuleRow,
    canApplyToOtherLayouts: Boolean,
): List<ExclusionRowAction> =
    listOfNotNull(
        ExclusionRowAction(
            ExclusionRowActionKind.TOGGLE,
            if (row.enabled) ExclusionsSettingsText.TURN_OFF else ExclusionsSettingsText.TURN_ON,
        ),
        ExclusionRowAction(ExclusionRowActionKind.APPLY_TO_ALL, ExclusionsSettingsText.APPLY_TO_ALL)
            .takeIf { canApplyToOtherLayouts },
        ExclusionRowAction(ExclusionRowActionKind.DELETE, ExclusionsSettingsText.DELETE),
    )

/** The dialogs the page can have open. Deleting always asks first. */
internal sealed interface ExclusionDialog {
    data class ConfirmDelete(val id: ExclusionRuleId) : ExclusionDialog

    data object AddText : ExclusionDialog
}

/** What the page can ask for; the stateful wrapper wires them to the controller and shell. */
internal data class ExclusionsPageCallbacks(
    val onAction: (ExclusionsSettingsAction) -> Unit = {},
    val onSelectLayout: (HomeLayoutDeviceClass) -> Unit = {},
    val problemWith: (TextRuleDraft) -> TextRuleProblem? = { null },
)

internal fun exclusionRowTestTag(id: ExclusionRuleId): String = "exclusion-row-${id.value}"

/**
 * One rule. The whole row is one focusable switch with a spoken summary (what it matches, its group, source, count
 * and position); tapping it turns the rule on or off. Apply to all layouts and Delete are in the overflow menu and
 * are also TalkBack custom actions on the row. Deleting only opens the confirmation.
 */
@Composable
@Suppress("LongParameterList")
internal fun ExclusionRuleListItem(
    row: ExclusionRuleRow,
    position: Int,
    total: Int,
    actions: List<ExclusionRowAction>,
    onToggle: (Boolean) -> Unit,
    onRun: (ExclusionRowActionKind) -> Unit,
) {
    val spoken = ExclusionsSettingsText.rowSpokenSummary(row, position, total)
    val accessibilityActions =
        actions.map { action ->
            CustomAccessibilityAction(action.label) {
                onRun(action.kind)
                true
            }
        }
    ListItem(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(exclusionRowTestTag(row.id))
                .clip(RiffleShapes.medium)
                .toggleable(value = row.enabled, role = Role.Switch, onValueChange = onToggle)
                .semantics(mergeDescendants = true) {
                    contentDescription = spoken
                    customActions = accessibilityActions
                },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = {
            Text(
                text = ExclusionsSettingsText.title(row),
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        supportingContent = { ExclusionRuleDetails(row) },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Switch(checked = row.enabled, onCheckedChange = null)
                ExclusionOverflowMenu(
                    row = row,
                    actions = actions.filter { it.kind != ExclusionRowActionKind.TOGGLE },
                    onRun = onRun,
                )
            }
        },
    )
}

@Composable
private fun ExclusionRuleDetails(row: ExclusionRuleRow) {
    Column(verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xxs)) {
        if (row.label != null) {
            Text(
                text = row.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = ExclusionsSettingsText.badges(row).joinToString(separator = " · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ExclusionsSettingsText.statusLine(row)?.let { status ->
            Text(text = status, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ExclusionOverflowMenu(
    row: ExclusionRuleRow,
    actions: List<ExclusionRowAction>,
    onRun: (ExclusionRowActionKind) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier =
                Modifier.semantics {
                    contentDescription = ExclusionsSettingsText.moreActionsDescription(row)
                },
        ) {
            Icon(imageVector = Icons.Filled.MoreVert, contentDescription = null)
        }
        RiffleContextMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(text = action.label) },
                    onClick = {
                        expanded = false
                        onRun(action.kind)
                    },
                )
            }
        }
    }
}
