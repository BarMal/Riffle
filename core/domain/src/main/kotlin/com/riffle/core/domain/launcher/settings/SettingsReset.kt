package com.riffle.core.domain.launcher.settings

/**
 * "Reset settings" (Settings > About): every setting back to its default, keeping what is the user's own data rather
 * than a setting. Pure.
 *
 * Kept: the feed list (the feeds the user added), and the Floating dock's apps (a Dock). Everything else in
 * [LauncherSettings] returns to its default, including the Developer options state (hidden again), the hints seen,
 * Simple mode and the hide rules of notifications. Not part of [LauncherSettings] and so never touched: Home, the
 * Dock, saved Home layouts, hidden apps, the calendar feeds, the Tasks token and the Stream's sections.
 */
object SettingsReset {
    fun reset(current: LauncherSettings): LauncherSettings =
        LauncherSettings(
            rss = RssSettings(feeds = current.rss.feeds),
            overlayDock = OverlayDockSettings(items = current.overlayDock.items),
        )
}
