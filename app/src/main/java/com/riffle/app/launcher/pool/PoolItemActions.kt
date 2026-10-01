package com.riffle.app.launcher.pool

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutId
import com.riffle.core.domain.launcher.home.AppShortcutItem

/**
 * What a tap on an app or shortcut in the preview's placed home page does: launch it through the existing
 * launchers (the same ones the standard home uses). Folders and widgets are handled by the page itself.
 */
internal class PoolItemActions(
    private val launchApp: (AppIdentity) -> Boolean,
    private val launchShortcut: (AppShortcut) -> Boolean,
) {
    fun open(item: AppShortcutItem): Boolean = open(item.appIdentity, item.appShortcutId, item.label)

    fun open(
        identity: AppIdentity,
        shortcutId: AppShortcutId?,
        label: String,
    ): Boolean =
        if (shortcutId == null) {
            launchApp(identity)
        } else {
            launchShortcut(AppShortcut(id = shortcutId, appIdentity = identity, shortLabel = label))
        }
}
