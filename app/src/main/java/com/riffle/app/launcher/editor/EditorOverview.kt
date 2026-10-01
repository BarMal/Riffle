package com.riffle.app.launcher.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.riffle.app.launcher.designsystem.RiffleElevation
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.PageHost
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.editor.FlowMode
import com.riffle.core.domain.launcher.workspace.editor.WorkspaceEdit

/**
 * The workspace as an editable list: name, pages (reorder, change content, remove), widgets inside grid
 * pages (move, change content, remove) and the dock's dynamic section. Reordering and moving are buttons, so
 * every edit is reachable without dragging. Edits go through the domain, which refuses invalid ones.
 */
@Composable
internal fun EditorOverview(
    workspace: Workspace,
    onAction: (EditorAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(RiffleSpacing.m),
    ) {
        NameField(workspace.name, onRename = { onAction(EditorAction.Apply(WorkspaceEdit.Rename(it))) })
        EditorSectionTitle(EditorText.PAGES)
        if (workspace.pages.isEmpty()) {
            Text(EditorText.EMPTY_WORKSPACE, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        workspace.pages.forEachIndexed { index, page ->
            PageCard(page, index, workspace.pages.size, onAction)
        }
        Button(onClick = { onAction(EditorAction.StartFlow(FlowMode.Add)) }) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(EditorText.ADD_PAGE, modifier = Modifier.padding(start = RiffleSpacing.s))
        }
        DockCard(workspace, onAction)
    }
}

@Composable
private fun NameField(
    name: String,
    onRename: (String) -> Unit,
) {
    var text by remember(name) { mutableStateOf(name) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(EditorText.NAME_LABEL) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { onRename(text) }, enabled = text.isNotBlank() && text.trim() != name) {
            Text("Rename")
        }
    }
}

@Composable
private fun PageCard(
    page: PageHost,
    index: Int,
    count: Int,
    onAction: (EditorAction) -> Unit,
) {
    val name = EditorDescribe.pageName(index)
    EditorCard {
        Text(name, style = MaterialTheme.typography.titleSmall)
        Text(
            EditorDescribe.page(page),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(Icons.Filled.KeyboardArrowUp, "${EditorText.MOVE_UP}: $name", index > 0) {
                onAction(EditorAction.Apply(WorkspaceEdit.MovePage(page.id, index - 1)))
            }
            IconAction(Icons.Filled.KeyboardArrowDown, "${EditorText.MOVE_DOWN}: $name", index < count - 1) {
                onAction(EditorAction.Apply(WorkspaceEdit.MovePage(page.id, index + 1)))
            }
            if (!(page is PageContainer && page.content is PageContent.WidgetGrid)) {
                IconAction(Icons.Filled.Edit, "${EditorText.EDIT_CONTENT}: $name", true) {
                    onAction(EditorAction.StartFlow(FlowMode.EditPage(page.id)))
                }
            }
            IconAction(Icons.Filled.Delete, "${EditorText.REMOVE}: $name", true) {
                onAction(EditorAction.Apply(WorkspaceEdit.RemovePage(page.id)))
            }
        }
        if (page is PageContainer && page.content is PageContent.WidgetGrid) {
            GridWidgets(page, page.content as PageContent.WidgetGrid, onAction)
        }
    }
}

@Composable
private fun GridWidgets(
    page: PageContainer,
    grid: PageContent.WidgetGrid,
    onAction: (EditorAction) -> Unit,
) {
    grid.placements.forEach { placement ->
        val label = EditorDescribe.widget(placement)
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(
            horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val id = placement.widget.id
            val move = { dc: Int, dr: Int ->
                onAction(
                    EditorAction.Apply(
                        WorkspaceEdit.MoveWidget(page.id, id, placement.column + dc, placement.row + dr),
                    ),
                )
            }
            IconAction(
                Icons.Filled.KeyboardArrowLeft,
                "${EditorText.MOVE_LEFT}: $label",
                placement.column > 0,
            ) { move(-1, 0) }
            IconAction(Icons.Filled.KeyboardArrowRight, "${EditorText.MOVE_RIGHT}: $label", true) { move(1, 0) }
            IconAction(Icons.Filled.KeyboardArrowUp, "${EditorText.MOVE_UP}: $label", placement.row > 0) { move(0, -1) }
            IconAction(Icons.Filled.KeyboardArrowDown, "${EditorText.MOVE_DOWN}: $label", true) { move(0, 1) }
            IconAction(Icons.Filled.Edit, "${EditorText.EDIT_CONTENT}: $label", true) {
                onAction(EditorAction.StartFlow(FlowMode.EditWidget(page.id, id)))
            }
            IconAction(Icons.Filled.Delete, "${EditorText.REMOVE}: $label", true) {
                onAction(EditorAction.Apply(WorkspaceEdit.RemoveWidget(page.id, id)))
            }
        }
    }
    OutlinedButton(onClick = { onAction(EditorAction.StartFlow(FlowMode.Add)) }) { Text(EditorText.ADD_WIDGET_HERE) }
}

@Composable
private fun DockCard(
    workspace: Workspace,
    onAction: (EditorAction) -> Unit,
) {
    val section = workspace.dock.dynamicSection
    EditorCard {
        Text(EditorText.DOCK_SECTION, style = MaterialTheme.typography.titleSmall)
        Text(
            EditorDescribe.dock(section),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.s)) {
            OutlinedButton(onClick = { onAction(EditorAction.StartFlow(FlowMode.EditDock)) }) {
                Text(EditorText.SET_DOCK_SECTION)
            }
            if (section != null) {
                TextButton(onClick = { onAction(EditorAction.Apply(WorkspaceEdit.SetDockSection(null))) }) {
                    Text(EditorText.REMOVE_DOCK_SECTION)
                }
            }
        }
    }
}

@Composable
private fun EditorCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RiffleShapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = RiffleElevation.level1,
    ) {
        Column(
            modifier = Modifier.padding(RiffleSpacing.m),
            verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xs),
        ) {
            content()
        }
    }
}

@Composable
private fun IconAction(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled) { Icon(icon, contentDescription = description) }
}
