package com.riffle.app.launcher

import com.riffle.app.launcher.editor.EditorText
import com.riffle.app.launcher.workspace.SourceAccessRoute
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.settings.LayoutFallbackNotice
import com.riffle.core.domain.launcher.workspace.settings.SourceStatus
import com.riffle.core.domain.launcher.workspace.settings.WorkspaceRow
import com.riffle.core.domain.launcher.workspace.settings.WorkspacesSettingsMessage
import com.riffle.core.domain.launcher.workspace.sources.CalendarAccessStatus

/** User-visible wording for the Workspaces and Sources settings pages. Plain strings so JVM tests can check them. */
internal object WorkspacesSettingsText {
    const val WORKSPACES_TITLE = "Workspaces"
    const val PREVIEW_OFF = "Turn on Workspaces (preview) in Settings to use this page."
    const val NOT_LOADED = "Workspaces are still loading. Open this page again in a moment."
    const val ACTIVE = "Active"
    const val DEFAULT = "Default"
    const val EDIT = "Edit"
    const val SWITCH_TO = "Switch to this workspace"
    const val RENAME = "Rename"
    const val DUPLICATE = "Duplicate"
    const val MAKE_DEFAULT = "Make default"
    const val MOVE_UP = "Move up"
    const val MOVE_DOWN = "Move down"
    const val DELETE = "Delete"
    const val INSTALL_PRESET = "Install preset..."
    const val COPY_FROM_OTHER = "Copy from other layout..."
    const val CANCEL = "Cancel"
    const val UNDO = "Undo"
    const val SAVE = "Save"
    const val NAME_LABEL = "Workspace name"
    const val DEFAULT_PRESET_TAG = "Default"
    const val SWITCH_AFTER_INSTALL = "Switch to it after installing"
    const val EDIT_OTHER_LAYOUT_REASON = "Switch Settings to this device's layout to edit it."
    const val EDITING_NOTE = "Each layout keeps its own workspaces. Changes here never touch your home screen items."

    fun layoutName(layout: HomeLayoutDeviceClass): String =
        when (layout) {
            HomeLayoutDeviceClass.PHONE -> "Phone (folded)"
            HomeLayoutDeviceClass.PHONE_LANDSCAPE -> "Phone (landscape)"
            HomeLayoutDeviceClass.FOLDABLE -> "Foldable (unfolded)"
            HomeLayoutDeviceClass.TABLET -> "Tablet"
            HomeLayoutDeviceClass.DESKTOP -> "Desktop"
        }

    fun editingLayout(
        layout: HomeLayoutDeviceClass,
        isCurrent: Boolean,
    ): String =
        if (isCurrent) {
            "Editing the layout this device is showing now: ${layoutName(layout)}."
        } else {
            "Editing ${layoutName(layout)}, not the layout this device is showing now."
        }

    fun resetLabel(presetName: String): String = "Reset to $presetName preset"

    fun rowSummary(row: WorkspaceRow): String =
        listOfNotNull(
            if (row.isActive) ACTIVE else null,
            if (row.isDefault) DEFAULT else null,
            row.presetName?.let { "$it preset" },
            pages(row.pageCount),
        ).joinToString(separator = " · ")

    /** What TalkBack reads for a row, which also states what tapping it does. */
    fun rowSpokenSummary(row: WorkspaceRow): String =
        buildString {
            append(row.name)
            append(", ")
            append(rowSummary(row))
            append(if (row.isActive) "" else ". Double tap to switch to it")
        }

    fun pages(count: Int): String = if (count == 1) "1 page" else "$count pages"

    fun moreActionsDescription(name: String): String = "More actions for $name"

    fun fallbackNotice(notice: LayoutFallbackNotice): String {
        val what =
            notice.unsupported.distinct().map(EditorText::expressionLabel).joinToString(separator = " and ")
                .ifEmpty { "everything in \"${notice.requestedName}\"" }
        return "This layout can't draw $what (used by \"${notice.requestedName}\"), so it is using the default " +
            "workspace, \"${notice.shownName}\", instead."
    }
}

/** Wording for the Workspaces page's confirmations, pickers and announcements. */
internal object WorkspacesDialogText {
    const val PRESET_PICKER_TITLE = "Install preset"
    const val PRESET_PICKER_BODY =
        "Adds a new workspace to this layout. Presets are starting points: edit them freely."
    const val COPY_TITLE = "Copy from another layout"
    const val COPY_CONFIRM = "Replace and copy"
    const val DELETE_CONFIRM = "Delete"
    const val RESET_CONFIRM = "Reset"
    const val INSTALL_CONFIRM = "Install"
    const val ONLY_ONE_REASON = "The last workspace on a layout can't be deleted."

    fun deleteBody(name: String): String =
        "Delete \"$name\"? Its pages and dock section are removed from this layout. Your other workspaces and " +
            "everything on your home screen are not touched. You can undo right after."

    fun resetBody(
        name: String,
        presetName: String,
    ): String =
        "Reset \"$name\" to the $presetName preset? Its pages and dock section are replaced by the preset's. The " +
            "name stays. You can undo right after."

    fun copyBody(
        source: HomeLayoutDeviceClass,
        target: HomeLayoutDeviceClass,
        replaced: Int,
        copied: Int,
    ): String {
        val into = WorkspacesSettingsText.layoutName(target)
        val from = WorkspacesSettingsText.layoutName(source)
        return "A one-time copy, not a link. This replaces the ${workspaces(replaced)} on $into with copies of the " +
            "${workspaces(copied)} on $from. Afterwards the two layouts are independent: changing one never " +
            "changes the other. Your home screen items are not copied or touched. You can undo right after."
    }

    fun workspaces(count: Int): String = if (count == 1) "1 workspace" else "$count workspaces"

    fun message(message: WorkspacesSettingsMessage): String =
        when (message) {
            is WorkspacesSettingsMessage.Activated -> "Switched to \"${message.name}\""
            is WorkspacesSettingsMessage.Renamed -> "Renamed to \"${message.name}\""
            is WorkspacesSettingsMessage.Duplicated -> "Duplicated \"${message.name}\""
            is WorkspacesSettingsMessage.Moved -> "Moved \"${message.name}\""
            is WorkspacesSettingsMessage.MadeDefault -> "\"${message.name}\" is now the default"
            is WorkspacesSettingsMessage.Deleted -> "Deleted \"${message.name}\""
            is WorkspacesSettingsMessage.Reset -> "Reset \"${message.name}\" to the ${message.presetName} preset"
            is WorkspacesSettingsMessage.Installed -> "Installed \"${message.name}\""
            is WorkspacesSettingsMessage.Copied ->
                "Copied ${workspaces(message.copied)} from ${WorkspacesSettingsText.layoutName(message.source)}"
            WorkspacesSettingsMessage.CannotDeleteLast -> ONLY_ONE_REASON
            WorkspacesSettingsMessage.InvalidName -> "A workspace needs a name."
            WorkspacesSettingsMessage.NoKnownPreset -> "This workspace has no recorded preset to reset to."
            WorkspacesSettingsMessage.NothingToDo -> "Nothing changed."
        }
}

/** Wording for the Sources page. */
internal object SourcesSettingsText {
    const val TITLE = "Sources"
    const val SOURCES_INTRO =
        "Turn a source off to stop Riffle reading it. Pages that use it then say it is turned off, with a " +
            "button to turn it back on. Nothing is deleted."
    const val HIDDEN_ITEMS_SOON = "Hidden items and rules (coming soon)"
    const val HIDDEN_ITEMS_SOON_BODY = "Rules to hide items from a source will be managed here."
    const val ALLOW = "Allow"

    /** The label of the Allow button for a source's explicit access flow; null when it has none. */
    fun allowLabel(
        route: SourceAccessRoute,
        calendar: CalendarAccessStatus,
    ): String? =
        when (route) {
            SourceAccessRoute.CALENDAR -> calendar.calendarAccessActionLabel() ?: ALLOW
            SourceAccessRoute.NOTIFICATION_ACCESS -> "Allow notification access"
            SourceAccessRoute.USAGE_ACCESS -> "Open usage access settings"
            SourceAccessRoute.NONE -> null
        }

    /** Why access is asked for, shown next to the button so the reason is read before anything can appear. */
    fun rationale(
        route: SourceAccessRoute,
        calendar: CalendarAccessStatus,
    ): String? =
        when (route) {
            SourceAccessRoute.CALENDAR -> calendar.calendarAccessSettingsLabel()
            SourceAccessRoute.NOTIFICATION_ACCESS ->
                "Riffle reads notifications on this device only and never stores or sends them. This opens " +
                    "Android's notification access settings."
            SourceAccessRoute.USAGE_ACCESS ->
                "Riffle reads which apps you used lately, on this device only. This opens Android's usage " +
                    "access settings."
            SourceAccessRoute.NONE -> null
        }

    fun statusDescription(
        title: String,
        status: SourceStatus,
    ): String = "$title, ${status.label}"
}
