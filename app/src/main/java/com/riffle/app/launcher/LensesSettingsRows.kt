package com.riffle.app.launcher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.LensId
import com.riffle.core.domain.launcher.workspace.LibraryProblem
import com.riffle.core.domain.launcher.workspace.settings.LensRow

internal enum class LensRowActionKind {
    RENAME,
    DUPLICATE,
    COPY,
    DELETE,
}

/** One action on a lens row. [layout] is the target of a [LensRowActionKind.COPY]. */
internal data class LensRowAction(
    val kind: LensRowActionKind,
    val label: String,
    val layout: HomeLayoutDeviceClass? = null,
    val enabled: Boolean = true,
    val disabledReason: String? = null,
)

/**
 * The actions a lens row offers, in menu order. Duplicate is disabled with the reason when the layout is full;
 * there is one Copy entry per other layout, each naming it. These are the overflow menu entries and the TalkBack
 * custom actions of the row.
 */
internal fun lensRowActions(
    layoutFull: Boolean,
    copyTargets: List<HomeLayoutDeviceClass>,
): List<LensRowAction> =
    buildList {
        add(LensRowAction(LensRowActionKind.RENAME, LensesSettingsText.RENAME))
        add(
            LensRowAction(
                kind = LensRowActionKind.DUPLICATE,
                label = LensesSettingsText.DUPLICATE,
                enabled = !layoutFull,
                disabledReason = LensesMessageText.problem(LibraryProblem.LIBRARY_FULL).takeIf { layoutFull },
            ),
        )
        copyTargets.forEach { target ->
            add(LensRowAction(LensRowActionKind.COPY, LensesSettingsText.copyToLabel(target), target))
        }
        add(LensRowAction(LensRowActionKind.DELETE, LensesSettingsText.DELETE))
    }

internal fun lensRowTestTag(id: LensId): String = "lens-row-${id.value}"

internal fun lensRowMenuTestTag(id: LensId): String = "lens-row-menu-${id.value}"

/**
 * One saved lens in the list. The whole row is one focusable element: tapping it opens the lens; its spoken summary
 * (name, sources, shape, usage, position) is its content description, and every row action is also a TalkBack custom
 * action. The overflow menu is a separate 48 dp button, so the actions are reachable without TalkBack too.
 */
@Composable
@Suppress("LongParameterList")
internal fun LensListItem(
    row: LensRow,
    position: Int,
    total: Int,
    actions: List<LensRowAction>,
    highlighted: Boolean,
    onClick: () -> Unit,
    onRun: (LensRowAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spoken = LensesSettingsText.rowSpokenSummary(row, position, total)
    val accessibilityActions =
        actions.filter { it.enabled }.map { action ->
            CustomAccessibilityAction(action.label) {
                onRun(action)
                true
            }
        }
    ListItem(
        modifier =
            modifier
                .fillMaxWidth()
                .testTag(lensRowTestTag(row.id))
                .clip(RiffleShapes.medium)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) {
                    contentDescription = spoken
                    selected = highlighted
                    customActions = accessibilityActions
                },
        colors =
            ListItemDefaults.colors(
                containerColor =
                    if (highlighted) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            ),
        headlineContent = { Text(text = row.name, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = {
            Text(
                text = LensesSettingsText.rowSummary(row),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = { LensOverflowMenu(row = row, actions = actions, onRun = onRun) },
    )
}

@Composable
private fun LensOverflowMenu(
    row: LensRow,
    actions: List<LensRowAction>,
    onRun: (LensRowAction) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier =
                Modifier
                    .testTag(lensRowMenuTestTag(row.id))
                    .semantics { contentDescription = LensesSettingsText.moreActionsDescription(row.name) },
        ) {
            Icon(imageVector = Icons.Filled.MoreVert, contentDescription = null)
        }
        RiffleContextMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { LensActionLabel(action) },
                    enabled = action.enabled,
                    onClick = {
                        expanded = false
                        onRun(action)
                    },
                )
            }
        }
    }
}

/** A menu entry: its label and, when it cannot be used, why. */
@Composable
private fun LensActionLabel(action: LensRowAction) {
    Column(modifier = Modifier.fillMaxWidth()) {
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
