package com.riffle.app.launcher.sources

import com.riffle.core.domain.launcher.stream.PlayerSighting
import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.ItemExtValue
import com.riffle.core.domain.launcher.workspace.ItemTarget
import com.riffle.core.domain.launcher.workspace.sources.MediaItemExt
import com.riffle.core.domain.launcher.workspace.sources.MediaSessionInfo
import com.riffle.core.domain.launcher.workspace.sources.RecentPlayerItems

/**
 * The recent players that have no session, as quiet source cards after the player cards: what is playing is recorded,
 * an uninstalled app drops out, and an app whose session is active (even one with no notification to show) has no
 * source card. Nothing happens without a tracker.
 */
internal fun recentPlayerItems(
    media: MediaSourceDependencies,
    installedApps: InstalledAppSnapshotProvider,
    items: List<Item>,
    sessions: List<MediaSessionInfo>,
): List<Item> {
    val tracker = media.recentPlayers ?: return emptyList()
    val itemsByPackage = items.mapNotNull { item -> (item.target as? ItemTarget.App)?.let { item to it } }
    val sightings =
        itemsByPackage.map { (item, target) ->
            val playing = (item.ext[MediaItemExt.PLAYING] as? ItemExtValue.Flag)?.value == true
            PlayerSighting(target.packageName, target.profileId, playing, item.title, item.subtitle)
        } +
            sessions.filter { session -> itemsByPackage.none { (_, t) -> t.packageName == session.packageName } }
                .map { session -> PlayerSighting(session.packageName, null, playing = false) }
    val snapshot = installedApps.snapshot()
    val installed = snapshot?.apps?.map { app -> app.identity.packageName.value }?.toSet()
    val labels = snapshot?.apps.orEmpty().associate { app -> app.identity.packageName.value to app.label }
    return RecentPlayerItems.build(
        sources = tracker.observe(sightings, installed),
        appLabel = labels::get,
        canResume = media.resumer::canResume,
    )
}
