package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.DockEditRejectionReason
import com.riffle.core.domain.launcher.home.DockEditResult
import com.riffle.core.domain.launcher.home.DockEngine
import com.riffle.core.domain.launcher.home.HomeLayout

fun DockEngine.applyEdit(
    action: LauncherShellAction,
    layout: HomeLayout,
): DockEditResult =
    when (action) {
        is LauncherShellAction.AddAppToDock ->
            addAppToDock(layout = layout, app = action.app)

        is LauncherShellAction.SelectDockEnabled ->
            setDockEnabled(layout = layout, enabled = action.enabled)

        is LauncherShellAction.SelectDockCapacity ->
            setDockCapacity(layout = layout, capacity = action.capacity)

        is LauncherShellAction.RemoveDockShortcut ->
            removeDockItem(layout = layout, itemId = action.itemId)

        is LauncherShellAction.MoveDockShortcut ->
            moveDockItem(
                layout = layout,
                itemId = action.itemId,
                direction = action.direction,
            )

        else -> DockEditResult.Rejected(DockEditRejectionReason.ITEM_NOT_FOUND)
    }
