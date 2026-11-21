package com.riffle.core.domain.launcher.entry

/** Where the launcher icon's entry point sends the person. */
enum class LauncherEntryTarget {
    /** The app is the default Home: ask the system for Home (an implicit Home intent), so the task is the home task. */
    HOME,

    /** The app is not the default Home: open the app itself (preview), so it can still be set up. */
    OPEN_APP,
}

/**
 * The launcher icon, the installer's "Open" button and `getLaunchIntentForPackage` all start the entry activity. Held
 * Home role: Home, so the system creates a real home task. Otherwise: the app, as the icon always did.
 */
fun launcherEntryTarget(homeRoleHeld: Boolean): LauncherEntryTarget =
    if (homeRoleHeld) LauncherEntryTarget.HOME else LauncherEntryTarget.OPEN_APP
