package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.split.SplitMode

/** Every change the user can make to a [StreamLayout]. */
sealed interface StreamLayoutAction {
    /** "Show as": Stack or List for one section. */
    data class SetView(val section: StreamSection, val view: StreamView) : StreamLayoutAction

    data class SetEnabled(val section: StreamSection, val enabled: Boolean) : StreamLayoutAction

    /** Moves [section] one place earlier ([delta] -1) or later (+1); clamped at either end. */
    data class Move(val section: StreamSection, val delta: Int) : StreamLayoutAction

    data class SetOptions(val options: StreamSectionOptions) : StreamLayoutAction

    /** The split's remembered state: the Stream's share of the room and the last-used shown state (P6). */
    data class SetSplit(val fraction: Float, val lastShown: SplitMode) : StreamLayoutAction

    /** "Show as" for the whole Stream on an expanded posture: Pages or Stage. */
    data class SetWideView(val wideView: StreamWideView) : StreamLayoutAction

    /** Puts a previously held layout back (Undo). */
    data class Restore(val layout: StreamLayout) : StreamLayoutAction

    data object Reset : StreamLayoutAction
}

/** The pure reducer behind every Stream layout edit. Never throws; an action that changes nothing returns its input. */
object StreamLayoutReducer {
    fun reduce(
        layout: StreamLayout,
        action: StreamLayoutAction,
    ): StreamLayout =
        when (action) {
            is StreamLayoutAction.SetView -> layout.updateSection(action.section) { it.copy(view = action.view) }
            is StreamLayoutAction.SetEnabled ->
                layout.updateSection(
                    action.section,
                ) { it.copy(enabled = action.enabled) }
            is StreamLayoutAction.Move -> layout.move(action.section, action.delta)
            is StreamLayoutAction.SetOptions -> layout.copy(options = action.options)
            is StreamLayoutAction.SetSplit -> layout.copy(split = StreamSplit.of(action.fraction, action.lastShown))
            is StreamLayoutAction.SetWideView -> layout.copy(wideView = action.wideView)
            is StreamLayoutAction.Restore -> action.layout
            StreamLayoutAction.Reset -> StreamLayout.DEFAULT
        }

    private fun StreamLayout.updateSection(
        section: StreamSection,
        change: (StreamSectionLayout) -> StreamSectionLayout,
    ): StreamLayout = copy(sections = sections.map { if (it.section == section) change(it) else it })

    private fun StreamLayout.move(
        section: StreamSection,
        delta: Int,
    ): StreamLayout {
        val from = sections.indexOfFirst { it.section == section }
        val to = (from + delta).coerceIn(0, sections.lastIndex)
        if (from < 0 || from == to) return this
        val reordered = sections.toMutableList()
        reordered.add(to, reordered.removeAt(from))
        return copy(sections = reordered)
    }
}
