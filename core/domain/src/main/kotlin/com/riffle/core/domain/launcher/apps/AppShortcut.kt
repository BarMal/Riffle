package com.riffle.core.domain.launcher.apps

data class AppShortcut(
    val id: AppShortcutId,
    val appIdentity: AppIdentity,
    val shortLabel: String,
    val longLabel: String? = null,
    val enabled: Boolean = true,
    val disabledMessage: String? = null,
)

@JvmInline
value class AppShortcutId(val value: String)

typealias AppShortcutsByApp = Map<AppIdentity, List<AppShortcut>>

fun interface AppShortcutRepository {
    fun shortcutsFor(apps: List<InstalledApp>): AppShortcutsByApp
}
