package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.editor.EditorChoiceRow
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.workspace.DockPresentation
import com.riffle.core.domain.launcher.workspace.WorkspaceNewApps
import com.riffle.core.domain.launcher.workspace.pool.NewAppPlacement
import com.riffle.core.domain.launcher.workspace.settings.WorkspacesSettingsMessage

/**
 * "Workspace dock": whether this workspace shows the layout's dock, on which edge and at which icon size (11.2). Pins
 * are never chosen here (they are the layout's). Hidden stays listed but disabled, with the reason, while no gesture
 * opens the workspace menu (11.4).
 */
@Composable
internal fun WorkspaceDockDialog(
    dialog: WorkspaceDialog.Dock,
    onDismiss: () -> Unit,
    onSave: (DockPresentation?) -> Unit,
) {
    val current = dialog.current ?: DockPresentation()
    var hidden by remember { mutableStateOf(current.hidden && dialog.canHide) }
    var edge by remember { mutableStateOf(current.edge) }
    var iconSize by remember { mutableStateOf(current.iconSizeDp) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(WorkspaceDockSettingsText.dockTitle(dialog.name)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
            ) {
                Text(WorkspaceDockSettingsText.DOCK_BODY, style = MaterialTheme.typography.bodyMedium)
                DialogHeading(WorkspaceDockSettingsText.SHOW_HEADING)
                EditorChoiceRow(
                    title = WorkspaceDockSettingsText.SHOWN,
                    selected = !hidden,
                    enabled = true,
                    onClick = { hidden = false },
                )
                EditorChoiceRow(
                    title = WorkspaceDockSettingsText.HIDDEN,
                    selected = hidden,
                    enabled = dialog.canHide,
                    onClick = { hidden = true },
                    reason = WorkspaceDockSettingsText.HIDDEN_NEEDS_GESTURE.takeUnless { dialog.canHide },
                )
                DialogHeading(WorkspaceDockSettingsText.EDGE_HEADING)
                WorkspaceDockSettingsText.edgeChoices.forEach { choice ->
                    EditorChoiceRow(
                        title = WorkspaceDockSettingsText.edgeLabel(choice),
                        selected = edge == choice,
                        enabled = !hidden,
                        onClick = { edge = choice },
                    )
                }
                DialogHeading(WorkspaceDockSettingsText.SIZE_HEADING)
                WorkspaceDockSettingsText.iconSizeChoices.forEach { size ->
                    EditorChoiceRow(
                        title = WorkspaceDockSettingsText.iconSizeLabel(size),
                        selected = iconSize == size,
                        enabled = !hidden,
                        onClick = { iconSize = size },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(DockPresentation(hidden, edge, DockPresentation.clampedIconSize(iconSize))) },
            ) {
                Text(WorkspacesSettingsText.SAVE)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(WorkspacesSettingsText.CANCEL) } },
    )
}

/**
 * "New apps": whether a newly installed app also gets a home icon in this workspace, and whether a page is added when
 * every page is full (9.8). "Use the preset's default" clears the workspace's own choice.
 */
@Composable
internal fun WorkspaceNewAppsDialog(
    dialog: WorkspaceDialog.NewApps,
    onDismiss: () -> Unit,
    onSave: (WorkspaceNewApps?) -> Unit,
) {
    var followPreset by remember { mutableStateOf(dialog.followsPreset && dialog.presetName != null) }
    var placement by remember { mutableStateOf(dialog.current.placement) }
    var autoPages by remember { mutableStateOf(dialog.current.autoPages) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(WorkspaceDockSettingsText.newAppsTitle(dialog.name)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
            ) {
                Text(WorkspaceDockSettingsText.NEW_APPS_BODY, style = MaterialTheme.typography.bodyMedium)
                dialog.presetName?.let { preset ->
                    EditorChoiceRow(
                        title = WorkspaceDockSettingsText.presetDefault(preset),
                        selected = followPreset,
                        enabled = true,
                        onClick = { followPreset = !followPreset },
                        multiple = true,
                    )
                }
                NewAppPlacement.entries.forEach { choice ->
                    EditorChoiceRow(
                        title = WorkspaceDockSettingsText.placementLabel(choice),
                        selected = placement == choice,
                        enabled = !followPreset,
                        onClick = { placement = choice },
                        supporting = WorkspaceDockSettingsText.placementSupporting(choice),
                    )
                }
                EditorChoiceRow(
                    title = WorkspaceDockSettingsText.AUTO_PAGES,
                    selected = autoPages,
                    enabled = !followPreset && placement == NewAppPlacement.HOME_AND_FINDER,
                    onClick = { autoPages = !autoPages },
                    supporting = WorkspaceDockSettingsText.AUTO_PAGES_SUPPORTING,
                    multiple = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(if (followPreset) null else WorkspaceNewApps(placement, autoPages)) }) {
                Text(WorkspacesSettingsText.SAVE)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(WorkspacesSettingsText.CANCEL) } },
    )
}

@Composable
private fun DialogHeading(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
}

/** Wording for the dock and new-app choices of a workspace. Plain strings so JVM tests can check them. */
internal object WorkspaceDockSettingsText {
    const val DOCK = "Dock..."
    const val NEW_APPS = "New apps..."
    const val DOCK_BODY =
        "Choose how the dock looks while this workspace is shown. The pinned apps are the same in every workspace."
    const val SHOW_HEADING = "Dock"
    const val SHOWN = "Show the dock"
    const val HIDDEN = "Hide the dock"
    const val HIDDEN_NEEDS_GESTURE =
        "To hide the dock, first set a gesture to open the workspace menu in Settings > Gestures."
    const val EDGE_HEADING = "Edge"
    const val SIZE_HEADING = "Icon size"
    const val FOLLOW_EDGE = "Same as the layout's dock"
    const val FOLLOW_SIZE = "Same as the layout's dock"
    const val NEW_APPS_BODY = "What happens here when you install an app. It is always in All apps."
    const val AUTO_PAGES = "Add a page when the pages are full"
    const val AUTO_PAGES_SUPPORTING = "Otherwise a new app gets no home icon here while every page is full"

    /** Null is "follow the layout's dock". */
    val edgeChoices: List<DockPosition?> =
        listOf(null, DockPosition.BOTTOM, DockPosition.LEFT, DockPosition.RIGHT, DockPosition.TOP)

    /** Null is "follow the layout's dock"; the rest lie within the dock's icon size range. */
    val iconSizeChoices: List<Int?> = listOf(null, 32, 40, 48, 56)

    fun dockTitle(name: String): String = "Dock for \"$name\""

    fun newAppsTitle(name: String): String = "New apps in \"$name\""

    fun edgeLabel(edge: DockPosition?): String =
        when (edge) {
            null -> FOLLOW_EDGE
            DockPosition.BOTTOM -> "Bottom"
            DockPosition.LEFT -> "Left"
            DockPosition.RIGHT -> "Right"
            DockPosition.TOP -> "Top"
        }

    fun iconSizeLabel(size: Int?): String = if (size == null) FOLLOW_SIZE else "$size dp"

    fun presetDefault(presetName: String): String = "Use the $presetName preset's default"

    fun placementLabel(placement: NewAppPlacement): String =
        when (placement) {
            NewAppPlacement.FINDER_ONLY -> "Only in All apps"
            NewAppPlacement.HOME_AND_FINDER -> "Also add an icon to the home pages"
        }

    fun placementSupporting(placement: NewAppPlacement): String =
        when (placement) {
            NewAppPlacement.FINDER_ONLY -> "Add it to a page yourself"
            NewAppPlacement.HOME_AND_FINDER -> "In the first free cell, like iOS"
        }

    /** The row's supporting summary of a dock override, as the design's list item reads it (11.4). */
    fun dockSummary(presentation: DockPresentation?): String =
        when {
            presentation == null || presentation.followsShared -> "Dock: same as the layout"
            presentation.hidden -> "Dock: hidden"
            else ->
                listOfNotNull(
                    presentation.edge?.let { "${edgeLabel(it)} edge" },
                    presentation.iconSizeDp?.let { "$it dp" },
                ).joinToString(separator = ", ", prefix = "Dock: ")
        }

    fun message(message: WorkspacesSettingsMessage.DockAndNewAppsMessage): String =
        when (message) {
            is WorkspacesSettingsMessage.DockChanged ->
                "\"${message.name}\" ${dockSummary(message.presentation).replaceFirstChar { it.lowercase() }}"
            WorkspacesSettingsMessage.DockHiddenNeedsMenuGesture -> HIDDEN_NEEDS_GESTURE
            is WorkspacesSettingsMessage.NewAppsChanged -> {
                val what =
                    when (message.newApps.placement) {
                        NewAppPlacement.FINDER_ONLY -> "new apps go only to All apps"
                        NewAppPlacement.HOME_AND_FINDER -> "new apps also get a home icon"
                    }
                val preset = if (message.followsPreset) " (the preset's default)" else ""
                "In \"${message.name}\", $what$preset"
            }
        }
}
