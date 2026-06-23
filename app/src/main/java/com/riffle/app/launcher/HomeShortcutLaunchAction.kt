package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.home.AppShortcutItem

fun AppShortcutItem.launchAction(): LauncherShellAction =
    appShortcutId
        ?.let { shortcutId ->
            LauncherShellAction.LaunchAppShortcut(
                AppShortcut(
                    id = shortcutId,
                    appIdentity = appIdentity,
                    shortLabel = label,
                ),
            )
        }
        ?: LauncherShellAction.LaunchApp(appIdentity)
