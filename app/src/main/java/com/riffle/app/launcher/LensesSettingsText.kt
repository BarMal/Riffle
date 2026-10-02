package com.riffle.app.launcher

import com.riffle.app.launcher.editor.EditorReasonText
import com.riffle.app.launcher.editor.EditorText
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.LibraryProblem
import com.riffle.core.domain.launcher.workspace.MAX_SAVED_LENSES
import com.riffle.core.domain.launcher.workspace.MAX_SAVED_LENS_NAME
import com.riffle.core.domain.launcher.workspace.settings.BrokenUse
import com.riffle.core.domain.launcher.workspace.settings.LensCopyTarget
import com.riffle.core.domain.launcher.workspace.settings.LensProblem
import com.riffle.core.domain.launcher.workspace.settings.LensRow
import com.riffle.core.domain.launcher.workspace.settings.LensesSettingsMessage
import com.riffle.core.domain.launcher.workspace.settings.PageKind
import com.riffle.core.domain.launcher.workspace.settings.UsePlace
import com.riffle.core.domain.launcher.workspace.settings.UsedByRow

/** User-visible wording for Settings > Saved lenses. Plain strings so JVM tests can check them. */
internal object LensesSettingsText {
    const val TITLE = "Saved lenses"
    const val DEVELOPER_SUBTITLE =
        "Named, reusable lenses for each layout: build, rename, copy and see where they are used"
    const val NOT_LOADED = "Workspaces are still loading. Open this page again in a moment."
    const val NEW = "New"
    const val NEW_DESCRIPTION = "New saved lens"
    const val LIST_HEADING = "Saved lenses"
    const val EXPLAINER =
        "A saved lens is a named choice of sources, filter, grouping and order that containers can share. " +
            "Each layout keeps its own: copying one to another layout is a one-time copy, never a link."
    const val DETAIL_PLACEHOLDER = "Choose a saved lens to edit it, or New to build one."
    const val EMPTY = "No saved lenses on this layout yet. Choose New to build one."
    const val FULL_HINT = "This layout has reached the limit of $MAX_SAVED_LENSES saved lenses."
    const val RENAME = "Rename"
    const val DUPLICATE = "Duplicate"
    const val DELETE = "Delete"
    const val OPEN = "Open"
    const val CANCEL = "Cancel"
    const val SAVE = "Save"
    const val SAVE_CHANGES = "Save changes"
    const val CREATE = "Create lens"
    const val BACK_TO_LIST = "Back to saved lenses"
    const val UNDO = "Undo"
    const val NAME_LABEL = "Lens name"
    const val UNSAVED_HINT = "Unsaved changes"
    const val SAVE_OPTIONS = "Save..."
    const val NAME_COUNTER_LABEL = "characters"
    const val NEW_LENS_TITLE = "New saved lens"
    const val SOURCES_HEADING = "Sources"
    const val PREVIEW_HEADING = "Preview"
    const val PREVIEW_NOTE = "A live preview of your own data. Nothing shown here is saved."
    const val PREVIEW_UNAVAILABLE = "The preview is not available here."
    const val DRAWS_AS_HEADING = "Can be drawn as"
    const val USED_BY_HEADING = "Used by"
    const val NOT_USED = "No container uses this lens yet."
    const val USED_BY_NEW = "Containers can use it once it is saved."
    const val EDIT_WORKSPACE = "Edit workspace"
    const val EDIT_OTHER_LAYOUT_REASON = "Switch Settings to this device's layout to open the editor."
    const val ACTIONS_HEADING = "Actions"
    const val UNSAVED_TITLE = "Discard changes?"
    const val UNSAVED_BODY = "Your changes to this lens have not been saved."
    const val DISCARD = "Discard"
    const val KEEP_EDITING = "Keep editing"
    const val DELETE_TITLE = "Delete saved lens?"
    const val DELETE_CONFIRM = "Delete"
    const val DELETE_DETACH = "Keep the containers as they are"
    const val DELETE_DETACH_HINT = "Each container keeps drawing the same lens, no longer shared."
    const val DELETE_REPLACE = "Use another saved lens instead"
    const val DELETE_REPLACE_HINT = "Containers switch to it. Any it cannot draw keep their current lens."
    const val NO_REPLACEMENT = "There is no other saved lens to use."
    const val COPY_TITLE = "Copy to another layout"
    const val COPY_CONFIRM = "Copy"
    const val BREAKS_TITLE = "This change affects containers"
    const val SAVE_DETACH = "Save and detach those containers"
    const val SAVE_DETACH_HINT = "The rest follow the change. The affected ones keep the old lens, no longer shared."
    const val SAVE_AS_NEW = "Save as a new lens"
    const val SAVE_AS_NEW_HINT = "Keeps the original lens untouched. Nothing is rebound."
    const val BREAKS_NOTE_HEADING = "Would stop working"
    const val ADVANCED_BREAKS_NOTE =
        "Choose how to save: detach the affected containers, or save as a new lens and leave this one as it is."

    fun layoutName(layout: HomeLayoutDeviceClass): String = WorkspacesSettingsText.layoutName(layout)

    fun listHeading(
        layout: HomeLayoutDeviceClass,
        count: Int,
    ): String = "Saved lenses for ${layoutName(layout)}: ${lenses(count)} of $MAX_SAVED_LENSES"

    fun lenses(count: Int): String = if (count == 1) "1 lens" else "$count lenses"

    fun usedIn(count: Int): String =
        when (count) {
            0 -> "not used"
            1 -> "used in 1 place"
            else -> "used in $count places"
        }

    fun sourceNames(row: LensRow): String = row.sources.joinToString(separator = ", ") { EditorText.sourceLabel(it) }

    fun rowSummary(row: LensRow): String =
        listOfNotNull(
            sourceNames(row),
            if (row.grouped) "grouped" else "flat",
            usedIn(row.usedIn),
            "from a preset".takeIf { row.fromPreset },
        ).joinToString(separator = " · ")

    /** What TalkBack reads for a row; it also states what tapping does and where the row is in the list. */
    fun rowSpokenSummary(
        row: LensRow,
        position: Int,
        total: Int,
    ): String = "${row.name}. ${rowSummary(row)}. Lens $position of $total. Double tap to open"

    fun moreActionsDescription(name: String): String = "More actions for $name"

    fun copyToLabel(layout: HomeLayoutDeviceClass): String = "Copy to ${layoutName(layout)}"

    fun places(count: Int): String = if (count == 1) "1 container" else "$count containers"
}

/** Announcements and problems of the Saved lenses page. */
internal object LensesMessageText {
    fun problem(problem: LibraryProblem): String =
        when (problem) {
            LibraryProblem.BLANK_NAME -> "A saved lens needs a name."
            LibraryProblem.NAME_TOO_LONG -> "Names can be at most $MAX_SAVED_LENS_NAME characters."
            LibraryProblem.NAME_TAKEN -> "Another saved lens already has that name."
            LibraryProblem.LIBRARY_FULL -> "This layout already has $MAX_SAVED_LENSES saved lenses. Delete one first."
            LibraryProblem.UNKNOWN_LENS -> "That saved lens no longer exists."
        }

    fun problem(problem: LensProblem): String =
        when (problem) {
            LensProblem.NoSource -> "Choose at least one source."
            LensProblem.NothingCanDraw ->
                "Nothing on this layout can draw this lens yet. Try another source or setting."
            is LensProblem.Library -> problem(problem.problem)
        }

    fun nameCounter(length: Int): String = "$length of $MAX_SAVED_LENS_NAME"

    fun message(message: LensesSettingsMessage): String =
        when (message) {
            is LensesSettingsMessage.Created -> "Created \"${message.name}\""
            is LensesSettingsMessage.Saved -> saved(message)
            is LensesSettingsMessage.Renamed -> "Renamed to \"${message.name}\""
            is LensesSettingsMessage.Duplicated -> "Duplicated as \"${message.name}\""
            is LensesSettingsMessage.Deleted -> deleted(message)
            is LensesSettingsMessage.CopiedToLayout ->
                "Copied \"${message.name}\" to ${LensesSettingsText.layoutName(
                    message.target,
                )} as \"${message.newName}\""
            is LensesSettingsMessage.Refused -> problem(message.problem)
            is LensesSettingsMessage.BreaksContainers ->
                "Not saved: it would stop ${LensesSettingsText.places(message.count)} from working."
            LensesSettingsMessage.NothingCanDraw -> "Nothing on this layout can draw that lens."
            LensesSettingsMessage.NothingToDo -> "Nothing to change."
        }

    private fun saved(message: LensesSettingsMessage.Saved): String =
        if (message.detached == 0) {
            "Saved \"${message.name}\""
        } else {
            "Saved \"${message.name}\". ${LensesSettingsText.places(message.detached)} kept the old lens."
        }

    private fun deleted(message: LensesSettingsMessage.Deleted): String {
        val name = "Deleted \"${message.name}\""
        val switched = message.usedBy - message.detached
        return when {
            message.usedBy == 0 -> name
            message.replacedBy == null -> "$name. Containers that used it keep their lens, no longer shared."
            message.detached == 0 ->
                "$name. ${LensesSettingsText.places(switched)} switched to \"${message.replacedBy}\"."
            else ->
                "$name. ${LensesSettingsText.places(switched)} switched to \"${message.replacedBy}\"; " +
                    "${LensesSettingsText.places(message.detached)} kept the current lens."
        }
    }
}

/** Wording for the page's dialogs. */
internal object LensesDialogText {
    fun deleteBody(
        name: String,
        usedBy: Int,
    ): String =
        if (usedBy == 0) {
            "Delete \"$name\"? No container uses it. You can undo right after."
        } else {
            "Delete \"$name\"? It is used in ${LensesSettingsText.places(usedBy)}. Nothing stops working: choose " +
                "what they do. You can undo right after."
        }

    fun replacementImpact(detached: Int): String =
        if (detached == 0) {
            "Every container can use it."
        } else {
            "${LensesSettingsText.places(detached)} cannot draw it and will keep their current lens."
        }

    fun copyBody(
        name: String,
        target: LensCopyTarget,
    ): String {
        val where = LensesSettingsText.layoutName(target.layout)
        return when {
            target.full ->
                "$where already has $MAX_SAVED_LENSES saved lenses, so \"$name\" cannot be copied there. " +
                    "Delete one there first."
            else ->
                "Copy \"$name\" to $where as \"${target.newName}\"? It is a one-time copy, not a link: changing one " +
                    "never changes the other, and nothing on $where uses it yet. $where has " +
                    "${LensesSettingsText.lenses(target.count)} now. You can undo right after."
        }
    }

    fun saveAsNewLabel(name: String): String = "Save as new lens \"$name\""

    fun copyTargetLabel(target: LensCopyTarget): String =
        "${LensesSettingsText.layoutName(
            target.layout,
        )} (${LensesSettingsText.lenses(target.count)}${if (target.full) ", full" else ""})"
}

/** Wording for the lens detail and builder. */
internal object LensesDetailText {
    fun place(place: UsePlace): String =
        when (place) {
            is UsePlace.Page ->
                when (place.kind) {
                    PageKind.PAGE -> "Page ${place.position}"
                    PageKind.PAGE_SET -> "Page ${place.position} (a page per group)"
                    PageKind.FINDER -> "Finder page ${place.position}"
                }
            is UsePlace.Widget -> "Widget on page ${place.pagePosition}"
            UsePlace.Dock -> "Dock section"
        }

    /** "Standard > Page 2 (a page per group), Card stack": what the Used by row says. */
    fun usedBy(row: UsedByRow): String =
        "${row.workspaceName} > ${place(row.place)}, ${EditorText.expressionLabel(row.expression)}"

    fun usedBySpoken(
        row: UsedByRow,
        canEdit: Boolean,
    ): String = usedBy(row) + if (canEdit) ". Double tap to edit this workspace" else ""

    /** One broken container with the domain's reasons, in words. */
    fun broken(use: BrokenUse): String =
        "${usedBy(use.use)}: " + use.issues.map(EditorReasonText::workspace).distinct().joinToString(separator = "; ")

    fun drawableAs(kinds: List<ExpressionKind>): String =
        kinds.joinToString(separator = ", ") { EditorText.expressionLabel(it) }

    fun previewDescription(name: String): String = "Preview of $name"

    fun breaksSummary(count: Int): String =
        "This change would stop ${LensesSettingsText.places(count)} from working. Nothing is saved until you choose."
}
