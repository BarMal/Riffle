package com.riffle.core.domain.launcher.stream

/**
 * Where the Stream lands when it is opened from the Dock (ADR 0003): on now playing, or on one app's stack in the
 * Notifications section. Transient UI intent; never stored.
 */
sealed interface StreamFocusTarget {
    /** The Now section. */
    data object Now : StreamFocusTarget

    /** The Notifications group [groupKey] (`package:profile`, the group's own key). */
    data class AppStack(val groupKey: String) : StreamFocusTarget

    /** One whole section: where a glance widget on Home lands (its Now, Agenda or Feeds section). */
    data class Section(val section: StreamSection) : StreamFocusTarget
}

object StreamFocus {
    /**
     * The key of the page [target] lands on in [pages] (ADR 0003, Views), or null when the layout hides what it names
     * (the Stream just opens where it was).
     *
     * - Now lands on the Now page; a Section on that section's first page.
     * - An app's stack lands on that app's Notifications page; one whose notifications were cleared between the tap
     *   and the open lands on the Notifications section's first page.
     */
    fun resolvePage(
        target: StreamFocusTarget,
        pages: List<StreamPage>,
    ): String? =
        when (target) {
            is StreamFocusTarget.Section -> pages.firstOrNull { it.section == target.section }?.key
            StreamFocusTarget.Now -> pages.firstOrNull { it.section == StreamSection.NOW }?.key
            is StreamFocusTarget.AppStack ->
                (
                    pages.firstOrNull { it.section == StreamSection.NOTIFICATIONS && it.groupKey == target.groupKey }
                        ?: pages.firstOrNull { it.section == StreamSection.NOTIFICATIONS }
                )?.key
        }
}
