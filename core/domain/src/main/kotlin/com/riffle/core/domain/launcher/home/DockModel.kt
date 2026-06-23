package com.riffle.core.domain.launcher.home

data class DockModel(
    val capacity: Int,
    val items: List<LauncherItem> = emptyList(),
    val isEnabled: Boolean = true,
    val iconSizeDp: Int = DEFAULT_DOCK_ICON_SIZE_DP,
) {
    val availableSlots: Int = capacity - items.size
}

const val DEFAULT_DOCK_ICON_SIZE_DP = 48
const val MIN_DOCK_ICON_SIZE_DP = 32
const val MAX_DOCK_ICON_SIZE_DP = 56
