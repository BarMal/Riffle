package com.riffle.core.domain.launcher.search

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppShortcutsByApp
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppCatalog

class LauncherSearchProvider {
    fun search(
        query: String,
        apps: List<InstalledApp>,
        settingsEntries: List<LauncherSearchSettingsEntry>,
        shortcutsByApp: AppShortcutsByApp = emptyMap(),
    ): List<LauncherSearchResult> {
        val queryTokens = normalizedSearchTokens(query)
        if (queryTokens.isEmpty()) return emptyList()

        val appHits =
            InstalledAppCatalog()
                .searchApps(
                    apps = apps,
                    query = query,
                    shortcutsByApp = shortcutsByApp,
                )
                .mapIndexed { index, app ->
                    LauncherSearchHit(
                        result = LauncherSearchResult.App(app),
                        rank = APP_MATCH_RANK,
                        sourceIndex = index,
                    )
                }
        val settingHits =
            settingsEntries.mapIndexedNotNull { index, entry ->
                entry.searchRank(queryTokens)?.let { rank ->
                    LauncherSearchHit(
                        result = LauncherSearchResult.Setting(entry),
                        rank = rank,
                        sourceIndex = index,
                    )
                }
            }

        return (appHits + settingHits)
            .sortedWith(
                compareBy<LauncherSearchHit> { hit -> hit.rank }
                    .thenBy { hit -> hit.result.type.sortOrder }
                    .thenBy { hit -> hit.result.title.lowercase() }
                    .thenBy { hit -> hit.sourceIndex },
            )
            .map { hit -> hit.result }
    }

    private fun LauncherSearchSettingsEntry.searchRank(queryTokens: List<String>): Int? {
        val title = title.lowercase()
        val secondaryValues =
            listOf(
                subtitle,
                section,
            ) + searchAliases
        val normalizedSecondaryValues = secondaryValues.map { value -> value.lowercase() }

        return when {
            title.containsAllSearchTokens(queryTokens) && title.startsWith(queryTokens.first()) -> SETTING_TITLE_RANK
            title.containsAllSearchTokens(queryTokens) -> SETTING_TITLE_RANK
            title.searchAcronym().containsAllSearchTokens(queryTokens) -> SETTING_TITLE_RANK
            normalizedSecondaryValues.containsAllSearchTokens(queryTokens) -> SETTING_SECONDARY_RANK
            normalizedSecondaryValues.map(String::searchAcronym).containsAllSearchTokens(queryTokens) ->
                SETTING_SECONDARY_RANK
            else -> null
        }
    }
}

sealed interface LauncherSearchResult {
    val key: String
    val title: String
    val subtitle: String?
    val type: LauncherSearchResultType

    data class App(
        val app: InstalledApp,
    ) : LauncherSearchResult {
        override val key: String = "app:${app.identity.stableSearchKey}"
        override val title: String = app.label
        override val subtitle: String = app.identity.packageName.value
        override val type: LauncherSearchResultType = LauncherSearchResultType.APP
    }

    data class Setting(
        val entry: LauncherSearchSettingsEntry,
    ) : LauncherSearchResult {
        override val key: String = "setting:${entry.id.value}"
        override val title: String = entry.title
        override val subtitle: String = entry.subtitle
        override val type: LauncherSearchResultType = LauncherSearchResultType.SETTING
    }
}

data class LauncherSearchSettingsEntry(
    val id: LauncherSearchSettingsEntryId,
    val title: String,
    val subtitle: String,
    val section: String,
    val searchAliases: List<String> = emptyList(),
)

@JvmInline
value class LauncherSearchSettingsEntryId(val value: String)

enum class LauncherSearchResultType(
    internal val sortOrder: Int,
) {
    APP(0),
    SETTING(1),
}

private data class LauncherSearchHit(
    val result: LauncherSearchResult,
    val rank: Int,
    val sourceIndex: Int,
)

private const val APP_MATCH_RANK = 0
private const val SETTING_TITLE_RANK = 0
private const val SETTING_SECONDARY_RANK = 2

private val AppIdentity.stableSearchKey: String
    get() = "${packageName.value}/${activityName.value}/${profile.id.value}"
