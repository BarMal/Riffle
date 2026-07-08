package com.riffle.app.launcher

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.widgets.EmptyHomeWidgetViewFactory
import com.riffle.app.launcher.widgets.HomeWidgetViewFactory
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutsByApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockBackgroundSizing
import com.riffle.core.domain.launcher.home.DockItemMoveDirection
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.FolderItem
import kotlin.math.min

@Composable
internal fun Dock(
    dock: DockModel,
    isEditing: Boolean,
    notificationCountsByPackage: Map<AppPackageName, Int>,
    appShortcutsByApp: AppShortcutsByApp,
    appIconLoader: AppIconLoader,
    widgetViewFactory: HomeWidgetViewFactory = EmptyHomeWidgetViewFactory,
    interactions: DockInteractions,
) {
    val presentation = DockPresentation(notificationCountsByPackage, appShortcutsByApp, widgetViewFactory, interactions)
    val renderedSlotCount =
        dockRenderedSlotCount(
            capacity = dock.capacity,
            itemCount = dock.items.size,
            isEditing = isEditing,
        )
    val isBackgroundVisible =
        dockBackgroundVisible(
            capacity = dock.capacity,
            itemCount = dock.items.size,
            isEditing = isEditing,
            backgroundSizing = dock.backgroundSizing,
        )

    if (!isBackgroundVisible) {
        return
    }

    BoxWithConstraints(
        modifier = Modifier.dockShelfGestureInput(interactions),
        contentAlignment = Alignment.Center,
    ) {
        HomeBackgroundContextMenu(
            haptics = interactions.haptics,
            onAction = interactions.onAction,
            modifier = Modifier.matchParentSize(),
        )
        val dockWidthDp =
            dockContainerWidthDp(
                availableWidthDp = maxWidth.value.toInt(),
                slotCount = renderedSlotCount,
                iconSizeDp = dock.iconSizeDp,
                itemSpacingDp = dock.itemSpacingDp,
                backgroundSizing = dock.backgroundSizing,
            )
        val contentViewportWidthDp =
            dockContentViewportWidthDp(
                slotCount = renderedSlotCount,
                iconSizeDp = dock.iconSizeDp,
                itemSpacingDp = dock.itemSpacingDp,
                availableDockWidthDp = dockWidthDp,
            )
        val slotMetrics =
            dockSlotRenderMetrics(
                slotCount = renderedSlotCount,
                iconSizeDp = dock.iconSizeDp,
                itemSpacingDp = dock.itemSpacingDp,
                availableContentWidthDp = contentViewportWidthDp,
            )

        Box(
            modifier =
                Modifier
                    .animateContentSize()
                    .width(dockWidthDp.dp)
                    .height(dockHeightDp(slotMetrics.iconSizeDp).dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(
                            alpha = dock.backgroundAlphaPercent / 100f,
                        ),
                    )
                    .padding(horizontal = DOCK_HORIZONTAL_PADDING_DP.dp, vertical = DOCK_VERTICAL_PADDING_DP.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (renderedSlotCount > 0 && contentViewportWidthDp > 0) {
                DockSlotsRow(
                    dock = dock,
                    renderedSlotCount = renderedSlotCount,
                    contentViewportWidthDp = contentViewportWidthDp,
                    slotMetrics = slotMetrics,
                    isEditing = isEditing,
                    presentation = presentation,
                    appIconLoader = appIconLoader,
                )
            }
        }
    }
}

@Composable
private fun DockSlotsRow(
    dock: DockModel,
    renderedSlotCount: Int,
    contentViewportWidthDp: Int,
    slotMetrics: DockSlotRenderMetrics,
    isEditing: Boolean,
    presentation: DockPresentation,
    appIconLoader: AppIconLoader,
) {
    val scrollState = rememberScrollState()
    val overflowAffordance =
        DockOverflowAffordance(
            scrollOffsetPx = scrollState.value,
            maxScrollOffsetPx = scrollState.maxValue,
        )
    val fadeColor =
        MaterialTheme.colorScheme.surfaceVariant.copy(
            alpha = dock.backgroundAlphaPercent / 100f,
        )

    Box(
        modifier =
            Modifier
                .width(contentViewportWidthDp.dp)
                .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier =
                Modifier
                    .width(dockSlotContentWidthDp(renderedSlotCount, slotMetrics).dp)
                    .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(slotMetrics.itemSpacingDp.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(renderedSlotCount) { index ->
                DockSlot(
                    modifier = Modifier.requiredSize(slotMetrics.iconSizeDp.dp),
                    state =
                        DockSlotState(
                            item = dockSlotItemState(dock.items.getOrNull(index)),
                            shortcutIndex = index,
                            shortcutCount = dock.items.size,
                            iconSizeDp = slotMetrics.iconSizeDp,
                            isEditing = isEditing,
                        ),
                    presentation = presentation,
                    appIconLoader = appIconLoader,
                )
            }
        }

        if (overflowAffordance.showStart) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .width(DOCK_OVERFLOW_FADE_WIDTH_DP.dp)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(fadeColor, fadeColor.copy(alpha = 0f)),
                            ),
                        ),
            )
        }
        if (overflowAffordance.showEnd) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterEnd)
                        .width(DOCK_OVERFLOW_FADE_WIDTH_DP.dp)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(fadeColor.copy(alpha = 0f), fadeColor),
                            ),
                        ),
            )
        }
    }
}

private const val DOCK_MAX_WIDTH_DP = 560
private const val DOCK_VERTICAL_CHROME_DP = 32
private const val DOCK_HORIZONTAL_PADDING_DP = 14
private const val DOCK_VERTICAL_PADDING_DP = 10
private const val DOCK_OVERFLOW_FADE_WIDTH_DP = 20

internal fun dockHeightDp(iconSizeDp: Int): Int = iconSizeDp + DOCK_VERTICAL_CHROME_DP

internal fun dockContentViewportWidthDp(
    slotCount: Int,
    iconSizeDp: Int,
    itemSpacingDp: Int,
    availableDockWidthDp: Int = DOCK_MAX_WIDTH_DP,
): Int {
    if (slotCount <= 0) {
        return 0
    }
    val contentWidth = (slotCount * iconSizeDp) + ((slotCount - 1) * itemSpacingDp)
    val maxDockWidth = min(availableDockWidthDp, DOCK_MAX_WIDTH_DP)
    val maxContentWidth = (maxDockWidth - (DOCK_HORIZONTAL_PADDING_DP * 2)).coerceAtLeast(0)
    return min(contentWidth, maxContentWidth)
}

internal fun dockContainerWidthDp(
    availableWidthDp: Int,
    slotCount: Int,
    iconSizeDp: Int,
    itemSpacingDp: Int,
    backgroundSizing: DockBackgroundSizing,
): Int {
    val maxDockWidth = min(availableWidthDp, DOCK_MAX_WIDTH_DP).coerceAtLeast(0)
    if (backgroundSizing == DockBackgroundSizing.FIXED) {
        return maxDockWidth
    }
    val contentViewportWidth =
        dockContentViewportWidthDp(
            slotCount = slotCount,
            iconSizeDp = iconSizeDp,
            itemSpacingDp = itemSpacingDp,
            availableDockWidthDp = maxDockWidth,
        )
    return min(maxDockWidth, contentViewportWidth + (DOCK_HORIZONTAL_PADDING_DP * 2))
}

internal fun dockRenderedSlotCount(
    capacity: Int,
    itemCount: Int,
    isEditing: Boolean,
): Int =
    when {
        capacity <= 0 -> 0
        itemCount <= 0 && !isEditing -> 0
        else -> capacity.coerceAtLeast(itemCount)
    }

internal fun dockBackgroundVisible(
    capacity: Int,
    itemCount: Int,
    isEditing: Boolean,
    backgroundSizing: DockBackgroundSizing,
): Boolean =
    when {
        backgroundSizing == DockBackgroundSizing.FIXED -> true
        capacity <= 0 -> false
        isEditing -> true
        else -> itemCount > 0
    }

internal data class DockOverflowAffordance(
    val showStart: Boolean,
    val showEnd: Boolean,
) {
    constructor(
        scrollOffsetPx: Int,
        maxScrollOffsetPx: Int,
    ) : this(
        showStart = maxScrollOffsetPx > 0 && scrollOffsetPx > 0,
        showEnd = maxScrollOffsetPx > 0 && scrollOffsetPx < maxScrollOffsetPx,
    )
}

private data class DockPresentation(
    val notificationCountsByPackage: Map<AppPackageName, Int>,
    val appShortcutsByApp: AppShortcutsByApp,
    val widgetViewFactory: HomeWidgetViewFactory,
    val interactions: DockInteractions,
)

internal data class DockInteractions(
    val haptics: LauncherHaptics = NoopLauncherHaptics,
    val onFolderOpen: (FolderItem) -> Unit = {},
    val isShelfExpanded: Boolean = false,
    val onShelfExpandedChange: ((Boolean) -> Unit)? = null,
    val onAction: (LauncherShellAction) -> Unit,
)

private data class DockSlotState(
    val item: DockSlotItemState?,
    val shortcutIndex: Int,
    val shortcutCount: Int,
    val iconSizeDp: Int,
    val isEditing: Boolean,
)

private data class DockShortcutState(
    val iconSizeDp: Int,
    val shortcutIndex: Int,
    val shortcutCount: Int,
    val isEditing: Boolean,
    val notificationCount: Int,
    val appShortcuts: List<AppShortcut>,
)

@Composable
private fun DockSlot(
    modifier: Modifier,
    state: DockSlotState,
    presentation: DockPresentation,
    appIconLoader: AppIconLoader,
) {
    val editingSlotColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(18.dp))
                .then(if (state.isEditing) Modifier.background(editingSlotColor) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        when (val item = state.item) {
            null -> Unit
            is DockSlotItemState.Shortcut ->
                DockShortcut(
                    shortcut = item.shortcut,
                    state =
                        DockShortcutState(
                            iconSizeDp = state.iconSizeDp,
                            shortcutIndex = state.shortcutIndex,
                            shortcutCount = state.shortcutCount,
                            isEditing = state.isEditing,
                            notificationCount =
                                presentation.notificationCountsByPackage[item.shortcut.appIdentity.packageName] ?: 0,
                            appShortcuts = presentation.appShortcutsByApp[item.shortcut.appIdentity].orEmpty(),
                        ),
                    presentation = presentation,
                    appIconLoader = appIconLoader,
                )
            is DockSlotItemState.Folder ->
                DockItemPlaceholder(
                    item =
                        DockSlotItemState.Placeholder(
                            id = item.id,
                            label = item.label,
                            kind = DockSlotPlaceholderKind.FOLDER,
                        ),
                    iconSizeDp = state.iconSizeDp,
                    modifier =
                        if (state.isEditing) {
                            Modifier
                        } else {
                            Modifier.clickable(onClick = { presentation.interactions.onFolderOpen(item.folder) })
                        },
                )
            is DockSlotItemState.Widget ->
                DockWidget(
                    widget = item.widget,
                    widgetViewFactory = presentation.widgetViewFactory,
                    iconSizeDp = state.iconSizeDp,
                )
            is DockSlotItemState.Placeholder ->
                DockItemPlaceholder(
                    item = item,
                    iconSizeDp = state.iconSizeDp,
                )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun DockShortcut(
    shortcut: AppShortcutItem,
    state: DockShortcutState,
    presentation: DockPresentation,
    appIconLoader: AppIconLoader,
) {
    val isContextMenuExpanded = remember(shortcut.id) { mutableStateOf(false) }

    Box(
        modifier =
            Modifier
                .requiredSize(state.iconSizeDp.dp),
    ) {
        LauncherAppIcon(
            identity = shortcut.appIdentity,
            label = shortcut.label,
            iconLoader = appIconLoader,
            modifier =
                Modifier
                    .requiredSize(state.iconSizeDp.dp)
                    .combinedClickable(
                        onClick = {
                            if (state.isEditing) {
                                isContextMenuExpanded.value = true
                            } else {
                                presentation.interactions.onAction(shortcut.launchAction())
                            }
                        },
                        onLongClick = {
                            presentation.interactions.haptics.longPress()
                            isContextMenuExpanded.value = true
                        },
                        onLongClickLabel = "Show ${shortcut.label} actions",
                    ),
        )

        if (!state.isEditing) {
            NotificationCountBadge(
                count = state.notificationCount,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
        ShortcutContextMenu(
            expanded = isContextMenuExpanded.value,
            items =
                dockShortcutContextMenuItems(
                    shortcut = shortcut,
                    appShortcuts = state.appShortcuts,
                    isEditing = state.isEditing,
                    shortcutIndex = state.shortcutIndex,
                    shortcutCount = state.shortcutCount,
                ),
            onDismissRequest = { isContextMenuExpanded.value = false },
            onAction = presentation.interactions.onAction,
        )
    }
}

internal fun dockShortcutContextMenuItems(
    shortcut: AppShortcutItem,
    appShortcuts: List<AppShortcut> = emptyList(),
    isEditing: Boolean = false,
    shortcutIndex: Int = 0,
    shortcutCount: Int = 1,
): List<ShortcutContextMenuItem> {
    val editItems =
        if (isEditing) {
            listOf(
                ShortcutContextMenuItem(
                    label = "Move left",
                    action =
                        LauncherShellAction.MoveDockShortcut(
                            itemId = shortcut.id,
                            direction = DockItemMoveDirection.LEFT,
                        ),
                    enabled = shortcutIndex > 0,
                ),
                ShortcutContextMenuItem(
                    label = "Move right",
                    action =
                        LauncherShellAction.MoveDockShortcut(
                            itemId = shortcut.id,
                            direction = DockItemMoveDirection.RIGHT,
                        ),
                    enabled = shortcutIndex < shortcutCount - 1,
                ),
            )
        } else {
            emptyList()
        }

    return editItems +
        shortcutContextMenuItems(
            shortcut = shortcut,
            surface = ShortcutContextSurface.DOCK,
            appShortcuts = appShortcuts,
        )
}
