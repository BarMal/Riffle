package com.riffle.app.launcher.editor

import com.riffle.core.domain.launcher.workspace.ContainerIssue
import com.riffle.core.domain.launcher.workspace.GestureAxis
import com.riffle.core.domain.launcher.workspace.ItemField
import com.riffle.core.domain.launcher.workspace.LensIssue
import com.riffle.core.domain.launcher.workspace.ResultShape
import com.riffle.core.domain.launcher.workspace.WorkspaceIssue
import com.riffle.core.domain.launcher.workspace.editor.EditRejection
import com.riffle.core.domain.launcher.workspace.editor.ExpressionChoice
import com.riffle.core.domain.launcher.workspace.settings.SourceStatus

/** Plain-language reasons for why a choice is not available or an edit was refused. */
internal object EditorReasonText {
    const val SOURCE_OFF_NOTE =
        "Turned off in Settings > Sources. A lens can still use it, and shows nothing until it is on."

    /** The status word a source row shows when its host knows the status (Settings); null for Ready or unknown. */
    fun sourceStatusLabel(status: SourceStatus?): String? =
        when (status) {
            null, SourceStatus.READY -> null
            SourceStatus.LOADING -> "Checking"
            SourceStatus.NEEDS_PERMISSION -> EditorText.NEEDS_ACCESS
            SourceStatus.OFF -> "Off"
            SourceStatus.UNAVAILABLE -> "Unavailable"
        }

    fun lens(issue: LensIssue): String =
        when (issue) {
            is LensIssue.MissingRequiredField -> "Needs the ${field(issue.field)} of each item"
            is LensIssue.ShapeNotAccepted -> "Cannot draw ${shapes(issue.produced)}; it draws ${shapes(issue.accepted)}"
            is LensIssue.SourceNotGroupable -> "${EditorText.sourceLabel(issue.sourceId)} cannot be grouped"
            is LensIssue.UnknownSource -> "${EditorText.sourceLabel(issue.sourceId)} is not available"
        }

    fun container(issue: ContainerIssue): String =
        when (issue) {
            is ContainerIssue.InvalidPairing -> issue.issues.joinToString("; ") { lens(it) }
            is ContainerIssue.PageSetNeedsGroupedLens -> "A page per group needs a grouped lens"
            is ContainerIssue.AxisConflict -> "Scrolls ${axis(issue.axis)}, which the page swipe already uses"
            is ContainerIssue.WidgetOutOfBounds -> "Does not fit on the page"
            is ContainerIssue.WidgetOverlap -> "Overlaps another widget"
            is ContainerIssue.DuplicateContainerId -> "Already on this page"
            is ContainerIssue.InvalidFinder -> "The Finder page must be Categories or an A to Z list"
        }

    fun workspace(issue: WorkspaceIssue): String =
        when (issue) {
            is WorkspaceIssue.Container -> container(issue.issue)
            is WorkspaceIssue.DockPairing -> issue.issues.joinToString("; ") { lens(it) }
            WorkspaceIssue.NoPages -> "A workspace needs at least one page"
            WorkspaceIssue.MultipleFinderPages -> "Only one page can be the Finder"
            is WorkspaceIssue.UnsupportedExpression ->
                "${EditorText.expressionLabel(issue.kind)} is not available on this screen"
        }

    fun rejection(rejection: EditRejection): String =
        when (rejection) {
            is EditRejection.Invalid -> rejection.issues.map { workspace(it) }.distinct().joinToString("; ")
            is EditRejection.UnknownPage -> "That page no longer exists"
            is EditRejection.UnknownWidget -> "That widget no longer exists"
            is EditRejection.NotAGridPage -> "That page does not hold widgets"
            is EditRejection.NotABoundPage -> "That page holds widgets; change a widget instead"
            EditRejection.BlankName -> "A workspace needs a name"
            EditRejection.BlankSkinId -> "Choose a skin or follow the launcher's"
        }

    /** Why an expression is not offered, as one line. Empty when it is. */
    fun expression(choice: ExpressionChoice): String =
        when {
            choice.enabled -> ""
            choice.unsupportedByLayout -> "Not available on this screen"
            choice.containerIssues.isNotEmpty() -> choice.containerIssues.joinToString("; ") { workspace(it) }
            else -> choice.lensIssues.joinToString("; ") { lens(it) }
        }

    private fun field(field: ItemField): String =
        when (field) {
            ItemField.TITLE -> "title"
            ItemField.SUBTITLE -> "subtitle"
            ItemField.BODY -> "text"
            ItemField.ICON -> "icon"
            ItemField.IMAGE -> "image"
            ItemField.TIME -> "time"
            ItemField.GROUP -> "group"
            ItemField.ACTIONS -> "actions"
            ItemField.EXT -> "extra details"
        }

    private fun shapes(shapes: Set<ResultShape>): String =
        shapes.sortedBy { it.ordinal }.joinToString(" or ") {
            when (it) {
                ResultShape.FLAT -> "a plain list"
                ResultShape.GROUPED -> "groups"
                ResultShape.SINGLE -> "a single item"
            }
        }

    private fun axis(axis: GestureAxis): String =
        when (axis) {
            GestureAxis.VERTICAL_SCROLL -> "up and down"
            GestureAxis.HORIZONTAL_SCROLL -> "sideways"
            GestureAxis.HORIZONTAL_PAGER -> "sideways between pages"
        }
}
