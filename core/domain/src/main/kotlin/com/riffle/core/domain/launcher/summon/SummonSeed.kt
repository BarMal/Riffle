package com.riffle.core.domain.launcher.summon

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.HomeLayout

/**
 * The apps a first Summon ring is built from: the Dock's pinned apps, then Home's apps page by page. Deep shortcuts
 * and folders are left for the user to add, and each app counts once.
 */
fun summonSeedApps(layout: HomeLayout): List<AppIdentity> =
    (layout.dock.items + layout.pages.flatMap { it.items })
        .filterIsInstance<AppShortcutItem>()
        .filter { it.appShortcutId == null }
        .map { it.appIdentity }
        .distinct()
