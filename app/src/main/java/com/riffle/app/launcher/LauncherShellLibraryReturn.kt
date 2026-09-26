package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.home.HomeEditMode
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.LauncherViewModeAvailability
import com.riffle.core.domain.launcher.home.LibraryExitTrigger
import com.riffle.core.domain.launcher.home.modeAfterLeavingLibrary

/**
 * Library, the app drawer, may have been left by [trigger] (Decision 10, #1243).
 *
 * When the user's "After leaving Library" setting is Home and Library is showing, the device being
 * held switches to its Home mode, exactly as any other mode change does; otherwise nothing changes.
 */
internal fun LauncherShellState.withLibraryLeft(
    trigger: LibraryExitTrigger,
    homeLayoutRepository: HomeLayoutRepository,
    viewModeAvailability: LauncherViewModeAvailability,
): LauncherShellState =
    homeLayoutSet
        .modeAfterLeavingLibrary(trigger = trigger, returnTarget = launcherSettings.appDrawer.afterLeavingLibrary)
        ?.let { homeMode ->
            withSelectedHomeLayoutMode(
                mode = homeMode,
                homeLayoutRepository = homeLayoutRepository,
                viewModeAvailability = viewModeAvailability,
                // Always the device being held, even when Settings is on screen and configuring
                // another device class's layout.
                targetDeviceClass = homeLayoutSet.activeKey.deviceClass,
            ).withHomeScreenLibraryApps(homeLayoutRepository)
        }
        ?: this

/**
 * Whether system Back, with nothing nested to unwind, should leave Library for Home: Library is on
 * screen, browsing (not being edited), and the setting returns Home.
 */
internal val LauncherShellState.backLeavesLibrary: Boolean
    get() =
        destination == ShellDestination.HOME &&
            homeLayout.editMode == HomeEditMode.Browsing &&
            homeLayoutSet.modeAfterLeavingLibrary(
                trigger = LibraryExitTrigger.BACK,
                returnTarget = launcherSettings.appDrawer.afterLeavingLibrary,
            ) != null
