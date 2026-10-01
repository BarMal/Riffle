package com.riffle.core.domain.launcher.workspace.editor

import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.LensGroup
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.SourceCapability
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.WidgetContainer
import com.riffle.core.domain.launcher.workspace.WidgetPlacement
import com.riffle.core.domain.launcher.workspace.WidgetSpan
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory
import com.riffle.core.domain.launcher.workspace.sources.SourceAccess

internal val APPS = SourceId("apps")
internal val NOTES = SourceId("notifications")
internal val CAL = SourceId("calendar")

internal val DESCRIPTORS =
    listOf(
        SourceDescriptor(APPS, setOf(SourceCapability.GROUPABLE, SourceCapability.ACTIONABLE)),
        SourceDescriptor(NOTES, setOf(SourceCapability.GROUPABLE, SourceCapability.PRIVACY_SENSITIVE)),
        SourceDescriptor(CAL, setOf(SourceCapability.LIVE)),
    )

internal val EDIT_CONTEXT = EditContext(sources = DESCRIPTORS)

internal fun cid(value: String) = ContainerId(value)

internal fun lens(
    source: SourceId,
    group: LensGroup = LensGroup.None,
    limit: Int? = null,
) = Lens(listOf(source), group = group, limit = limit)

internal fun binding(
    expression: ExpressionKind = ExpressionKind.LIST,
    source: SourceId = APPS,
    group: LensGroup = LensGroup.None,
    limit: Int? = null,
) = LensBinding(lens(source, group = group, limit = limit), expression)

internal fun boundPage(
    id: String,
    binding: LensBinding = binding(),
) = PageContainer(cid(id), PageContent.Bound(binding))

internal fun widget(
    id: String,
    columns: Int = 1,
    rows: Int = 1,
    binding: LensBinding = binding(),
) = WidgetContainer(cid(id), WidgetSpan(columns, rows), binding)

internal fun gridPage(
    id: String,
    columns: Int = 4,
    rows: Int = 4,
    vararg placements: WidgetPlacement,
) = PageContainer(cid(id), PageContent.WidgetGrid(columns, rows, placements.toList()))

internal fun workspace(vararg pages: com.riffle.core.domain.launcher.workspace.PageHost) =
    Workspace(WorkspaceId("w"), "Main", pages.toList())

internal fun counterIds(prefix: String = "id"): WorkspaceIdFactory {
    var n = 0
    return WorkspaceIdFactory { "$prefix-${n++}" }
}

internal fun choices(access: Map<SourceId, SourceAccess> = emptyMap()) = SourceChoices.build(DESCRIPTORS, access)
