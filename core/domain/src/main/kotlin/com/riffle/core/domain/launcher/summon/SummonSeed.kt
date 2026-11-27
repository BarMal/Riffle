package com.riffle.core.domain.launcher.summon

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.HomeLayout

/**
 * The apps a first Summon ring is built from: Home's apps page by page, then the Dock's. The Dock already has its own
 * place on the ring (Favourites), so its apps only fill what Home leaves short. Deep shortcuts and folders are left for
 * the user to add, and each app counts once.
 */
fun summonSeedApps(layout: HomeLayout): List<AppIdentity> {
    val dock = layout.dock.items.appIdentities()
    val home = layout.pages.flatMap { it.items }.appIdentities().filterNot { it in dock }
    return home + dock
}

private fun List<Any>.appIdentities(): List<AppIdentity> =
    filterIsInstance<AppShortcutItem>()
        .filter { it.appShortcutId == null }
        .map { it.appIdentity }
        .distinct()
