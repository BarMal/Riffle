package com.riffle.app.launcher.apps

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Process
import android.os.UserHandle
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutId
import com.riffle.core.domain.launcher.apps.AppShortcutRepository
import com.riffle.core.domain.launcher.apps.AppShortcutsByApp
import com.riffle.core.domain.launcher.apps.InstalledApp

internal class AndroidAppShortcutRepository(
    context: Context,
    private val userHandle: UserHandle = Process.myUserHandle(),
    private val mapper: AndroidAppShortcutMapper = AndroidAppShortcutMapper(),
) : AppShortcutRepository {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)

    override fun shortcutsFor(apps: List<InstalledApp>): AppShortcutsByApp =
        apps.associate { app -> app.identity to app.shortcuts() }
            .filterValues { shortcuts -> shortcuts.isNotEmpty() }

    private fun InstalledApp.shortcuts(): List<AppShortcut> =
        runCatching {
            launcherApps.getShortcuts(shortcutQuery, userHandle)
                .orEmpty()
                .map { shortcut -> mapper.map(identity = identity, shortcut = shortcut.toAndroidShortcut()) }
        }.getOrDefault(emptyList())

    private val InstalledApp.shortcutQuery: LauncherApps.ShortcutQuery
        get() =
            LauncherApps.ShortcutQuery()
                .setPackage(identity.packageName.value)
                .setActivity(
                    ComponentName(
                        identity.packageName.value,
                        identity.activityName.value,
                    ),
                )
                .setQueryFlags(SHORTCUT_QUERY_FLAGS)
}

internal class AndroidAppShortcutMapper {
    fun map(
        identity: AppIdentity,
        shortcut: AndroidAppShortcut,
    ): AppShortcut =
        AppShortcut(
            id = AppShortcutId(shortcut.id),
            appIdentity = identity,
            shortLabel = shortcut.shortLabel.ifBlank { shortcut.id },
            longLabel = shortcut.longLabel?.takeIf { label -> label.isNotBlank() },
            enabled = shortcut.enabled,
            disabledMessage = shortcut.disabledMessage?.takeIf { message -> message.isNotBlank() },
        )
}

internal data class AndroidAppShortcut(
    val id: String,
    val shortLabel: String,
    val longLabel: String?,
    val enabled: Boolean,
    val disabledMessage: String?,
)

private fun ShortcutInfo.toAndroidShortcut(): AndroidAppShortcut =
    AndroidAppShortcut(
        id = id,
        shortLabel = shortLabel?.toString().orEmpty(),
        longLabel = longLabel?.toString(),
        enabled = isEnabled,
        disabledMessage = disabledMessage?.toString(),
    )

private const val SHORTCUT_QUERY_FLAGS: Int =
    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
