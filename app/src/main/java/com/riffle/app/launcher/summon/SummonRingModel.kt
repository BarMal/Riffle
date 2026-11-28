package com.riffle.app.launcher.summon

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.glance.GlanceWidgetKind
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.icons.IconPackDrawableRef
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.summon.SummonCollectionId
import com.riffle.core.domain.launcher.summon.SummonSettings
import com.riffle.core.domain.launcher.summon.SummonSlot
import com.riffle.core.domain.launcher.summon.SummonSlotRole

/** One slot of the ring as it draws: the stored reference resolved against what is installed and on Home now. */
internal sealed interface SummonEntry {
    val key: String
    val label: String
    val role: SummonSlotRole

    /** [notifications] is the app's group in the Stream when it has any: then resting on it shows its card. */
    data class App(
        val identity: AppIdentity,
        override val label: String,
        val notifications: AppNotificationGroup? = null,
    ) : SummonEntry {
        override val key: String
            get() = "app:${identity.packageName.value}/${identity.activityName.value}:${identity.profile.id.value}"
        override val role: SummonSlotRole
            get() = if (notifications?.notifications.isNullOrEmpty()) SummonSlotRole.APP else SummonSlotRole.PREVIEW
    }

    /** A collection (or a Home folder not yet copied into one): its own level of apps, glances and collections. */
    data class Folder(
        val id: String,
        override val label: String,
        val items: List<SummonEntry>,
        val icon: IconPackDrawableRef? = null,
    ) : SummonEntry {
        override val key: String get() = "folder:$id"
        override val role: SummonSlotRole get() = SummonSlotRole.FOLDER

        /** The apps directly inside, for its preview. */
        val apps: List<App> get() = items.filterIsInstance<App>()
    }

    /** Resting on it shows the glance itself as a card; released, it opens its section of the Stream. */
    data class Glance(
        val kind: GlanceWidgetKind,
        override val label: String,
    ) : SummonEntry {
        override val key: String get() = "glance:${kind.storedId}"
        override val role: SummonSlotRole get() = SummonSlotRole.PREVIEW
    }

    data class Favourites(
        override val label: String,
        val apps: List<App>,
    ) : SummonEntry {
        override val key: String get() = "favourites"
        override val role: SummonSlotRole get() = SummonSlotRole.FAVOURITES
    }
}

/**
 * The ring for one frame: the top level as the settings order it (only the first `slotCount`, and only what still
 * resolves) and each folder's apps. A folder shows at most `slotCount - 1` apps, since it keeps the top level's grid of
 * positions and leaves the one it was opened from empty.
 */
internal class SummonRingModel private constructor(
    private val root: List<SummonEntry>,
    private val folderCapacity: Int,
) {
    fun level(path: List<Int>): List<SummonEntry> =
        path.fold(root) { level, index ->
            (level.getOrNull(index) as? SummonEntry.Folder)?.items?.take(folderCapacity) ?: return emptyList()
        }

    fun roles(path: List<Int>): List<SummonSlotRole> = level(path).map { it.role }

    fun entry(
        path: List<Int>,
        index: Int,
    ): SummonEntry? = level(path).getOrNull(index)

    /** The Dock's apps when Favourites is on the ring, else none. */
    val favourites: List<SummonEntry.App>
        get() = root.filterIsInstance<SummonEntry.Favourites>().firstOrNull()?.apps.orEmpty()

    companion object {
        fun from(
            settings: SummonSettings,
            layout: HomeLayout,
            installed: List<InstalledApp>,
            glanceLabel: (GlanceWidgetKind) -> String,
            favouritesLabel: String,
            notifications: List<AppNotificationGroup> = emptyList(),
        ): SummonRingModel {
            val labels = installed.filter { it.enabled }.associate { it.identity to it.label }
            val groups = notifications.associateBy { it.packageName to it.profileId }

            fun appEntry(identity: AppIdentity): SummonEntry.App? =
                labels[identity]?.let { label ->
                    SummonEntry.App(identity, label, groups[identity.packageName to identity.profile.id])
                }

            fun appsOf(items: List<AppShortcutItem>): List<SummonEntry.App> =
                items.filter { it.appShortcutId == null }.mapNotNull { appEntry(it.appIdentity) }

            val folders =
                layout.pages
                    .flatMap { it.items }
                    .filterIsInstance<FolderItem>()
                    .associateBy { it.id }
            val collections = settings.collections.associateBy { it.id }

            // [inside]: the collections already open on the way here, so one that reaches itself stops.
            fun entryOf(
                slot: SummonSlot,
                inside: Set<SummonCollectionId>,
            ): SummonEntry? =
                when (slot) {
                    is SummonSlot.App -> appEntry(slot.identity)
                    is SummonSlot.Folder ->
                        folders[slot.folderId]?.let {
                            SummonEntry.Folder(
                                "home:${it.id.value}",
                                it.label,
                                appsOf(it.items),
                            )
                        }
                    is SummonSlot.Collection ->
                        collections[slot.id]?.takeIf { slot.id !in inside }?.let { collection ->
                            SummonEntry.Folder(
                                id = collection.id.value,
                                label = collection.name,
                                items = collection.items.mapNotNull { entryOf(it, inside + collection.id) },
                                icon = collection.icon,
                            )
                        }
                    is SummonSlot.Glance -> SummonEntry.Glance(slot.kind, glanceLabel(slot.kind))
                    SummonSlot.Favourites ->
                        SummonEntry.Favourites(
                            favouritesLabel,
                            appsOf(layout.dock.items.filterIsInstance<AppShortcutItem>()),
                        )
                }
            val root = settings.ringSlots.mapNotNull { entryOf(it, emptySet()) }
            return SummonRingModel(root, folderCapacity = (settings.slotCount - 1).coerceAtLeast(1))
        }
    }
}
