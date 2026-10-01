package com.riffle.core.domain.launcher.workspace

import com.riffle.core.domain.launcher.workspace.sources.MAX_SEARCH_QUERY_LENGTH

/**
 * The text a lens hands to a [ParameterizedItemSource], for example a search query. It is user-authored lens
 * configuration, so a [Lens] stores it; results produced from it are never stored.
 *
 * Always normalized (trimmed, cut at [MAX_SEARCH_QUERY_LENGTH]) and never blank, so a parameter that exists is
 * a query worth running. [toString] never reveals the text, so a stray log line or diagnostic cannot leak it.
 */
class SourceParameter private constructor(val text: String) {
    override fun equals(other: Any?): Boolean = other is SourceParameter && other.text == text

    override fun hashCode(): Int = text.hashCode()

    override fun toString(): String = "SourceParameter"

    companion object {
        /** Null when [raw] is blank after normalization. */
        fun query(raw: String): SourceParameter? =
            raw.trim().take(MAX_SEARCH_QUERY_LENGTH).trim().takeIf { it.isNotEmpty() }?.let(::SourceParameter)
    }
}

/**
 * Optional extension of [ItemSource] for a source whose output depends on a lens-supplied [SourceParameter].
 * [ItemSource.subscribe] with no parameter stays the default behaviour (for search, the shared
 * `SearchQueryHolder`), so a lens without a parameter is unchanged. Sources that do not implement this are
 * never given a parameter.
 */
interface ParameterizedItemSource : ItemSource {
    /** Same contract as [ItemSource.subscribe] (replay of the latest state, one upstream per distinct call). */
    fun subscribe(
        parameter: SourceParameter,
        observer: SourceObserver,
    ): SourceSubscription
}
