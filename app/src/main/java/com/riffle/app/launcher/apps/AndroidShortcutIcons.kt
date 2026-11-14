package com.riffle.app.launcher.apps

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import com.riffle.app.launcher.workspace.ItemImageKeyParser
import com.riffle.core.domain.launcher.workspace.ItemImageHandle

/**
 * The icon of one launcher shortcut (`ItemImageKeys.shortcutIcon`, `shortcut-icon:<profile>/<package>/<shortcut id>`),
 * read from the platform the way the launcher reads its shortcuts: only the Home app may, so without the role (or when
 * the shortcut has gone) this is null and the card draws the app's icon. Blocking: call off the main thread.
 */
internal class AndroidShortcutIcons(
    context: Context,
    private val currentUser: UserHandle = Process.myUserHandle(),
) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val densityDpi = context.resources.displayMetrics.densityDpi

    fun iconFor(handle: ItemImageHandle): Drawable? =
        runCatching {
            val (profileId, packageName, shortcutId) = ShortcutIconKey.parse(handle) ?: return@runCatching null
            val user =
                ItemImageKeyParser.profileFor(profileId).toUserHandle(
                    userProfiles = launcherApps.profiles,
                    currentUser = currentUser,
                    userManager = userManager,
                    launcherApps = launcherApps,
                )
            val query =
                LauncherApps.ShortcutQuery()
                    .setPackage(packageName)
                    .setShortcutIds(listOf(shortcutId))
                    .setQueryFlags(
                        LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                            LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                            LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
                    )
            launcherApps.getShortcuts(query, user)?.firstOrNull()
                ?.let { shortcut -> launcherApps.getShortcutIconDrawable(shortcut, densityDpi) }
        }.getOrNull()
}

/** Reads the key `ItemImageKeys.shortcutIcon` writes: the profile, the package and the shortcut id (the rest of it). */
internal object ShortcutIconKey {
    private const val PREFIX = "shortcut-icon:"

    fun parse(handle: ItemImageHandle): Triple<String, String, String>? {
        if (!handle.key.startsWith(PREFIX)) return null
        val parts = handle.key.removePrefix(PREFIX).split('/', limit = 3)
        return if (parts.size == 3 && parts.none { it.isEmpty() }) Triple(parts[0], parts[1], parts[2]) else null
    }

    fun isShortcutIcon(handle: ItemImageHandle): Boolean = handle.key.startsWith(PREFIX)
}
