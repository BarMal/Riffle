package com.riffle.core.domain.launcher.search.wider

import java.text.Normalizer

/** Name matching shared by the wider sources: case and diacritics are ignored. */
object WiderMatching {
    const val FULL_PREFIX = 100
    const val WORD_PREFIX = 80
    const val CONTAINS = 60
    const val KEYWORD = 40

    private val marks = Regex("\\p{M}+")
    private val separators = Regex("[^\\p{L}\\p{N}]+")

    /** Lower case, accents removed ("Zoë" is "zoe"). */
    fun fold(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD).replace(marks, "").lowercase()

    fun words(text: String): List<String> = fold(text).split(separators).filter(String::isNotEmpty)

    /**
     * How well [name] matches the query words: the whole name starts with the query, every query word starts some
     * word of the name (in any order), or null. Substrings in the middle of a word are not matches.
     */
    fun nameScore(
        name: String,
        query: String,
    ): Int? {
        val queryWords = words(query)
        if (queryWords.isEmpty()) return null
        val nameWords = words(name)
        val prefixOfName = fold(name).trimStart().startsWith(queryWords.joinToString(" "))
        return when {
            prefixOfName -> FULL_PREFIX
            queryWords.all { q -> nameWords.any { it.startsWith(q) } } -> WORD_PREFIX
            else -> null
        }
    }

    /** Like [nameScore] for a title, which also accepts a word that only contains the query, and keyword hits. */
    fun titleScore(
        title: String,
        keywords: List<String>,
        query: String,
    ): Int? {
        val queryWords = words(query)
        val folded = fold(title)
        val prefix = nameScore(title, query)
        return when {
            prefix != null -> prefix
            queryWords.isNotEmpty() && queryWords.all { folded.contains(it) } -> CONTAINS
            queryWords.isNotEmpty() && keywords.any { keyword -> queryWords.all { fold(keyword).contains(it) } } ->
                KEYWORD
            else -> null
        }
    }
}
