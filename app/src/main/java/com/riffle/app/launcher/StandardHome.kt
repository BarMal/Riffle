package com.riffle.app.launcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.HomeEditMode
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeShortcutMoveDirection
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.settings.HomeSwipeGestureSettings
import kotlin.math.roundToInt

@Composable
fun StandardHome(
    layout: HomeLayout,
    installedApps: List<InstalledApp>,
    homeSwipeGestures: HomeSwipeGestureSettings,
    notificationCountsByPackage: Map<AppPackageName, Int>,
    appIconLoader: AppIconLoader,
    onAction: (LauncherShellAction) -> Unit,
) {
    val isEditingPage = layout.editMode is HomeEditMode.EditingPage
    val isManagingPages = layout.editMode is HomeEditMode.ManagingPages
    val isEditing = isEditingPage || isManagingPages
    val openedFolderId = remember { mutableStateOf<LauncherItemId?>(null) }
    val swipeThresholdPx = with(LocalDensity.current) { HOME_SWIPE_THRESHOLD_DP.dp.toPx() }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .homeSwipeNavigation(
                    enabled = !isEditing,
                    thresholdPx = swipeThresholdPx,
                    homeSwipeGestures = homeSwipeGestures,
                    onAction = onAction,
                )
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HomeToolbar(
            isEditing = isEditing,
            onAction = onAction,
        )
        AnimatedWorkspaceGrid(
            layout = layout,
            isEditing = isEditingPage,
            notificationCountsByPackage = notificationCountsByPackage,
            appIconLoader = appIconLoader,
            onFolderOpen = { folder -> openedFolderId.value = folder.id },
            onAction = onAction,
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
        )
        if (isEditingPage) {
            PageEditControls(
                pageCount = layout.pages.size,
                selectedPageIndex = layout.selectedPageIndex,
                onAction = onAction,
            )
            HomeFolderEditControls(
                layout = layout,
                onAction = onAction,
            )
        }
        if (isManagingPages) {
            PageOverviewControls(
                layout = layout,
                onAction = onAction,
            )
        }
        PageIndicator(
            pageCount = layout.pages.size,
            selectedPageIndex = layout.selectedPageIndex,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Dock(
            dock = layout.dock,
            isEditing = isEditingPage,
            notificationCountsByPackage = notificationCountsByPackage,
            appIconLoader = appIconLoader,
            onAction = onAction,
        )
    }
    layout.openedFolder(openedFolderId.value)?.let { folder ->
        FolderDialog(
            folder = folder,
            layout = layout,
            installedApps = installedApps,
            appIconLoader = appIconLoader,
            onDismiss = { openedFolderId.value = null },
            onAction = onAction,
        )
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
private fun AnimatedWorkspaceGrid(
    layout: HomeLayout,
    isEditing: Boolean,
    notificationCountsByPackage: Map<AppPackageName, Int>,
    appIconLoader: AppIconLoader,
    onFolderOpen: (FolderItem) -> Unit,
    onAction: (LauncherShellAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val animatedPageIndex =
        animateFloatAsState(
            targetValue = layout.selectedPageIndex.toFloat(),
            label = "home-page-index",
        )

    BoxWithConstraints(modifier = modifier) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val pageOffsetPx =
            ((layout.selectedPageIndex - animatedPageIndex.value) * widthPx).roundToInt()

        WorkspaceGrid(
            page = layout.selectedPage,
            isEditing = isEditing,
            notificationCountsByPackage = notificationCountsByPackage,
            appIconLoader = appIconLoader,
            onFolderOpen = onFolderOpen,
            onAction = onAction,
            modifier =
                Modifier
                    .fillMaxSize()
                    .offset { IntOffset(x = pageOffsetPx, y = 0) },
        )
    }
}

@Composable
private fun WorkspaceGrid(
    page: LauncherPage,
    isEditing: Boolean,
    notificationCountsByPackage: Map<AppPackageName, Int>,
    appIconLoader: AppIconLoader,
    onFolderOpen: (FolderItem) -> Unit,
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
                    val item = page.itemAt(cell = GridCell(column = column, row = row))

                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (item != null) {
                            HomeGridItem(
                                item = item,
                                isEditing = isEditing,
                                notificationCount = notificationCountsByPackage.notificationCountFor(item),
                                appIconLoader = appIconLoader,
                                onFolderOpen = onFolderOpen,
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
@OptIn(ExperimentalFoundationApi::class)
private fun HomeGridItem(
    item: LauncherItem,
    isEditing: Boolean,
    notificationCount: Int,
    appIconLoader: AppIconLoader,
    onFolderOpen: (FolderItem) -> Unit,
    onAction: (LauncherShellAction) -> Unit,
) {
    when (item) {
        is AppShortcutItem ->
            HomeShortcut(
                shortcut = item,
                isEditing = isEditing,
                notificationCount = notificationCount,
                appIconLoader = appIconLoader,
                onAction = onAction,
            )

        is FolderItem ->
            HomeFolder(
                folder = item,
                isEditing = isEditing,
                notificationCount = notificationCount,
                appIconLoader = appIconLoader,
                onFolderOpen = onFolderOpen,
                onAction = onAction,
            )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun HomeShortcut(
    shortcut: AppShortcutItem,
    isEditing: Boolean,
    notificationCount: Int,
    appIconLoader: AppIconLoader,
    onAction: (LauncherShellAction) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .combinedClickable(
                        enabled = !isEditing,
                        onClick = { onAction(LauncherShellAction.LaunchApp(shortcut.appIdentity)) },
                        onLongClick = { onAction(LauncherShellAction.EnterHomeEditMode) },
                    ),
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
            MoveItemControls(
                item = shortcut,
                label = shortcut.label,
                onAction = onAction,
            )
            RemoveShortcutButton(
                label = shortcut.label,
                onClick = { onAction(LauncherShellAction.RemoveHomeShortcut(shortcut.id)) },
            )
        } else {
            NotificationCountBadge(
                count = notificationCount,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}

@Composable
fun BoxScope.MoveItemControls(
    item: LauncherItem,
    label: String,
    onAction: (LauncherShellAction) -> Unit,
) {
    fun moveItem(direction: HomeShortcutMoveDirection) {
        onAction(
            LauncherShellAction.MoveHomeShortcut(
                itemId = item.id,
                direction = direction,
            ),
        )
    }

    MoveShortcutButton(
        label = label,
        direction = HomeShortcutMoveDirection.UP,
        text = "U",
        alignment = Alignment.TopCenter,
        onClick = { moveItem(HomeShortcutMoveDirection.UP) },
    )
    MoveShortcutButton(
        label = label,
        direction = HomeShortcutMoveDirection.DOWN,
        text = "D",
        alignment = Alignment.BottomCenter,
        onClick = { moveItem(HomeShortcutMoveDirection.DOWN) },
    )
    MoveShortcutButton(
        label = label,
        direction = HomeShortcutMoveDirection.LEFT,
        text = "L",
        alignment = Alignment.CenterStart,
        onClick = { moveItem(HomeShortcutMoveDirection.LEFT) },
    )
    MoveShortcutButton(
        label = label,
        direction = HomeShortcutMoveDirection.RIGHT,
        text = "R",
        alignment = Alignment.CenterEnd,
        onClick = { moveItem(HomeShortcutMoveDirection.RIGHT) },
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
fun BoxScope.RemoveShortcutButton(
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

private fun Modifier.homeSwipeNavigation(
    enabled: Boolean,
    thresholdPx: Float,
    homeSwipeGestures: HomeSwipeGestureSettings,
    onAction: (LauncherShellAction) -> Unit,
): Modifier =
    if (!enabled) {
        this
    } else {
        pointerInput(thresholdPx) {
            var horizontalDragPx = 0f
            var verticalDragPx = 0f
            val interpreter = HomeSwipeGestureInterpreter(thresholdPx = thresholdPx)
            val actionMapper = HomeSwipeGestureActionMapper()

            detectDragGestures(
                onDragStart = {
                    horizontalDragPx = 0f
                    verticalDragPx = 0f
                },
                onDrag = { change, dragAmount ->
                    horizontalDragPx += dragAmount.x
                    verticalDragPx += dragAmount.y
                    change.consume()
                },
                onDragEnd = {
                    interpreter
                        .gestureFor(horizontalDragPx, verticalDragPx)
                        ?.let { gesture -> actionMapper.actionFor(gesture, homeSwipeGestures) }
                        ?.let(onAction)
                },
                onDragCancel = {
                    horizontalDragPx = 0f
                    verticalDragPx = 0f
                },
            )
        }
    }

private const val HOME_SWIPE_THRESHOLD_DP = 80
