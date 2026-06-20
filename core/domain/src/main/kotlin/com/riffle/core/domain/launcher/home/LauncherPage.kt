package com.riffle.core.domain.launcher.home

@JvmInline
value class LauncherPageId(val value: String)

data class LauncherPage(
    val id: LauncherPageId,
    val grid: GridDimensions,
    val items: List<LauncherItem> = emptyList(),
)
