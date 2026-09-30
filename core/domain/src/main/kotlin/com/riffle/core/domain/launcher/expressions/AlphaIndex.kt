package com.riffle.core.domain.launcher.expressions

import java.text.Normalizer
import java.util.Locale

/** One A-Z bucket: the [letter] heading and the entries filed under it, already sorted. */
data class AlphaSection<T>(
    val letter: Char,
    val entries: List<T>,
)

/**
 * Pure letter bucketing and scrubber maths for the AlphaList expression. Framework-free so the
 * indexing rules are unit tested without a device.
 */
object AlphaIndex {
    /** Bucket for anything that does not start with a Latin letter (digits, symbols, other scripts). */
    const val OTHER: Char = '#'

    /**
     * The section letter for [title]: the first character, diacritics folded away ("Arzte" with an
     * umlaut files under A), upper-cased. Anything that does not fold to A-Z, including null, blank
     * and redacted titles, files under [OTHER].
     */
    fun letterOf(title: String?): Char {
        val first = title?.trimStart()?.firstOrNull() ?: return OTHER
        val folded =
            Normalizer
                .normalize(first.toString(), Normalizer.Form.NFD)
                .firstOrNull()
                ?.uppercaseChar()
        return if (folded != null && folded in 'A'..'Z') folded else OTHER
    }

    /**
     * Buckets [entries] into sections ordered A-Z then [OTHER]. Entries inside a section are sorted
     * by title, ignoring case; ties keep their incoming order. Empty sections are omitted.
     */
    fun <T> sections(
        entries: List<T>,
        title: (T) -> String?,
    ): List<AlphaSection<T>> =
        entries
            .groupBy { letterOf(title(it)) }
            .map { (letter, bucket) ->
                AlphaSection(letter, bucket.sortedBy { title(it)?.lowercase(Locale.ROOT).orEmpty() })
            }.sortedBy { sortKey(it.letter) }

    /**
     * Index of each section's header in a flat list that renders one header row followed by the
     * section's entries. This is where the scrubber scrolls to.
     */
    fun <T> headerIndices(sections: List<AlphaSection<T>>): Map<Char, Int> {
        val indices = LinkedHashMap<Char, Int>()
        var next = 0
        sections.forEach { section ->
            indices[section.letter] = next
            next += 1 + section.entries.size
        }
        return indices
    }

    /**
     * The letters to draw on a scrubber with room for [maxLetters]. When every letter fits they are
     * all kept; otherwise letters are thinned evenly, always keeping the first and the last.
     */
    fun thin(
        letters: List<Char>,
        maxLetters: Int,
    ): List<Char> {
        val lastSlot = maxLetters - 1
        val lastLetter = letters.lastIndex
        return when {
            maxLetters <= 0 -> emptyList()
            letters.size <= maxLetters -> letters
            maxLetters == 1 -> listOf(letters.first())
            else -> (0 until maxLetters).map { slot -> letters[slot * lastLetter / lastSlot] }
        }
    }

    /**
     * Which of [count] equal-height scrubber cells a touch at [fraction] of the scrubber's height
     * (0 top, 1 bottom) is over. Out-of-range touches clamp to the first or last cell, so a drag
     * that leaves the scrubber keeps pointing at its end.
     */
    fun cellAt(
        fraction: Float,
        count: Int,
    ): Int {
        if (count <= 0) return 0
        val clamped = if (fraction.isNaN()) 0f else fraction.coerceIn(0f, 1f)
        return (clamped * count).toInt().coerceAtMost(count - 1)
    }

    private fun sortKey(letter: Char): Int = if (letter == OTHER) Int.MAX_VALUE else letter.code
}
