package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.InstalledApp

/**
 * Packages installed and not yet placed (S9, N9): a confirmed package-added event queues the package; once the
 * refreshed app list holds its apps ([arrived]) the shell places them where a workspace asks for it. An entry leaves
 * the queue only through [done], after the apps were placed or when nothing can ever place them on this home, so an
 * install that arrives before the workspaces have loaded is kept until they have. Main thread, like the shell.
 */
internal class PendingInstalls {
    private val pending = LinkedHashSet<Pair<AppPackageName, AppProfile>>()

    val isEmpty: Boolean get() = pending.isEmpty()

    fun add(
        packageName: AppPackageName,
        profile: AppProfile,
    ) {
        pending += packageName to profile
    }

    /** The apps of [apps] that belong to a queued package (same package and profile); the queue is unchanged. */
    fun arrived(apps: List<InstalledApp>): List<InstalledApp> =
        apps.filter { (it.identity.packageName to it.identity.profile) in pending }

    /** [apps] were placed (or can never be): their packages leave the queue. */
    fun done(apps: List<InstalledApp>) {
        pending.removeAll(apps.map { it.identity.packageName to it.identity.profile }.toSet())
    }
}
