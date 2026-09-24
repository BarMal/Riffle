package com.riffle.core.domain.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass

/**
 * The device class a mode switch applies to.
 *
 * A mode chosen in Settings applies to whichever device class Settings is configuring, which can be
 * another device's layout. Every other mode switch -- a home gesture, the dock, leaving Cards --
 * acts on what is on screen, so it targets the device class being held, wherever Settings was last
 * pointed.
 */
val LauncherShellState.modeSwitchTargetDeviceClass: HomeLayoutDeviceClass
    get() =
        when (destination) {
            ShellDestination.SETTINGS -> settingsLayoutDeviceClass
            else -> homeLayoutSet.activeKey.deviceClass
        }
