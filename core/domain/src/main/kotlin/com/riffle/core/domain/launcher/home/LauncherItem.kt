package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppIdentity

sealed interface LauncherItem {
    val id: LauncherItemId
    val placement: GridPlacement?

    fun withPlacement(placement: GridPlacement): LauncherItem
}

@JvmInline
value class LauncherItemId(val value: String)

data class AppShortcutItem(
    override val id: LauncherItemId,
    val appIdentity: AppIdentity,
    val label: String,
    override val placement: GridPlacement? = null,
) : LauncherItem {
    override fun withPlacement(placement: GridPlacement): LauncherItem = copy(placement = placement)
}
