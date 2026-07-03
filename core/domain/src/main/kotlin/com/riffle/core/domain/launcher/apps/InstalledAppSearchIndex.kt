package com.riffle.core.domain.launcher.apps

class InstalledAppSearchIndex(
    private val apps: List<InstalledApp>,
    private val shortcutsByApp: AppShortcutsByApp = emptyMap(),
) {
    fun search(query: String): List<InstalledApp> =
        query.normalizedSearchTokens().let { queryTokens ->
            when {
                queryTokens.isEmpty() -> apps
                else ->
                    apps
                        .mapIndexedNotNull { index, app ->
                            app.searchRank(queryTokens)?.let { rank ->
                                InstalledAppSearchHit(
                                    app = app,
                                    rank = rank,
                                    sourceIndex = index,
                                )
                            }
                        }
                        .sortedWith(
                            compareBy<InstalledAppSearchHit> { hit -> hit.rank }
                                .thenBy { hit -> hit.sourceIndex },
                        )
                        .map { hit -> hit.app }
            }
        }

    private fun InstalledApp.searchRank(queryTokens: List<String>): Int? =
        listOfNotNull(
            labelRank(queryTokens),
            shortcutRank(queryTokens),
            identityRank(queryTokens),
        ).minOrNull()

    private fun InstalledApp.labelRank(queryTokens: List<String>): Int? =
        label.lowercase().let { label ->
            when {
                label.matchesAll(queryTokens) && label.startsWith(queryTokens.first()) -> 0
                label.matchesAll(queryTokens) -> 1
                label.acronym().matchesAll(queryTokens) -> 1
                else -> null
            }
        }

    private fun InstalledApp.shortcutRank(queryTokens: List<String>): Int? =
        shortcutsByApp[identity].orEmpty()
            .mapNotNull { shortcut -> shortcut.searchRank(queryTokens) }
            .minOrNull()

    private fun InstalledApp.identityRank(queryTokens: List<String>): Int? =
        listOf(
            identity.packageName.value,
            identity.activityName.value,
            identity.profile.id.value,
            identity.profile.type.name,
        )
            .map { token -> token.lowercase() }
            .takeIf { tokens -> tokens.matchesAll(queryTokens) }
            ?.let { 3 }

    private fun AppShortcut.searchRank(queryTokens: List<String>): Int? =
        listOf(
            shortLabel,
            longLabel.orEmpty(),
            id.value,
        )
            .map { token -> token.lowercase() }
            .let { tokens ->
                when {
                    tokens.matchesAll(queryTokens) -> 2
                    tokens.map(String::acronym).matchesAll(queryTokens) -> 2
                    else -> null
                }
            }
}

private fun String.normalizedSearchTokens(): List<String> =
    trim()
        .lowercase()
        .split(Regex("\\s+"))
        .filter(String::isNotBlank)

private fun String.matchesAll(queryTokens: List<String>): Boolean {
    return queryTokens.all { queryToken -> contains(queryToken) }
}

private fun String.acronym(): String =
    split(Regex("[^a-z0-9]+"))
        .filter(String::isNotBlank)
        .joinToString(separator = "") { token -> token.first().toString() }

private fun List<String>.matchesAll(queryTokens: List<String>): Boolean =
    queryTokens.all { queryToken -> any { token -> token.contains(queryToken) } }

private data class InstalledAppSearchHit(
    val app: InstalledApp,
    val rank: Int,
    val sourceIndex: Int,
)
