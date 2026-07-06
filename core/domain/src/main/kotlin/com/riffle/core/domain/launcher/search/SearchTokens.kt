package com.riffle.core.domain.launcher.search

private val SearchWhitespace = Regex("\\s+")
private val SearchAcronymBoundary = Regex("[^a-z0-9]+")

fun normalizedSearchTokens(query: String): List<String> =
    query
        .trim()
        .lowercase()
        .split(SearchWhitespace)
        .filter(String::isNotBlank)

fun String.searchAcronym(): String =
    lowercase()
        .split(SearchAcronymBoundary)
        .filter(String::isNotBlank)
        .joinToString(separator = "") { token -> token.first().toString() }

fun String.containsAllSearchTokens(queryTokens: List<String>): Boolean {
    return queryTokens.all { queryToken -> contains(queryToken) }
}

fun Iterable<String>.containsAllSearchTokens(queryTokens: List<String>): Boolean =
    queryTokens.all { queryToken -> any { value -> value.contains(queryToken) } }
