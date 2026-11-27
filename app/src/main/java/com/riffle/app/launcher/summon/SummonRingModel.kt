package com.riffle.app.launcher.summon

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.glance.GlanceWidgetKind
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.summon.SummonSettings
import com.riffle.core.domain.launcher.summon.SummonSlot
import com.riffle.core.domain.launcher.summon.SummonSlotRole

/** One slot of the ring as it draws: the stored reference resolved against what is installed and on Home now. */
internal sealed interface SummonEntry {
    val key: String
    val label: String
    val role: SummonSlotRole

    data class App(
        val identity: AppIdentity,
        override val label: String,
    ) : SummonEntry {
        override val key: String
            get() = "app:${identity.packageName.value}/${identity.activityName.value}:${identity.profile.id.value}"
        override val role: SummonSlotRole get() = SummonSlotRole.APP
    }

    data class Folder(
        val id: LauncherItemId,
        override val label: String,
        val apps: List<App>,
    ) : SummonEntry {
        override val key: String get() = "folder:${id.value}"
        override val role: SummonSlotRole get() = SummonSlotRole.FOLDER
    }

    /** Released, it opens its section of the Stream. */
    data class Glance(
        val kind: GlanceWidgetKind,
        override val label: String,
    ) : SummonEntry {
        override val key: String get() = "glance:${kind.storedId}"
        override val role: SummonSlotRole get() = SummonSlotRole.APP
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
        when {
            path.isEmpty() -> root
            path.size == 1 ->
                (
                    root.getOrNull(
                        path.first(),
                    ) as? SummonEntry.Folder
                )?.apps?.take(folderCapacity).orEmpty()
            else -> emptyList()
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
        ): SummonRingModel {
            val labels = installed.filter { it.enabled }.associate { it.identity to it.label }

            fun appEntry(identity: AppIdentity): SummonEntry.App? =
                labels[identity]?.let {
                    SummonEntry.App(
                        identity,
                        it,
                    )
                }

            fun appsOf(items: List<AppShortcutItem>): List<SummonEntry.App> =
                items.filter { it.appShortcutId == null }.mapNotNull { appEntry(it.appIdentity) }

            val folders =
                layout.pages
                    .flatMap { it.items }
                    .filterIsInstance<FolderItem>()
                    .associateBy { it.id }
            val root =
                settings.ringSlots.mapNotNull { slot ->
                    when (slot) {
                        is SummonSlot.App -> appEntry(slot.identity)
                        is SummonSlot.Folder ->
                            folders[slot.folderId]?.let { SummonEntry.Folder(it.id, it.label, appsOf(it.items)) }
                        is SummonSlot.Glance -> SummonEntry.Glance(slot.kind, glanceLabel(slot.kind))
                        SummonSlot.Favourites ->
                            SummonEntry.Favourites(
                                favouritesLabel,
                                appsOf(layout.dock.items.filterIsInstance<AppShortcutItem>()),
                            )
                    }
                }
            return SummonRingModel(root, folderCapacity = (settings.slotCount - 1).coerceAtLeast(1))
        }
    }
}
