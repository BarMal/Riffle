package com.riffle.core.domain.launcher.workspace.preset

import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.LensGroup
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.PageHost
import com.riffle.core.domain.launcher.workspace.PageRole
import com.riffle.core.domain.launcher.workspace.PageSetContainer
import com.riffle.core.domain.launcher.workspace.WidgetContainer
import com.riffle.core.domain.launcher.workspace.WidgetPlacement
import com.riffle.core.domain.launcher.workspace.WidgetSpan
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceDock
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.appsByGroup
import com.riffle.core.domain.launcher.workspace.appsLens
import com.riffle.core.domain.launcher.workspace.homeGridLens
import com.riffle.core.domain.launcher.workspace.notificationsLens

/** The HomeLayout page the home-grid pages of every preset refer to (`HomeLayoutDefaults.standard`). */
internal const val HOME_PAGE_ID = "home"

/** Deterministic ids for one preset variant: `preset:<preset>:<posture>[:<part>]`. */
internal class PresetIds(
    preset: String,
    posture: PresetPosture,
) {
    val workspace = WorkspaceId("preset:$preset:${posture.name.lowercase()}")

    fun container(part: String) = ContainerId("${workspace.value}:$part")
}

internal fun bound(
    ids: PresetIds,
    part: String,
    lens: Lens,
    expression: ExpressionKind,
    role: PageRole = PageRole.STANDARD,
): PageHost = PageContainer(ids.container(part), PageContent.Bound(LensBinding(lens, expression)), role)

/** The Finder page: All apps, grouped by category for Categories, a flat sorted list for AlphaList. */
internal fun finderPage(
    ids: PresetIds,
    expression: ExpressionKind,
): PageHost =
    when (expression) {
        ExpressionKind.CATEGORIES -> bound(ids, "finder", appsByGroup(), expression, PageRole.FINDER)
        else -> bound(ids, "finder", appsLens(), expression, PageRole.FINDER)
    }

internal fun homePage(
    ids: PresetIds,
    expression: ExpressionKind,
): PageHost = bound(ids, "home", homeGridLens(HOME_PAGE_ID), expression)

internal fun notificationsPageSet(ids: PresetIds): PageHost =
    PageSetContainer(
        ids.container("inbox"),
        LensBinding(notificationsLens().copy(group = LensGroup.ByGroupKey), ExpressionKind.CARD_STACK),
    )

internal fun widgetPage(
    ids: PresetIds,
    part: String,
    size: Pair<Int, Int>,
    widgets: List<WidgetPlacement>,
): PageHost = PageContainer(ids.container(part), PageContent.WidgetGrid(size.first, size.second, widgets))

/** One widget at [at] (column, row) spanning [span] (columns, rows) cells. */
internal fun widget(
    ids: PresetIds,
    name: String,
    at: Pair<Int, Int>,
    span: Pair<Int, Int>,
    binding: LensBinding,
): WidgetPlacement =
    WidgetPlacement(
        WidgetContainer(ids.container("w-$name"), WidgetSpan(span.first, span.second), binding),
        column = at.first,
        row = at.second,
    )

internal fun workspace(
    ids: PresetIds,
    name: String,
    pages: List<PageHost>,
    dock: WorkspaceDock = WorkspaceDock(),
): Workspace = Workspace(ids.workspace, name, pages, dock)
