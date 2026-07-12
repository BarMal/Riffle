package com.riffle.app.launcher

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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutsByApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockBackgroundSizing
import com.riffle.core.domain.launcher.home.DockItemMoveDirection
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import kotlin.math.min

@Composable
internal fun Dock(
    dock: DockModel,
    isEditing: Boolean,
    notificationGroupsByApp: List<AppNotificationGroup>,
    appShortcutsByApp: AppShortcutsByApp,
    appIconLoader: AppIconLoader,
    widgetViewFactory: HomeWidgetViewFactory = EmptyHomeWidgetViewFactory,
    interactions: DockInteractions,
) {
    val presentation = DockPresentation(notificationGroupsByApp, appShortcutsByApp, widgetViewFactory, interactions)

    BoxWithConstraints(
        modifier = Modifier.dockShelfGestureInput(interactions),
        contentAlignment = Alignment.Center,
    ) {
        val surfaceMetrics =
            dockSurfaceMetrics(
                dock = dock,
                isEditing = isEditing,
                availableWidthDp = maxWidth.value.toInt(),
            ) ?: return@BoxWithConstraints
        HomeBackgroundContextMenu(
            haptics = interactions.haptics,
            onAction = interactions.onAction,
            modifier = Modifier.matchParentSize(),
        )
        DockSurfaceRow(
            modifier =
                Modifier
                    .dockShelfPolicies(interactions),
            dock = dock,
            surfaceMetrics = surfaceMetrics,
            isEditing = isEditing,
            presentation = presentation,
            appIconLoader = appIconLoader,
        )
    }
}

@Composable
internal fun DockSlotsRow(
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
internal const val DOCK_VERTICAL_CHROME_DP = 32
internal const val DOCK_HORIZONTAL_PADDING_DP = 14
internal const val DOCK_VERTICAL_PADDING_DP = 10
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
        isEditing -> capacity.coerceAtLeast(itemCount)
        else -> capacity
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

internal data class DockPresentation(
    val notificationGroupsByApp: List<AppNotificationGroup>,
    val appShortcutsByApp: AppShortcutsByApp,
    val widgetViewFactory: HomeWidgetViewFactory,
    val interactions: DockInteractions,
)

internal data class DockInteractions(
    val haptics: LauncherHaptics = NoopLauncherHaptics,
    val onFolderOpen: (FolderItem) -> Unit = {},
    val isShelfExpanded: Boolean = false,
    val onShelfExpandedChange: ((Boolean) -> Unit)? = null,
    val reducedMotion: Boolean = false,
    val homeInsetPolicy: HomeInsetPolicy = HomeInsetPolicy(),
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
                .clip(LocalLauncherCardShape.current)
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
                                presentation.notificationGroupsByApp.notificationCountFor(
                                    item.shortcut,
                                ),
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
                DockWidgetSlot(
                    widget = item.widget,
                    iconSizeDp = state.iconSizeDp,
                    isEditing = state.isEditing,
                    shortcutIndex = state.shortcutIndex,
                    shortcutCount = state.shortcutCount,
                    presentation = presentation,
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
