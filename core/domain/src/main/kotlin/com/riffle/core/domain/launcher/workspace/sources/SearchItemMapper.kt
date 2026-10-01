package com.riffle.core.domain.launcher.workspace.sources

import com.riffle.core.domain.launcher.search.LauncherSearchResult
import com.riffle.core.domain.launcher.search.LauncherSearchResultType
import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.ItemAction
import com.riffle.core.domain.launcher.workspace.ItemExtKey
import com.riffle.core.domain.launcher.workspace.ItemExtValue
import com.riffle.core.domain.launcher.workspace.ItemId
import com.riffle.core.domain.launcher.workspace.ItemTarget
import com.riffle.core.domain.launcher.workspace.SourceIds

/**
 * Maps the existing [LauncherSearchResult]s to [Item]s, keeping the provider's ranking. Results are grouped
 * by type (`apps`, `settings`). The query text is deliberately absent from every field: ids come from the
 * result's own key and nothing echoes what was typed.
 */
class SearchItemMapper {
    fun items(results: List<LauncherSearchResult>): List<Item> = results.map { result -> result.toItem() }

    private fun LauncherSearchResult.toItem(): Item =
        when (this) {
            is LauncherSearchResult.App -> appItem(this)
            is LauncherSearchResult.Setting -> settingItem(this)
        }

    private fun appItem(result: LauncherSearchResult.App): Item {
        val identity = result.app.identity
        return Item(
            id = ItemId("${SourceIds.SEARCH.value}:${result.key}"),
            sourceId = SourceIds.SEARCH,
            target = ItemTarget.App(identity.packageName.value, identity.profile.id.value),
            title = result.title,
            subtitle = result.subtitle,
            icon = ItemImageKeys.appIcon(identity),
            groupKey = result.type.groupKey(),
            groupLabel = result.type.groupLabel(),
            actions = listOf(ItemAction.Open()),
            ext = appProfileExt(identity.profile.type),
        )
    }

    private fun settingItem(result: LauncherSearchResult.Setting): Item =
        Item(
            id = ItemId("${SourceIds.SEARCH.value}:${result.key}"),
            sourceId = SourceIds.SEARCH,
            target = ItemTarget.Intent("${SETTING_TOKEN_PREFIX}${result.entry.id.value}"),
            title = result.title,
            subtitle = result.subtitle,
            groupKey = result.type.groupKey(),
            groupLabel = result.type.groupLabel(),
            actions = listOf(ItemAction.Open()),
            ext = mapOf(SECTION_KEY to ItemExtValue.Text(result.entry.section)),
        )

    private fun LauncherSearchResultType.groupKey(): String =
        when (this) {
            LauncherSearchResultType.APP -> "apps"
            LauncherSearchResultType.SETTING -> "settings"
        }

    private fun LauncherSearchResultType.groupLabel(): String =
        when (this) {
            LauncherSearchResultType.APP -> "Apps"
            LauncherSearchResultType.SETTING -> "Settings"
        }

    companion object {
        /** [ItemTarget.Intent] tokens for launcher settings pages start with this, followed by the entry id. */
        const val SETTING_TOKEN_PREFIX = "launcher-setting:"

        val SECTION_KEY = ItemExtKey("search.section")
    }
}
