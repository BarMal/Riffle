package com.riffle.app.launcher

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.riffle.app.R
import com.riffle.core.domain.launcher.search.wider.SearchSourceId
import com.riffle.core.domain.launcher.search.wider.WiderSearchMerger
import com.riffle.core.domain.launcher.settings.RESULTS_PER_GROUP_OPTIONS
import com.riffle.core.domain.launcher.settings.SearchSettings

internal const val SEARCH_SOURCE_SWITCH_TEST_TAG_PREFIX = "search-source-"
internal const val SEARCH_PER_GROUP_TEST_TAG_PREFIX = "search-per-group-"
internal const val SEARCH_ORDER_TEST_TAG_PREFIX = "search-order-"

/** The group's name in search and in the order setting. */
@StringRes
internal fun SearchSourceId.groupNameRes(): Int =
    when (this) {
        SearchSourceId.CALCULATOR -> R.string.search_group_calculator
        SearchSourceId.SYSTEM_SETTINGS -> R.string.search_group_system_settings
        SearchSourceId.CONTACTS -> R.string.search_group_contacts
        SearchSourceId.FILES -> R.string.search_group_files
    }

/**
 * Library > Search, below "Search results": which sources are in search, the order of the groups below the apps and
 * how many rows a group shows. Apps and app shortcuts are always searched, so they have no switch. One stored value
 * each (`SearchSettings`); every change is one [LauncherShellAction.SetSearchSettings].
 */
@Composable
internal fun SettingsSearchSourceRows(
    settings: SearchSettings,
    onAction: (LauncherShellAction) -> Unit,
) {
    fun change(update: SearchSettings) = onAction(LauncherShellAction.SetSearchSettings(update))

    SearchSwitch(
        modifier = Modifier.settingAnchor(SettingKey.LIBRARY_SEARCH_LAUNCHER_SETTINGS),
        key = SettingKey.LIBRARY_SEARCH_LAUNCHER_SETTINGS,
        subtitle = R.string.settings_search_launcher_settings_subtitle,
        checked = settings.launcherSettings,
        tag = "launcher",
    ) { change(settings.copy(launcherSettings = it)) }
    SearchSwitch(
        modifier = Modifier.settingAnchor(SettingKey.LIBRARY_SEARCH_CALCULATOR),
        key = SettingKey.LIBRARY_SEARCH_CALCULATOR,
        subtitle = R.string.settings_search_calculator_subtitle,
        checked = settings.calculator,
        tag = "calculator",
    ) { change(settings.withSource(SearchSourceId.CALCULATOR, it)) }
    SearchSwitch(
        modifier = Modifier.settingAnchor(SettingKey.LIBRARY_SEARCH_SYSTEM_SETTINGS),
        key = SettingKey.LIBRARY_SEARCH_SYSTEM_SETTINGS,
        subtitle = R.string.settings_search_system_settings_subtitle,
        checked = settings.systemSettings,
        tag = "system",
    ) { change(settings.withSource(SearchSourceId.SYSTEM_SETTINGS, it)) }
    SearchSwitch(
        modifier = Modifier.settingAnchor(SettingKey.LIBRARY_SEARCH_CONTACTS),
        key = SettingKey.LIBRARY_SEARCH_CONTACTS,
        subtitle = R.string.settings_search_contacts_subtitle,
        checked = settings.contacts,
        tag = "contacts",
    ) { change(settings.withSource(SearchSourceId.CONTACTS, it)) }
    SearchSwitch(
        modifier = Modifier.settingAnchor(SettingKey.LIBRARY_SEARCH_FILES),
        key = SettingKey.LIBRARY_SEARCH_FILES,
        subtitle = R.string.settings_search_files_subtitle,
        checked = settings.files,
        tag = "files",
    ) { change(settings.withSource(SearchSourceId.FILES, it)) }
    SearchSwitch(
        modifier = Modifier.settingAnchor(SettingKey.LIBRARY_SEARCH_WEB),
        key = SettingKey.LIBRARY_SEARCH_WEB,
        subtitle = R.string.settings_search_web_subtitle,
        checked = settings.webHandoff,
        tag = "web",
    ) { change(settings.copy(webHandoff = it)) }
    // The heading row and the three group rows are one setting: search scrolls to the group of them.
    Column(modifier = Modifier.settingAnchor(SettingKey.LIBRARY_SEARCH_ORDER)) {
        SettingsListRow(
            title = SettingKey.LIBRARY_SEARCH_ORDER.label(),
            subtitle = stringResource(R.string.settings_search_order_subtitle),
        )
        val order = WiderSearchMerger.completeOrder(settings.groupOrder)
        order.forEachIndexed { index, source ->
            val name = stringResource(source.groupNameRes())
            SettingsListRow(
                title = name,
                trailingContent = {
                    Row {
                        MoveButton(
                            source,
                            name,
                            delta = -1,
                            enabled = index > 0,
                        ) { change(settings.movedGroup(source, -1)) }
                        MoveButton(source, name, delta = 1, enabled = index < order.lastIndex) {
                            change(settings.movedGroup(source, 1))
                        }
                    }
                },
            )
        }
    }
    SettingsChoiceRow(
        modifier = Modifier.settingAnchor(SettingKey.LIBRARY_SEARCH_PER_GROUP),
        title = SettingKey.LIBRARY_SEARCH_PER_GROUP.label(),
        subtitle = stringResource(R.string.settings_search_per_group_subtitle),
        options = RESULTS_PER_GROUP_OPTIONS,
        selected = settings.resultsPerGroup,
        onSelect = { count -> change(settings.copy(resultsPerGroup = count)) },
        label = { count -> count.toString() },
        optionTag = { count -> "$SEARCH_PER_GROUP_TEST_TAG_PREFIX$count" },
    )
}

@Composable
private fun SearchSwitch(
    modifier: Modifier,
    key: SettingKey,
    @StringRes subtitle: Int,
    checked: Boolean,
    tag: String,
    onChange: (Boolean) -> Unit,
) {
    SettingsSwitchRow(
        modifier = modifier.testTag("$SEARCH_SOURCE_SWITCH_TEST_TAG_PREFIX$tag"),
        title = key.label(),
        subtitle = stringResource(subtitle),
        checked = checked,
        onCheckedChange = onChange,
    )
}

@Composable
private fun MoveButton(
    source: SearchSourceId,
    name: String,
    delta: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val description =
        stringResource(
            if (delta < 0) R.string.settings_stream_move_up_named else R.string.settings_stream_move_down_named,
            name,
        )
    TextButton(
        modifier =
            Modifier
                .testTag("$SEARCH_ORDER_TEST_TAG_PREFIX${source.name}-${if (delta < 0) "up" else "down"}")
                .semantics { contentDescription = description },
        enabled = enabled,
        onClick = onClick,
    ) {
        SettingsButtonText(text = stringResource(if (delta < 0) R.string.stream_move_up else R.string.stream_move_down))
    }
}
