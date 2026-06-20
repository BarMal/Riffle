package com.riffle.core.domain.launcher.home

data class DockModel(
    val capacity: Int,
    val items: List<LauncherItem> = emptyList(),
) {
    val availableSlots: Int = capacity - items.size
}
