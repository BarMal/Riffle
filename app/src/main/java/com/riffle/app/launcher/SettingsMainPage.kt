package com.riffle.app.launcher

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.core.domain.launcher.HomeRoleStatus
import com.riffle.core.domain.launcher.OverlayDockPermissionStatus
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus

internal fun settingsMainRowTestTag(page: SettingsPage): String = "settings-main-row-${page.name.lowercase()}"

internal fun settingsSearchResultTestTag(entry: SettingsSearchEntry): String = "settings-result-${entry.id}"

/**
 * The main page: a search field and the places Settings is organised by. Typing searches every individual setting
 * and page (label, path and keywords); a result opens its page scrolled to that setting.
 */
@Composable
internal fun SettingsMainPageContent(
    state: SettingsSurfaceState,
    onPageSelected: (SettingsPage) -> Unit,
    onSettingSelected: (SettingsTarget) -> Unit,
) {
    val text = rememberSettingsText()
    val query = rememberSaveable { mutableStateOf("") }
    val index = remember(text) { settingsSearchIndex(text) }

    AppSearchField(
        modifier = Modifier.fillMaxWidth().testTag(SETTINGS_SEARCH_FIELD_TEST_TAG),
        query = query.value,
        onQueryChanged = { value -> query.value = value },
        label = stringResource(R.string.settings_search_label),
    )
    if (query.value.isBlank()) {
        SettingsSection(title = stringResource(R.string.settings_main_section)) {
            settingsMainPageRows.forEach { page ->
                SettingsClickableRow(
                    modifier = Modifier.testTag(settingsMainRowTestTag(page)),
                    title = stringResource(page.titleRes),
                    subtitle = state.settingsOverviewStatus().rowSummary(page, text),
                    onClick = { onPageSelected(page) },
                )
            }
        }
    } else {
        val results = remember(index, query.value) { index.matching(query.value) }
        settingsSearchSummaryText(text, query.value, results.size)?.let { summary ->
            Text(
                modifier = Modifier.padding(horizontal = 8.dp),
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (results.isEmpty()) {
            Text(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 24.dp),
                text = stringResource(R.string.settings_search_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            SettingsSection(title = stringResource(R.string.settings_search_results_section)) {
                results.forEach { entry ->
                    SettingsClickableRow(
                        modifier = Modifier.testTag(settingsSearchResultTestTag(entry)),
                        title = entry.label,
                        subtitle = entry.path.ifEmpty { stringResource(R.string.settings_page_main) },
                        onClick = { onSettingSelected(SettingsTarget(entry.page, entry.key)) },
                    )
                }
            }
        }
    }
}

internal const val SETTINGS_SEARCH_FIELD_TEST_TAG = "settings-search-field"

/** The summary under a main-page row: what is in it, plus the state that matters for Library and access. */
internal fun SettingsOverviewStatus.rowSummary(
    page: SettingsPage,
    text: SettingsText,
): String =
    when (page) {
        SettingsPage.LIBRARY -> text.text(R.string.settings_page_library_summary_hidden, hiddenAppCount)
        SettingsPage.PRIVACY -> permissionsSummary(text)
        else -> text.text(page.summaryRes)
    }

private fun SettingsOverviewStatus.permissionsSummary(text: SettingsText): String =
    listOf(
        notificationAccessStatus.overviewLabel(text, R.string.settings_overview_notifications),
        homeRoleStatus.overviewLabel(text),
        overlayDockPermissionStatus.overviewLabel(text, R.string.settings_overview_floating_dock),
    ).joinToString(separator = " · ")

private fun NotificationAccessStatus.overviewLabel(
    text: SettingsText,
    feature: Int,
): String {
    val name = text.text(feature)
    return when (this) {
        NotificationAccessStatus.GRANTED -> text.text(R.string.settings_overview_allowed, name)
        NotificationAccessStatus.NOT_GRANTED -> text.text(R.string.settings_overview_not_allowed, name)
        NotificationAccessStatus.REVOKED -> text.text(R.string.settings_overview_revoked, name)
        NotificationAccessStatus.UNKNOWN -> text.text(R.string.settings_overview_checking, name)
    }
}

private fun OverlayDockPermissionStatus.overviewLabel(
    text: SettingsText,
    feature: Int,
): String {
    val name = text.text(feature)
    return when (this) {
        OverlayDockPermissionStatus.GRANTED -> text.text(R.string.settings_overview_allowed, name)
        OverlayDockPermissionStatus.NOT_GRANTED -> text.text(R.string.settings_overview_not_allowed, name)
        OverlayDockPermissionStatus.UNKNOWN -> text.text(R.string.settings_overview_checking, name)
    }
}

private fun HomeRoleStatus.overviewLabel(text: SettingsText): String =
    when (this) {
        HomeRoleStatus.DEFAULT_HOME -> text.text(R.string.settings_overview_home_set)
        HomeRoleStatus.NOT_DEFAULT_HOME -> text.text(R.string.settings_overview_home_not_set)
        HomeRoleStatus.UNKNOWN -> text.text(R.string.settings_overview_home_unknown)
    }
