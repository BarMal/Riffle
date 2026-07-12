package com.riffle.core.domain.launcher.home

@JvmInline
value class LauncherPageId(val value: String)

data class LauncherPage(
    val id: LauncherPageId,
    val type: LauncherPageType = LauncherPageType.Home,
    val grid: GridDimensions,
    val items: List<LauncherItem> = emptyList(),
    val isPinned: Boolean = false,
)
