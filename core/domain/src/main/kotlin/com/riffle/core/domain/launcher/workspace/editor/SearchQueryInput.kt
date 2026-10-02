package com.riffle.core.domain.launcher.workspace.editor

import com.riffle.core.domain.launcher.workspace.SourceParameter
import com.riffle.core.domain.launcher.workspace.sources.MAX_SEARCH_QUERY_LENGTH

/**
 * The pure rules behind the editor's per-lens search query field (Source step): how typed text is capped, when it
 * is applied to the flow draft, and what the helper text says. No Compose, no clock: the field passes its own
 * timing in. The text is user-authored lens configuration (see `LensQueryEdits`); results are never stored.
 */
object SearchQueryInput {
    const val MAX_LENGTH = MAX_SEARCH_QUERY_LENGTH

    /** How long typing must pause before the text is applied to the draft (each applied query is a live stream). */
    const val DEBOUNCE_MILLIS = 400L

    /** What the field holds after the user typed [raw]: cut at [MAX_LENGTH] without splitting a surrogate pair. */
    fun capped(raw: String): String {
        if (raw.length <= MAX_LENGTH) return raw
        val cut = if (raw[MAX_LENGTH - 1].isHighSurrogate()) MAX_LENGTH - 1 else MAX_LENGTH
        return raw.substring(0, cut)
    }

    /** The text the flow stores for [typed]: trimmed and capped, with blank as "" (use the global query). */
    fun applied(typed: String): String = SourceParameter.query(typed)?.text.orEmpty()

    /** True when [typed] differs from what the draft already holds ([draftQuery], "" when it has none). */
    fun needsApply(
        typed: String,
        draftQuery: String,
    ): Boolean = applied(typed) != draftQuery

    /** "12 / 128". */
    fun counter(typed: String): String = "${typed.length} / $MAX_LENGTH"

    /** True once the field is full, so the counter can be announced as a limit. */
    fun atLimit(typed: String): Boolean = typed.length >= MAX_LENGTH
}
