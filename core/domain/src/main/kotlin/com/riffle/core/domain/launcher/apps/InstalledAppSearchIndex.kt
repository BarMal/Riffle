package com.riffle.core.domain.launcher.apps

import com.riffle.core.domain.launcher.search.containsAllSearchTokens
import com.riffle.core.domain.launcher.search.normalizedSearchTokens
import com.riffle.core.domain.launcher.search.searchAcronym

class InstalledAppSearchIndex(
    private val apps: List<InstalledApp>,
    private val shortcutsByApp: AppShortcutsByApp = emptyMap(),
) {
    fun search(query: String): List<InstalledApp> =
        normalizedSearchTokens(query).let { queryTokens ->
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
                label.containsAllSearchTokens(queryTokens) && label.startsWith(queryTokens.first()) -> 0
                label.containsAllSearchTokens(queryTokens) -> 1
                label.searchAcronym().containsAllSearchTokens(queryTokens) -> 1
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
            category.orEmpty(),
        )
            .map { token -> token.lowercase() }
            .takeIf { tokens -> tokens.containsAllSearchTokens(queryTokens) }
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
                    tokens.containsAllSearchTokens(queryTokens) -> 2
                    tokens.map(String::searchAcronym).containsAllSearchTokens(queryTokens) -> 2
                    else -> null
                }
            }
}

private data class InstalledAppSearchHit(
    val app: InstalledApp,
    val rank: Int,
    val sourceIndex: Int,
)
