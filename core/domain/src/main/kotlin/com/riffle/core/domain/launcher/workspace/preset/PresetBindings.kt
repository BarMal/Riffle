package com.riffle.core.domain.launcher.workspace.preset

import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.LensSort
import com.riffle.core.domain.launcher.workspace.LensSortField
import com.riffle.core.domain.launcher.workspace.SortDirection
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.WorkspaceDock
import com.riffle.core.domain.launcher.workspace.notificationsLens
import com.riffle.core.domain.launcher.workspace.recentAppsLens

private const val ICON_ROW_LIMIT = 8
private const val DOCK_SLOTS = 5

// Bindings shared by the presets. Permission-gated sources are only referenced: they report
// PermissionRequired when observed, and nothing prompts at install.

internal fun mediaCard() = LensBinding(Lens(listOf(SourceIds.MEDIA), limit = 1), ExpressionKind.CARD)

internal fun nextEventCard() =
    LensBinding(
        Lens(listOf(SourceIds.CALENDAR), sort = LensSort(LensSortField.TIME, SortDirection.ASCENDING), limit = 1),
        ExpressionKind.CARD,
    )

internal fun quickActionsRow() =
    LensBinding(Lens(listOf(SourceIds.QUICK_ACTIONS), limit = ICON_ROW_LIMIT), ExpressionKind.ICON_ROW)

internal fun recentsRow() = LensBinding(recentAppsLens().copy(limit = ICON_ROW_LIMIT), ExpressionKind.ICON_ROW)

internal fun recentsList() = LensBinding(recentAppsLens(), ExpressionKind.LIST)

/** Newest notifications as icons, one per dock slot. */
internal fun notificationDock() =
    WorkspaceDock(LensBinding(notificationsLens().copy(limit = DOCK_SLOTS), ExpressionKind.ICON_ROW))
