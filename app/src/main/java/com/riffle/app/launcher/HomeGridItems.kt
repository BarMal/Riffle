package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherPage

fun LauncherPage.itemAt(cell: GridCell): LauncherItem? = items.firstOrNull { item -> item.placement?.cell == cell }

fun List<LauncherItem>.itemAt(cell: GridCell): LauncherItem? = firstOrNull { item -> item.placement?.cell == cell }
