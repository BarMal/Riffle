package com.riffle.app.launcher.editor

import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.SourceCapability
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.WorkspaceSourceIds
import com.riffle.core.domain.launcher.workspace.editor.ContainerKind
import com.riffle.core.domain.launcher.workspace.editor.EditorStep
import com.riffle.core.domain.launcher.workspace.editor.LensPreset

/** User-visible wording for the editor's domain values. Plain strings so JVM tests can check them. */
internal object EditorText {
    const val TITLE = "Edit workspace"
    const val DONE = "Done"
    const val CLOSE = "Close editor"
    const val UNDO = "Undo"
    const val REDO = "Redo"
    const val REVERT = "Revert changes"
    const val NEXT = "Next"
    const val BACK = "Back"
    const val ADD_TO_WORKSPACE = "Add to workspace"
    const val APPLY_CHANGES = "Apply changes"
    const val CANCEL = "Cancel"
    const val DISMISS = "Dismiss"
    const val DISCARD_TITLE = "Discard changes?"
    const val DISCARD_BODY = "Your edits to this workspace have not been saved."
    const val DISCARD = "Discard"
    const val KEEP_EDITING = "Keep editing"
    const val NAME_LABEL = "Workspace name"
    const val PAGES = "Pages"
    const val ADD_PAGE = "Add page or widget"
    const val DOCK_SECTION = "Dock section"
    const val DOCK_SECTION_EMPTY = "No dynamic section"
    const val SET_DOCK_SECTION = "Set dock section"
    const val REMOVE_DOCK_SECTION = "Remove dock section"
    const val PREVIEW = "Preview"
    const val PREVIEW_EMPTY = "Choose a source to see a preview."
    const val PREVIEW_NO_EXPRESSION = "No way to draw this yet. Adjust the source or the lens."
    const val NEEDS_ACCESS = "Needs access"
    const val NEEDS_ACCESS_RATIONALE =
        "This source stays empty until you allow access. Riffle never asks on its own: tap the button to review " +
            "what it reads and why."
    const val ALLOW_ACCESS = "Review access"
    const val UNAVAILABLE_SOURCE = "Not available on this device"
    const val CUSTOM_LENS = "Custom lens"
    const val CUSTOMISE = "Customise"
    const val SOURCES_HEADING = "Where should it come from?"
    const val LENS_HEADING = "What should it show?"
    const val EXPRESSION_HEADING = "How should it look?"
    const val CONTAINER_HEADING = "Where should it go?"
    const val CONFIRM_HEADING = "Ready to add"
    const val EMPTY_WORKSPACE = "No pages"
    const val FINDER_PAGE_LABEL = "Finder"
    const val PER_GROUP_NOTE = "Draws each group on its own page"
    const val START_PAGE_CURRENT = "Start page"
    const val SET_START_PAGE = "Set as start page"
    const val MOVE_UP = "Move up"
    const val MOVE_DOWN = "Move down"
    const val REMOVE = "Remove"
    const val EDIT_CONTENT = "Change content"
    const val ADD_WIDGET_HERE = "Add widget"
    const val MOVE_LEFT = "Move left"
    const val MOVE_RIGHT = "Move right"
    const val LIMIT_LABEL = "Show at most"
    const val NO_LIMIT = "No limit"
    const val QUERY_LABEL = "Search text for this lens"
    const val QUERY_HELP = "Leave empty to use the shared search text. Applies to this lens only, in the preview."
    const val QUERY_CLEAR = "Clear search text"

    fun stepTitle(step: EditorStep): String =
        when (step) {
            EditorStep.SOURCE -> "Source"
            EditorStep.EXPRESSION -> "Look"
            EditorStep.CONTAINER -> "Place"
            EditorStep.CONFIRM -> "Confirm"
        }

    fun stepHeading(step: EditorStep): String =
        when (step) {
            EditorStep.SOURCE -> SOURCES_HEADING
            EditorStep.EXPRESSION -> EXPRESSION_HEADING
            EditorStep.CONTAINER -> CONTAINER_HEADING
            EditorStep.CONFIRM -> CONFIRM_HEADING
        }

    /** "Step 2 of 4". The count is the steps this flow shows, since re-binding skips the container step. */
    fun stepProgress(
        position: Int,
        total: Int,
    ): String = "Step $position of $total"

    fun expressionLabel(kind: ExpressionKind): String =
        when (kind) {
            ExpressionKind.ICON_ROW -> "Icon row"
            ExpressionKind.ICON_GRID -> "Icon grid"
            ExpressionKind.LIST -> "List"
            ExpressionKind.INDEX -> "Index"
            ExpressionKind.CARD -> "Card"
            ExpressionKind.CARD_STACK -> "Card stack"
            ExpressionKind.CATEGORIES -> "Categories"
            ExpressionKind.ALPHA_LIST -> "A to Z list"
        }

    fun expressionHint(kind: ExpressionKind): String =
        when (kind) {
            ExpressionKind.ICON_ROW -> "Icons in one scrolling row"
            ExpressionKind.ICON_GRID -> "Icons in a grid"
            ExpressionKind.LIST -> "One line per item"
            ExpressionKind.INDEX -> "Headings with items beneath"
            ExpressionKind.CARD -> "One rich card"
            ExpressionKind.CARD_STACK -> "A stack of cards, one in focus"
            ExpressionKind.CATEGORIES -> "Groups of icons"
            ExpressionKind.ALPHA_LIST -> "Alphabetical list with a scrubber"
        }

    fun containerLabel(kind: ContainerKind): String =
        when (kind) {
            ContainerKind.WIDGET -> "Widget on a page"
            ContainerKind.PAGE -> "Full page"
            ContainerKind.FINDER_PAGE -> "Finder page"
            ContainerKind.PAGE_SET -> "Page per group"
            ContainerKind.DOCK_SECTION -> "Dock section"
        }

    fun containerHint(kind: ContainerKind): String =
        when (kind) {
            ContainerKind.WIDGET -> "Sits in a free spot on a widget page"
            ContainerKind.PAGE -> "Fills one page"
            ContainerKind.FINDER_PAGE -> "The page the dock's Finder opens"
            ContainerKind.PAGE_SET -> "One page for each group, swiped sideways"
            ContainerKind.DOCK_SECTION -> "Shown in the dock's dynamic area"
        }

    fun presetLabel(preset: LensPreset): String =
        when (preset) {
            LensPreset.EVERYTHING -> "Everything"
            LensPreset.NEWEST_FIRST -> "Newest first"
            LensPreset.A_TO_Z -> "A to Z"
            LensPreset.WITH_ACTIONS -> "With actions"
            LensPreset.GROUPED -> "Grouped"
            LensPreset.GROUPED_BY_DAY -> "Grouped by day"
            LensPreset.LATEST_FIVE -> "Latest five"
            LensPreset.LATEST_ONE -> "Latest one"
        }

    fun sourceLabel(id: SourceId): String =
        when (id) {
            SourceIds.ALL_APPS -> "All apps"
            SourceIds.RECENT_APPS -> "Recent apps"
            WorkspaceSourceIds.FREQUENT_APPS -> "Frequent apps"
            WorkspaceSourceIds.FAVOURITE_APPS -> "Favourite apps"
            WorkspaceSourceIds.HOME_GRID -> "Home screen"
            SourceIds.NOTIFICATIONS -> "Notifications"
            SourceIds.QUICK_ACTIONS -> "Quick actions"
            SourceIds.MEDIA -> "Media"
            SourceIds.CALENDAR -> "Calendar"
            SourceIds.RSS -> "Feeds"
            SourceIds.SEARCH -> "Search"
            SourceIds.ICS -> "Calendar feeds"
            else -> id.value
        }

    fun badgeLabel(capability: SourceCapability): String =
        when (capability) {
            SourceCapability.LIVE -> "Live"
            SourceCapability.GROUPABLE -> "Groupable"
            SourceCapability.ACTIONABLE -> "Actions"
            SourceCapability.SEARCHABLE -> "Searchable"
            SourceCapability.PRIVACY_SENSITIVE -> "Private"
        }
}
