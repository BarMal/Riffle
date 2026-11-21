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

/**
 * Whether the main activity, just created as the root of its task, should give way to a real Home start: it was started
 * from an intent that is not a Home intent (so the system typed its task as a standard task) while the app holds the
 * Home role. Cheapest checks first; [homeRoleHeld] (a platform call) is asked last.
 *
 * Never when an earlier hand-over was already made in this process ([alreadyHandedOver]), so a start that still is not
 * a Home start cannot loop.
 */
fun shouldHandOverToHome(
    isTaskRoot: Boolean,
    hasHomeCategory: Boolean,
    alreadyHandedOver: Boolean,
    homeRoleHeld: () -> Boolean,
): Boolean = isTaskRoot && !hasHomeCategory && !alreadyHandedOver && homeRoleHeld()
