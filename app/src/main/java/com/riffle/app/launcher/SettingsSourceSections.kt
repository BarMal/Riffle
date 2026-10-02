package com.riffle.app.launcher

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.exclusions.ExclusionsSettingsText
import com.riffle.app.launcher.ics.IcsFeedsText
import com.riffle.app.launcher.ics.IcsFeedsUiState
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.sources.SearchQueryHolder
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsFeedId

internal const val SOURCE_CONTENT_LEVEL_TAG = "source-content-level"
internal const val SOURCE_OPEN_ALL_RULES_TAG = "source-open-all-rules"
internal const val SOURCE_SEARCH_MODEL_TAG = "source-search-model"
internal const val SOURCE_SEARCH_GLOBAL_TAG = "source-search-global"
internal const val SOURCE_ICS_ADD_TAG = "source-ics-add"

/** The app-ish sources whose rules make up "Hidden apps". */
private val AppSourceIds = setOf(SourceIds.ALL_APPS, SourceIds.RECENT_APPS, SourceIds.QUICK_ACTIONS)

@Composable
internal fun SourceSpecificSections(
    page: SettingsPage,
    host: WorkspaceSettingsHost,
    state: SettingsSurfaceState,
    onPageSelected: (SettingsPage) -> Unit,
    onAction: (LauncherShellAction) -> Unit,
) {
    when (page) {
        SettingsPage.SOURCE_NOTIFICATIONS -> {
            SourceNotificationsIntro()
            SettingsExclusionsPageContent(
                state = state,
                onAction = onAction,
                sources = setOf(SourceIds.NOTIFICATIONS),
            )
            SourceOpenAllRulesRow(onClick = { onPageSelected(SettingsPage.EXCLUSIONS) })
        }
        SettingsPage.SOURCE_APPS -> {
            SourceHiddenAppsIntro()
            SettingsExclusionsPageContent(state = state, onAction = onAction, sources = AppSourceIds)
            SourceOpenAllRulesRow(onClick = { onPageSelected(SettingsPage.EXCLUSIONS) })
        }
        SettingsPage.SOURCE_RSS -> SettingsRssPageContent(state = state, onAction = onAction)
        SettingsPage.SOURCE_SEARCH ->
            SourceSearchSections(sharedQuerySet = rememberSharedQuerySet(host.globalSearchQuery))
        SettingsPage.SOURCE_ICS -> HostedIcsSections(host = host, onPageSelected = onPageSelected)
        else -> Unit
    }
}

/** Notifications: the content level is only a note until that control exists (design section 6.2). */
@Composable
internal fun SourceNotificationsIntro() {
    SettingsSection(title = SourceDetailText.CONTENT_LEVEL_TITLE) {
        SettingsListRow(
            modifier = Modifier.testTag(SOURCE_CONTENT_LEVEL_TAG),
            title = SourceDetailText.CONTENT_LEVEL_TITLE,
            subtitle = SourceDetailText.CONTENT_LEVEL_LATER,
        )
    }
    Text(
        modifier = Modifier.padding(horizontal = RiffleSpacing.s),
        text = "${SourceDetailText.RULES_TITLE}. ${SourceDetailText.NOTIFICATION_RULES_NOTE}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun SourceHiddenAppsIntro() {
    Text(
        modifier = Modifier.padding(horizontal = RiffleSpacing.s),
        text = "${SourceDetailText.APP_RULES_TITLE}. ${SourceDetailText.APP_RULES_NOTE}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun SourceOpenAllRulesRow(onClick: () -> Unit) {
    SettingsSection(title = ExclusionsSettingsText.TITLE) {
        SettingsClickableRow(
            modifier = Modifier.testTag(SOURCE_OPEN_ALL_RULES_TAG),
            title = SourceDetailText.OPEN_ALL_RULES,
            subtitle = ExclusionsSettingsText.ROW_SUBTITLE,
            onClick = onClick,
        )
    }
}

/** Search: the per-lens query model, and whether the one shared query is set (never its text). */
@Composable
internal fun SourceSearchSections(sharedQuerySet: Boolean?) {
    SettingsSection(title = SourceDetailText.SEARCH_MODEL_TITLE) {
        SettingsListRow(
            modifier = Modifier.testTag(SOURCE_SEARCH_MODEL_TAG),
            title = "One query per lens",
            subtitle = SourceDetailText.SEARCH_MODEL_BODY,
        )
    }
    SettingsSection(title = SourceDetailText.SEARCH_GLOBAL_TITLE) {
        SettingsListRow(
            modifier =
                Modifier
                    .testTag(SOURCE_SEARCH_GLOBAL_TAG)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            title =
                when (sharedQuerySet) {
                    null -> SourceDetailText.SEARCH_NO_HOST
                    true -> SourceDetailText.SEARCH_GLOBAL_SET
                    false -> SourceDetailText.SEARCH_GLOBAL_NONE
                },
            subtitle = SourceDetailText.SEARCH_GLOBAL_PRIVACY,
        )
    }
}

/** Whether [holder] has a query (null without a holder). Only the fact is read: the text is never kept or shown. */
@Composable
private fun rememberSharedQuerySet(holder: SearchQueryHolder?): Boolean? {
    var isSet by remember(holder) { mutableStateOf(holder?.current()?.isNotEmpty()) }
    DisposableEffect(holder) {
        val stop = holder?.let { h -> h.observe { isSet = h.current().isNotEmpty() } }
        onDispose { stop?.invoke() }
    }
    return isSet
}

@Composable
private fun HostedIcsSections(
    host: WorkspaceSettingsHost,
    onPageSelected: (SettingsPage) -> Unit,
) {
    val controller = host.icsFeeds
    if (controller == null) {
        SettingsSection(title = IcsFeedsText.TITLE) {
            SettingsListRow(title = SourceDetailText.ICS_NOT_AVAILABLE)
        }
    } else {
        DisposableEffect(controller) {
            controller.open()
            onDispose { controller.close() }
        }
        val state by controller.state.collectAsState()
        SourceIcsSections(
            state = state,
            onRefresh = controller::refreshAll,
            onToggle = controller::setEnabled,
            onRemove = controller::remove,
            onOpenFeeds = { onPageSelected(SettingsPage.ICS_FEEDS) },
        )
    }
}

/** Calendar feeds: the existing list with per-feed status and Refresh, and a link to the page that adds feeds. */
@Composable
internal fun SourceIcsSections(
    state: IcsFeedsUiState,
    onRefresh: () -> Unit,
    onToggle: (IcsFeedId, Boolean) -> Unit,
    onRemove: (IcsFeedId) -> Unit,
    onOpenFeeds: () -> Unit,
) {
    IcsFeedsListContent(state = state, onRefresh = onRefresh, onToggle = onToggle, onRemove = onRemove)
    SettingsSection(title = SourceDetailText.ADD_CALENDAR_FEED) {
        SettingsClickableRow(
            modifier = Modifier.testTag(SOURCE_ICS_ADD_TAG),
            title = SourceDetailText.ICS_OPEN_FEEDS,
            subtitle = SourceDetailText.ADD_CALENDAR_FEED_BODY,
            onClick = onOpenFeeds,
        )
    }
}
