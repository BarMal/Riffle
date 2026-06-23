package com.riffle.core.domain.launcher.apps

class InstalledAppSearchIndex(
    private val apps: List<InstalledApp>,
    private val shortcutsByApp: AppShortcutsByApp = emptyMap(),
) {
    fun search(query: String): List<InstalledApp> =
        query.trim().lowercase().let { normalizedQuery ->
            when {
                normalizedQuery.isBlank() -> apps
                else ->
                    apps
                        .mapIndexedNotNull { index, app ->
                            app.searchRank(normalizedQuery)?.let { rank ->
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

    private fun InstalledApp.searchRank(query: String): Int? =
        listOfNotNull(
            labelRank(query),
            shortcutRank(query),
            identityRank(query),
        ).minOrNull()

    private fun InstalledApp.labelRank(query: String): Int? =
        label.lowercase().let { label ->
            when {
                label.startsWith(query) -> 0
                label.contains(query) -> 1
                else -> null
            }
        }

    private fun InstalledApp.shortcutRank(query: String): Int? =
        shortcutsByApp[identity].orEmpty()
            .mapNotNull { shortcut -> shortcut.searchRank(query) }
            .minOrNull()

    private fun InstalledApp.identityRank(query: String): Int? =
        listOf(
            identity.packageName.value,
            identity.activityName.value,
            identity.profile.id.value,
            identity.profile.type.name,
        )
            .map { token -> token.lowercase() }
            .takeIf { tokens -> tokens.any { token -> token.contains(query) } }
            ?.let { 3 }

    private fun AppShortcut.searchRank(query: String): Int? =
        listOf(
            shortLabel,
            longLabel.orEmpty(),
            id.value,
        )
            .map { token -> token.lowercase() }
            .takeIf { tokens -> tokens.any { token -> token.contains(query) } }
            ?.let { 2 }
}

private data class InstalledAppSearchHit(
    val app: InstalledApp,
    val rank: Int,
    val sourceIndex: Int,
)
