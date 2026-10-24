package com.riffle.core.domain.launcher.settings

import com.riffle.core.domain.launcher.ShellDestination

/**
 * Durable privacy preferences (Settings > Privacy, design section 6.2). Additive and off by default.
 *
 * [protectScreenContent]: "Hide launcher content in screenshots and recents". While on, the launcher window is
 * marked secure wherever it can show item content (see [protectsWindow]), so screenshots, screen recordings,
 * casting and the recents thumbnail show it blank. It also blocks the person's own screenshots of Home, which
 * is why it is opt-in.
 */
data class PrivacySettings(
    val protectScreenContent: Boolean = false,
)

/**
 * Whether the window must be secure at [destination]. Every launcher surface can show item content (home pages,
 * widgets, the dock section, notifications, search, the drawer) except Settings, which never shows items
 * (design section 6.3), so Settings stays capturable.
 */
fun PrivacySettings.protectsWindow(destination: ShellDestination): Boolean =
    protectScreenContent && destination != ShellDestination.SETTINGS
