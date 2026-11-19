package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.glance.GlanceWidgets
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.StackedWidget
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.home.WidgetStackEngine
import com.riffle.core.domain.launcher.home.WidgetStackResult

/** Applies a widget stack edit (ADR 0003, Amendment 14) to Home; one the rules refuse changes nothing. */
internal fun LauncherShellState.withWidgetStackEdit(
    action: LauncherShellAction,
    engine: WidgetStackEngine,
    homeLayoutRepository: HomeLayoutRepository,
): LauncherShellState {
    val result =
        when (action) {
            is LauncherShellAction.ShowStackedWidget -> engine.show(homeLayout, action.itemId, action.index)
            is LauncherShellAction.MoveStackedWidget ->
                engine.reorder(homeLayout, action.itemId, action.from, action.to)
            is LauncherShellAction.RemoveStackedWidget -> engine.remove(homeLayout, action.itemId, action.index)
            is LauncherShellAction.MoveStackedWidgetOut -> engine.moveOut(homeLayout, action.itemId, action.index)
            is LauncherShellAction.AddHostedWidgetToStack ->
                engine.addMember(
                    homeLayout,
                    action.itemId,
                    StackedWidget(
                        action.hostedWidgetId,
                        action.label.ifBlank { DEFAULT_LABEL },
                        action.resizeConstraints,
                    ),
                )
            is LauncherShellAction.AddGlanceWidgetToStack ->
                engine.addMember(
                    homeLayout,
                    action.itemId,
                    StackedWidget(
                        GlanceWidgets.UNBOUND_HOST_ID,
                        action.label.ifBlank { action.kind.defaultLabel },
                        action.kind.resizeConstraints,
                        action.kind,
                    ),
                )
            else -> return this
        }
    return when (result) {
        is WidgetStackResult.Updated -> withHomeLayout(result.layout, homeLayoutRepository)
        is WidgetStackResult.Rejected -> this
    }
}

/** The host ids a Home action is about to give up: every widget of a removed item, or the removed stack member. */
internal fun LauncherShellState.removedHomeHostedWidgetIds(action: LauncherShellAction): List<HostedWidgetId> =
    when (action) {
        is LauncherShellAction.RemoveHomeShortcut -> homeLayout.hostedWidgetIdsForItem(action.itemId)
        is LauncherShellAction.RemoveStackedWidget ->
            homeLayout.selectedPage.items
                .filterIsInstance<WidgetItem>()
                .firstOrNull { it.id == action.itemId }
                ?.takeIf { it.isStack }
                ?.members
                ?.getOrNull(action.index)
                ?.takeIf { it.glance == null }
                ?.appWidgetId
                ?.let(::listOf)
                .orEmpty()
        else -> emptyList()
    }

/** Whether this edit may be taken as given: a layout whose item [itemId] is a stack. */
internal fun HomeLayout.isStack(itemId: LauncherItemId): Boolean =
    pages.flatMap { it.items }.filterIsInstance<WidgetItem>().any { it.id == itemId && it.isStack }

private const val DEFAULT_LABEL = "Widget"
