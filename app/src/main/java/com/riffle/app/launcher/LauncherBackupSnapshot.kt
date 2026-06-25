package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.settings.LauncherSettings

fun launcherBackupDocument(
    storedLayoutSet: HomeLayoutSet?,
    activeLayout: HomeLayout,
    launcherSettings: LauncherSettings,
): LauncherBackupDocument =
    LauncherBackupDocument(
        homeLayoutSet =
            (storedLayoutSet ?: HomeLayoutSet.fromLayout(activeLayout))
                .withActiveLayout(activeLayout),
        launcherSettings = launcherSettings,
    )
