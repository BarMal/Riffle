package com.riffle.app.screenshots

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.riffle.app.launcher.HOME_DOCK_PULL_TEST_TAG
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.library.LibraryView
import com.riffle.core.domain.launcher.settings.FeatureSettings
import com.riffle.core.domain.launcher.settings.HomeMode
import com.riffle.core.domain.launcher.settings.with

/** Fourteen more apps, so the fixture's twelve become twenty-six: two pages of Home's 4 by 5 grid. */
internal val pagedLibraryExtraApps: List<InstalledApp> =
    listOf(
        "Alarm", "Books", "Compass", "Docs", "Fitness", "Games", "Health",
        "Invoices", "Jobs", "Keep", "Launcher", "Podcasts", "Wallet", "Zoo",
    ).map { label ->
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.sample.${label.lowercase()}"),
                    activityName = AppActivityName(".MainActivity"),
                    profile = AppProfile.personal(),
                ),
            label = label,
        )
    }

internal fun pagedApp(label: String): InstalledApp = pagedLibraryExtraApps.first { app -> app.label == label }

/** The fixture shell with every app of both fixtures installed. */
internal fun LauncherShellState.withManyApps(): LauncherShellState =
    copy(installedApps = installedApps + pagedLibraryExtraApps)

internal fun LauncherShellState.libraryOpenAs(view: LibraryView): LauncherShellState =
    copy(
        destination = ShellDestination.APP_DRAWER,
        launcherSettings =
            launcherSettings.copy(
                appDrawer =
                    launcherSettings.appDrawer.withLibraryView(homeLayoutSet.activeKey.deviceClass, view),
            ),
    )

internal fun LauncherShellState.withHomeMode(mode: HomeMode): LauncherShellState =
    copy(launcherSettings = launcherSettings.copy(home = launcherSettings.home.copy(mode = mode)))

internal fun LauncherShellState.withFeatures(features: FeatureSettings): LauncherShellState =
    copy(launcherSettings = launcherSettings.copy(features = features))

/**
 * The shell's answer to the actions these tests send, as the real reducers would give it for the settings and the
 * navigation they touch; everything else changes nothing.
 */
internal fun LauncherShellState.answering(action: LauncherShellAction): LauncherShellState =
    when (action) {
        is LauncherShellAction.SelectLibraryView ->
            copy(
                launcherSettings =
                    launcherSettings.copy(
                        appDrawer =
                            launcherSettings.appDrawer.withLibraryView(
                                action.deviceClass ?: homeLayoutSet.activeKey.deviceClass,
                                action.view,
                            ),
                    ),
            )

        is LauncherShellAction.SelectHomeMode -> withHomeMode(action.mode)
        is LauncherShellAction.SetSimpleMode ->
            withFeatures(launcherSettings.features.copy(simpleMode = action.enabled))

        is LauncherShellAction.SetFeatureEnabled ->
            withFeatures(launcherSettings.features.with(action.feature, action.enabled))

        LauncherShellAction.OpenHome -> copy(destination = ShellDestination.HOME)
        else -> this
    }

/** A long-press on the empty space in the top-left corner of the node tagged [tag] (no icon is there). */
internal fun ComposeContentTestRule.longPressEmptySpace(tag: String) {
    onNodeWithTag(tag).performTouchInput { longClick(Offset(4f, 4f)) }
}

/** The labels of the Dock's custom accessibility actions. */
internal fun ComposeContentTestRule.dockActionLabels(): List<String> =
    onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).fetchSemanticsNode()
        .config[androidx.compose.ui.semantics.SemanticsActions.CustomActions].map { action -> action.label }
