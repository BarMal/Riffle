package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.onboarding.LocalWalkthroughLauncher
import com.riffle.app.launcher.onboarding.OnboardingText
import com.riffle.core.domain.launcher.home.WallpaperSource
import com.riffle.core.domain.launcher.home.workspaceGrid
import com.riffle.core.domain.launcher.settings.homeSystemBars

/**
 * The open Settings page. Pages are drawn from the shared rows in `SettingsRows.kt`; [onSettingSelected] opens a page
 * scrolled to one setting (search), [onPageSelected] just a page.
 */
@Composable
@Suppress("CyclomaticComplexMethod", "LongMethod")
internal fun SettingsPageContent(
    modifier: Modifier,
    state: SettingsSurfaceState,
    page: SettingsPage,
    onPageSelected: (SettingsPage) -> Unit,
    onAction: (LauncherShellAction) -> Unit,
    onSettingSelected: (SettingsTarget) -> Unit = { target -> onPageSelected(target.page) },
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (page) {
            SettingsPage.MAIN ->
                SettingsMainPageContent(
                    state = state,
                    onPageSelected = onPageSelected,
                    onSettingSelected = onSettingSelected,
                )

            SettingsPage.HOME -> SettingsHomePageContent(state = state, onAction = onAction)
            SettingsPage.DOCK ->
                SettingsDockPageContent(
                    state = state,
                    onPageSelected = onPageSelected,
                    onAction = onAction,
                )
            SettingsPage.FLOATING_DOCK -> SettingsFloatingDockPageContent(state = state, onAction = onAction)
            SettingsPage.LIBRARY ->
                SettingsLibraryPageContent(state = state, onPageSelected = onPageSelected, onAction = onAction)
            SettingsPage.HIDDEN_APPS -> SettingsHiddenAppsPageContent(state = state, onAction = onAction)
            SettingsPage.STREAM ->
                SettingsStreamPageContent(
                    state = state,
                    onPageSelected = onPageSelected,
                    onAction = onAction,
                )
            SettingsPage.STREAM_NOTIFICATIONS ->
                SettingsStreamNotificationsPageContent(state = state, onPageSelected = onPageSelected)
            SettingsPage.HIDDEN_ITEMS -> SettingsExclusionsPageContent(state = state, onAction = onAction)
            SettingsPage.STREAM_AGENDA ->
                SettingsStreamAgendaPageContent(
                    state = state,
                    onPageSelected = onPageSelected,
                )
            SettingsPage.CALENDAR_FEEDS -> SettingsIcsFeedsPageContent()
            SettingsPage.STREAM_TASKS -> SettingsStreamTasksPageContent()
            SettingsPage.STREAM_FEEDS -> SettingsStreamFeedsPageContent(state = state, onAction = onAction)
            SettingsPage.APPEARANCE -> SettingsAppearancePageContent(state = state, onAction = onAction)
            SettingsPage.GESTURES -> SettingsGesturesPageContent(state = state, onAction = onAction)
            SettingsPage.MOTION -> SettingsMotionPageContent(state = state, onAction = onAction)
            SettingsPage.PRIVACY -> SettingsPrivacyPageContent(state = state, onAction = onAction)
            SettingsPage.BACKUP -> SettingsBackupPageContent(onAction = onAction)
            SettingsPage.VERSION -> SettingsVersionPageContent(state = state)
        }
    }
}

@Composable
private fun SettingsDeviceConfigurationTabs(
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsLayoutDeviceTabs(
        selectedDeviceClass = state.selectedLayoutDeviceClass,
        availableDeviceClasses = state.availableLayoutDeviceClasses,
        onAction = onAction,
    )
}

/** The device tabs shared by the pages whose values are stored per posture (Home, Dock, Library, Stream). */
@Composable
internal fun SettingsPostureTabs(
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
) = SettingsDeviceConfigurationTabs(state = state, onAction = onAction)

@Composable
private fun SettingsHomePageContent(
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
) {
    val appearance = state.settings.appearance
    val homeSystemBars = appearance.homeSystemBars

    SettingsDeviceConfigurationTabs(state = state, onAction = onAction)
    SettingsSection(title = stringResource(R.string.settings_section_grid)) {
        HomeGridSetting(
            grid = state.homeLayout.settings.grid,
            visibleDimensions = state.homeLayout.workspaceGrid,
            onAction = onAction,
        )
    }
    SettingsSection(title = stringResource(R.string.settings_section_icons_and_labels)) {
        HomeLabelSetting(
            settings = state.homeLayout.settings.labels,
            onAction = onAction,
        )
    }
    SettingsReturnBehaviorSection(
        current = state.settings.home.returnBehavior,
        onSelect = { behavior -> onAction(LauncherShellAction.SelectReturnBehavior(behavior)) },
    )
    SettingsSection(title = stringResource(R.string.settings_section_wallpaper)) {
        SettingsChoiceRow(
            modifier = Modifier.settingAnchor(SettingKey.HOME_WALLPAPER),
            title = SettingKey.HOME_WALLPAPER.label(),
            options = WallpaperSource.entries,
            selected = appearance.wallpaper.source,
            onSelect = { source -> onAction(LauncherShellAction.SelectWallpaperSource(source)) },
            label = { source ->
                if (source == WallpaperSource.SYSTEM) {
                    stringResource(R.string.settings_wallpaper_system)
                } else {
                    stringResource(R.string.settings_wallpaper_solid)
                }
            },
        )
        WallpaperScrollModeSetting(
            selectedSource = appearance.wallpaper.source,
            selectedMode = appearance.wallpaper.scrollMode,
            onAction = onAction,
        )
        SettingsClickableRow(
            modifier = Modifier.settingAnchor(SettingKey.HOME_CHANGE_WALLPAPER),
            title = SettingKey.HOME_CHANGE_WALLPAPER.label(),
            subtitle = stringResource(R.string.settings_change_wallpaper_subtitle),
            onClick = { onAction(LauncherShellAction.ChangeWallpaper) },
        )
    }
    SettingsSection(title = stringResource(R.string.settings_section_full_screen)) {
        SettingsSwitchRow(
            modifier = Modifier.settingAnchor(SettingKey.HOME_HIDE_STATUS_BAR),
            title = SettingKey.HOME_HIDE_STATUS_BAR.label(),
            subtitle = stringResource(R.string.settings_hide_status_bar_subtitle),
            checked = homeSystemBars.statusBarHidden,
            onCheckedChange = { hidden ->
                onAction(LauncherShellAction.SelectHomeStatusBarHidden(hidden))
            },
        )
        SettingsSwitchRow(
            modifier = Modifier.settingAnchor(SettingKey.HOME_HIDE_NAVIGATION_BAR),
            title = SettingKey.HOME_HIDE_NAVIGATION_BAR.label(),
            subtitle = stringResource(R.string.settings_hide_navigation_bar_subtitle),
            checked = homeSystemBars.navigationBarHidden,
            onCheckedChange = { hidden ->
                onAction(LauncherShellAction.SelectHomeNavigationBarHidden(hidden))
            },
        )
    }
    LocalWalkthroughLauncher.current?.let { runSetup ->
        SettingsSection(title = stringResource(R.string.settings_section_set_up)) {
            SettingsClickableRow(
                modifier = Modifier.settingAnchor(SettingKey.HOME_SET_UP_AGAIN),
                title = OnboardingText.SETTINGS_ROW,
                subtitle = OnboardingText.SETTINGS_ROW_SUBTITLE,
                onClick = runSetup,
            )
        }
    }
}

@Composable
private fun SettingsDockPageContent(
    state: SettingsSurfaceState,
    onPageSelected: (SettingsPage) -> Unit,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsDeviceConfigurationTabs(state = state, onAction = onAction)
    SettingsSection(title = stringResource(R.string.settings_page_dock)) {
        DockSetting(
            dock = state.homeLayout.dock,
            onAction = onAction,
        )
    }
    SettingsSection(title = stringResource(R.string.settings_section_dynamic_side)) {
        DockDynamicSideSetting(
            dock = state.homeLayout.dock,
            notificationAccessStatus = state.notificationAccessStatus,
            onAction = onAction,
        )
    }
    SettingsSection(title = stringResource(R.string.settings_page_floating_dock)) {
        SettingsClickableRow(
            title = stringResource(R.string.settings_page_floating_dock),
            subtitle = stringResource(R.string.settings_page_floating_dock_summary),
            onClick = { onPageSelected(SettingsPage.FLOATING_DOCK) },
        )
    }
}

@Composable
private fun SettingsFloatingDockPageContent(
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsSection(title = stringResource(R.string.settings_page_floating_dock)) {
        OverlayDockSetting(
            settings = state.settings.overlayDock,
            permissionStatus = state.overlayDockPermissionStatus,
            onAction = onAction,
        )
    }
}

@Composable
private fun SettingsAppearancePageContent(
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
) {
    val appearance = state.settings.appearance

    SettingsSection(title = stringResource(R.string.settings_section_colour)) {
        ThemeModeSetting(selectedMode = appearance.themeMode, onAction = onAction)
        ThemeAccentSetting(selectedAccent = appearance.themeAccent, onAction = onAction)
        ThemeColorsSetting(colors = appearance.themeColors, onAction = onAction)
    }
    SettingsSection(title = stringResource(R.string.settings_section_glass)) {
        LiquidGlassSetting(
            settings = state.settings.liquidGlass,
            reducedTransparency = appearance.reducedTransparency,
            onAction = onAction,
        )
    }
    SettingsSection(title = stringResource(R.string.settings_section_shape_and_type)) {
        ThemeCornerStyleSetting(selectedStyle = appearance.themeCornerStyle, onAction = onAction)
        ThemeTypographySetting(selectedTypography = appearance.themeTypography, onAction = onAction)
    }
}

@Composable
private fun SettingsGesturesPageContent(
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
) {
    HomeSwipeGestureSetting(
        settings = state.settings.gestures,
        installedApps = state.installedApps,
        appShortcutsByApp = state.appShortcutsByApp,
        onAction = onAction,
    )
}
