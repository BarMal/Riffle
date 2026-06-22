package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.AppShortcutItem

fun AppShortcutItem.openAppInfoAction(): LauncherShellAction.OpenAppInfo = LauncherShellAction.OpenAppInfo(appIdentity)
