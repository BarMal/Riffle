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
}

/** What a [StreamFocusTarget] resolves to in the Stream as it is now: the section to scroll to and any group. */
data class StreamFocusResolution(
    val section: StreamSection,
    val groupKey: String?,
)

object StreamFocus {
    /**
     * Resolves [target] against the sections the layout shows ([enabledSections]) and the Notifications result. A
     * section the layout hides resolves to null (the Stream just opens at the top); a group that has gone away (its
     * notifications were cleared between the tap and the open) resolves to the Notifications section alone.
     */
    fun resolve(
        target: StreamFocusTarget,
        enabledSections: List<StreamSection>,
        notifications: StreamSectionResult?,
    ): StreamFocusResolution? =
        when (target) {
            StreamFocusTarget.Now ->
                StreamFocusResolution(StreamSection.NOW, null).takeIf { StreamSection.NOW in enabledSections }
            is StreamFocusTarget.AppStack ->
                if (StreamSection.NOTIFICATIONS !in enabledSections) {
                    null
                } else {
                    val groups = (notifications as? StreamSectionResult.Ready)?.groups.orEmpty()
                    val present = groups.any { it.key == target.groupKey }
                    StreamFocusResolution(StreamSection.NOTIFICATIONS, target.groupKey.takeIf { present })
                }
        }
}
