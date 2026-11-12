package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.riffle.app.R
import com.riffle.app.launcher.library.titleRes
import com.riffle.core.domain.launcher.library.LibraryView
import com.riffle.core.domain.launcher.settings.MAX_APP_DRAWER_ICON_GRID_COLUMNS
import com.riffle.core.domain.launcher.settings.MIN_APP_DRAWER_ICON_GRID_COLUMNS
import com.riffle.core.domain.launcher.settings.SearchResultPresentation

internal const val LIBRARY_VIEW_TEST_TAG_PREFIX = "library-view-"
internal const val APP_DRAWER_GRID_COLUMNS_TEST_TAG_PREFIX = "app-drawer-grid-columns-"
internal const val SEARCH_RESULTS_TEST_TAG_PREFIX = "search-results-"

/**
 * Library: Show as (the same stored choice as the Library's own long-press on its header), the icon grid's columns,
 * how search results read, and the Hidden apps sub-page.
 */
@Composable
internal fun SettingsLibraryPageContent(
    state: SettingsSurfaceState,
    onPageSelected: (SettingsPage) -> Unit,
    onAction: (LauncherShellAction) -> Unit,
) {
    val deviceClass = state.selectedLayoutDeviceClass
    val view = state.settings.appDrawer.libraryViewFor(deviceClass)

    SettingsPostureTabs(state = state, onAction = onAction)
    SettingsSection(title = stringResource(R.string.settings_section_view)) {
        SettingsChoiceRow(
            modifier = Modifier.settingAnchor(SettingKey.LIBRARY_SHOW_AS),
            title = SettingKey.LIBRARY_SHOW_AS.label(),
            subtitle = stringResource(R.string.settings_library_show_as_subtitle),
            options = LibraryView.entries,
            selected = view,
            onSelect = { option -> onAction(LauncherShellAction.SelectLibraryView(option, deviceClass)) },
            label = { option -> stringResource(option.titleRes()) },
            optionTag = { option -> "$LIBRARY_VIEW_TEST_TAG_PREFIX${option.name}" },
        )
        SettingsChoiceRow(
            modifier = Modifier.settingAnchor(SettingKey.LIBRARY_GRID_COLUMNS),
            title = SettingKey.LIBRARY_GRID_COLUMNS.label(),
            subtitle = stringResource(R.string.settings_library_grid_columns_subtitle),
            enabled = view == LibraryView.Grid,
            options = (MIN_APP_DRAWER_ICON_GRID_COLUMNS..MAX_APP_DRAWER_ICON_GRID_COLUMNS).toList(),
            selected = state.settings.appDrawer.iconGridColumns,
            onSelect = { columns -> onAction(LauncherShellAction.SelectAppDrawerIconGridColumns(columns)) },
            label = { columns -> columns.toString() },
            optionTag = { columns -> "$APP_DRAWER_GRID_COLUMNS_TEST_TAG_PREFIX$columns" },
        )
    }
    SettingsSection(title = stringResource(R.string.settings_section_search)) {
        SettingsChoiceRow(
            modifier = Modifier.settingAnchor(SettingKey.LIBRARY_SEARCH_RESULTS),
            title = SettingKey.LIBRARY_SEARCH_RESULTS.label(),
            subtitle = stringResource(R.string.settings_library_search_results_subtitle),
            options = SearchResultPresentation.entries,
            selected = state.settings.search.resultPresentation,
            onSelect = { presentation ->
                onAction(LauncherShellAction.SelectSearchResultPresentation(presentation))
            },
            label = { presentation ->
                if (presentation == SearchResultPresentation.ICONS) {
                    stringResource(R.string.settings_search_results_icons)
                } else {
                    stringResource(R.string.settings_search_results_list)
                }
            },
            optionTag = { presentation -> "$SEARCH_RESULTS_TEST_TAG_PREFIX${presentation.name}" },
        )
    }
    SettingsSection(title = stringResource(R.string.settings_section_hidden_and_refresh)) {
        SettingsClickableRow(
            title = stringResource(R.string.settings_page_hidden_apps),
            subtitle = stringResource(R.string.settings_hidden_apps_count, state.hiddenApps.size),
            onClick = { onPageSelected(SettingsPage.HIDDEN_APPS) },
        )
        AppRefreshSetting(onAction = onAction)
    }
}
