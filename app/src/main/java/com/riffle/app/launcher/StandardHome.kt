package com.riffle.app.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.HomeEditMode
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeShortcutMoveDirection
import com.riffle.core.domain.launcher.home.LauncherPage

@Composable
fun StandardHome(
    layout: HomeLayout,
    appIconLoader: AppIconLoader,
    onAction: (LauncherShellAction) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HomeToolbar(
            isEditing = layout.editMode is HomeEditMode.EditingPage,
            onAction = onAction,
        )
        WorkspaceGrid(
            page = layout.selectedPage,
            isEditing = layout.editMode is HomeEditMode.EditingPage,
            appIconLoader = appIconLoader,
            onAction = onAction,
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
        )
        PageIndicator(
            pageCount = layout.pages.size,
            selectedPageIndex = layout.selectedPageIndex,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Dock(dock = layout.dock)
    }
}

@Composable
private fun HomeToolbar(
    isEditing: Boolean,
    onAction: (LauncherShellAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            modifier = Modifier.weight(1f),
            onClick = { onAction(LauncherShellAction.OpenSearch) },
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(text = "Search")
        }
        TextButton(onClick = { onAction(LauncherShellAction.OpenAppDrawer) }) {
            Text(text = "Apps")
        }
        TextButton(onClick = { onAction(LauncherShellAction.OpenSettings) }) {
            Text(text = "Settings")
        }
        TextButton(
            onClick = {
                onAction(
                    if (isEditing) {
                        LauncherShellAction.ExitHomeEditMode
                    } else {
                        LauncherShellAction.EnterHomeEditMode
                    },
                )
            },
        ) {
            Text(text = if (isEditing) "Done" else "Edit")
        }
    }
}

@Composable
private fun WorkspaceGrid(
    page: LauncherPage,
    isEditing: Boolean,
    appIconLoader: AppIconLoader,
    onAction: (LauncherShellAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
    ) {
        val grid = page.grid
        repeat(grid.rows) {
            val row = it

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                repeat(grid.columns) { column ->
                    val shortcut = page.shortcutAt(cell = GridCell(column = column, row = row))

                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (shortcut != null) {
                            HomeShortcut(
                                shortcut = shortcut,
                                isEditing = isEditing,
                                appIconLoader = appIconLoader,
                                onAction = onAction,
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun HomeShortcut(
    shortcut: AppShortcutItem,
    isEditing: Boolean,
    appIconLoader: AppIconLoader,
    onAction: (LauncherShellAction) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .clickable(enabled = !isEditing) {
                        onAction(LauncherShellAction.LaunchApp(shortcut.appIdentity))
                    },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LauncherAppIcon(
                identity = shortcut.appIdentity,
                label = shortcut.label,
                iconLoader = appIconLoader,
                modifier = Modifier.size(44.dp),
            )
            Text(
                modifier = Modifier.widthIn(max = 72.dp),
                text = shortcut.label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }

        if (isEditing) {
            MoveShortcutControls(
                shortcut = shortcut,
                onAction = onAction,
            )
            RemoveShortcutButton(
                label = shortcut.label,
                onClick = { onAction(LauncherShellAction.RemoveHomeShortcut(shortcut.id)) },
            )
        }
    }
}

@Composable
private fun BoxScope.MoveShortcutControls(
    shortcut: AppShortcutItem,
    onAction: (LauncherShellAction) -> Unit,
) {
    fun moveShortcut(direction: HomeShortcutMoveDirection) {
        onAction(
            LauncherShellAction.MoveHomeShortcut(
                itemId = shortcut.id,
                direction = direction,
            ),
        )
    }

    MoveShortcutButton(
        label = shortcut.label,
        direction = HomeShortcutMoveDirection.UP,
        text = "U",
        alignment = Alignment.TopCenter,
        onClick = { moveShortcut(HomeShortcutMoveDirection.UP) },
    )
    MoveShortcutButton(
        label = shortcut.label,
        direction = HomeShortcutMoveDirection.DOWN,
        text = "D",
        alignment = Alignment.BottomCenter,
        onClick = { moveShortcut(HomeShortcutMoveDirection.DOWN) },
    )
    MoveShortcutButton(
        label = shortcut.label,
        direction = HomeShortcutMoveDirection.LEFT,
        text = "L",
        alignment = Alignment.CenterStart,
        onClick = { moveShortcut(HomeShortcutMoveDirection.LEFT) },
    )
    MoveShortcutButton(
        label = shortcut.label,
        direction = HomeShortcutMoveDirection.RIGHT,
        text = "R",
        alignment = Alignment.CenterEnd,
        onClick = { moveShortcut(HomeShortcutMoveDirection.RIGHT) },
    )
}

@Composable
private fun BoxScope.MoveShortcutButton(
    label: String,
    direction: HomeShortcutMoveDirection,
    text: String,
    alignment: Alignment,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .align(alignment)
                .size(20.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clickable(onClick = onClick)
                .semantics { contentDescription = "Move $label ${direction.name.lowercase()}" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun BoxScope.RemoveShortcutButton(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .align(Alignment.TopEnd)
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.errorContainer)
                .clickable(onClick = onClick)
                .semantics { contentDescription = "Remove $label" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "X",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

private fun LauncherPage.shortcutAt(cell: GridCell): AppShortcutItem? =
    items.filterIsInstance<AppShortcutItem>()
        .firstOrNull { item -> item.placement?.cell == cell }

@Composable
private fun PageIndicator(
    pageCount: Int,
    selectedPageIndex: Int,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(pageCount) { index ->
            val color =
                if (index == selectedPageIndex) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
                }

            Box(
                modifier =
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(color),
            )
        }
    }
}

@Composable
private fun Dock(dock: DockModel) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(76.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
                .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(dock.capacity) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f),
                            shape = RoundedCornerShape(16.dp),
                        ),
            )
        }
    }
}
