package com.riffle.core.domain.launcher.settings

import com.riffle.core.domain.launcher.icons.IconStyleSettings

/**
 * "Reset settings" (Settings > About): every setting back to its default, keeping what is the user's own data rather
 * than a setting. Pure.
 *
 * Kept: the feed list (the feeds the user added), the Floating dock's apps (a Dock), and the icons and names the user
 * chose for single apps (`IconStyleSettings.overrides`: the user's own changes to apps; the style they sit on, such as
 * the shape, does reset, and "Reset icon changes" in Settings > Appearance > Icons clears the changes). Everything
 * else in [LauncherSettings] returns to its default, including the Developer options state (hidden again), the hints
 * seen, Simple mode and the hide rules of notifications. Not part of [LauncherSettings] and so never touched: Home, the
 * Dock, saved Home layouts, hidden apps, the calendar feeds and the Tasks token. The Stream's sections (which are on,
 * their order, view and options) are stored apart from the settings and are reset with them by the Reset row
 * (`CachedStreamLayoutRepository.resetAll`), with Undo.
 */
object SettingsReset {
    fun reset(current: LauncherSettings): LauncherSettings =
        LauncherSettings(
            rss = RssSettings(feeds = current.rss.feeds),
            overlayDock = OverlayDockSettings(items = current.overlayDock.items),
            icons = IconStyleSettings(overrides = current.icons.overrides),
        )
}
