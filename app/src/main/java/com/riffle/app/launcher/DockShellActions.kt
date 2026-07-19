package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.DockConfigurationEngine
import com.riffle.core.domain.launcher.home.DockEditRejectionReason
import com.riffle.core.domain.launcher.home.DockEditResult
import com.riffle.core.domain.launcher.home.DockEngine
import com.riffle.core.domain.launcher.home.HomeLayout

@Suppress("CyclomaticComplexMethod")
fun DockEngine.applyEdit(
    action: LauncherShellAction,
    layout: HomeLayout,
): DockEditResult =
    when (action) {
        is LauncherShellAction.AddAppToDock ->
            addAppToDock(layout = layout, app = action.app)

        is LauncherShellAction.AddHostedWidgetToDock ->
            addWidgetToDock(
                layout = layout,
                hostedWidgetId = action.hostedWidgetId,
                label = action.label,
            )

        is LauncherShellAction.SelectDockEnabled ->
            DockConfigurationEngine().setDockEnabled(layout = layout, enabled = action.enabled)

        is LauncherShellAction.SelectDockNotificationCardsEnabled ->
            DockConfigurationEngine().setDockNotificationCardsEnabled(layout = layout, enabled = action.enabled)

        is LauncherShellAction.SelectDockCapacity ->
            DockConfigurationEngine().setDockCapacity(layout = layout, capacity = action.capacity)

        is LauncherShellAction.SelectDockIconSize ->
            DockConfigurationEngine().setDockIconSize(layout = layout, sizeDp = action.sizeDp)

        is LauncherShellAction.SelectDockBackgroundAlpha ->
            DockConfigurationEngine().setDockBackgroundAlpha(layout = layout, alphaPercent = action.alphaPercent)

        is LauncherShellAction.SelectDockVisualEffect ->
            DockConfigurationEngine().setDockVisualEffect(layout = layout, effect = action.effect)

        is LauncherShellAction.SelectDockBackgroundSizing ->
            DockConfigurationEngine().setDockBackgroundSizing(layout = layout, sizing = action.sizing)

        is LauncherShellAction.SelectDockItemSpacing ->
            DockConfigurationEngine().setDockItemSpacing(layout = layout, spacingDp = action.spacingDp)

        is LauncherShellAction.RemoveDockShortcut ->
            removeDockItem(layout = layout, itemId = action.itemId)

        is LauncherShellAction.MoveDockShortcut ->
            moveDockItem(
                layout = layout,
                itemId = action.itemId,
                direction = action.direction,
            )

        is LauncherShellAction.MoveDockShortcutToIndex ->
            moveDockItemToIndex(
                layout = layout,
                itemId = action.itemId,
                targetIndex = action.targetIndex,
            )

        else -> DockEditResult.Rejected(DockEditRejectionReason.ITEM_NOT_FOUND)
    }

internal fun LauncherShellAction.isDockConfigurationAction(): Boolean =
    when (this) {
        is LauncherShellAction.SelectDockEnabled,
        is LauncherShellAction.SelectDockNotificationCardsEnabled,
        is LauncherShellAction.SelectDockCapacity,
        is LauncherShellAction.SelectDockIconSize,
        is LauncherShellAction.SelectDockBackgroundAlpha,
        is LauncherShellAction.SelectDockVisualEffect,
        is LauncherShellAction.SelectDockBackgroundSizing,
        is LauncherShellAction.SelectDockItemSpacing,
        -> true

        else -> false
    }
