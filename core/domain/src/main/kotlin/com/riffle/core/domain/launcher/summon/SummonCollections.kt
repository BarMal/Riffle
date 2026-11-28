package com.riffle.core.domain.launcher.summon

import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.icons.IconPackDrawableRef

@JvmInline
value class SummonCollectionId(val value: String)

/** A folder of Summon's own: apps, glances and other collections, nested as deep as the user likes. */
data class SummonCollection(
    val id: SummonCollectionId,
    val name: String,
    val items: List<SummonSlot> = emptyList(),
    /** An icon-pack drawable to show for it; null shows a preview of its first items. */
    val icon: IconPackDrawableRef? = null,
)

fun SummonSettings.collection(id: SummonCollectionId): SummonCollection? = collections.firstOrNull { it.id == id }

/** These settings with an empty collection called [name]; unchanged when [id] is already taken. */
fun SummonSettings.withNewCollection(
    id: SummonCollectionId,
    name: String,
): SummonSettings = if (collection(id) != null) this else copy(collections = collections + SummonCollection(id, name))

fun SummonSettings.renamedCollection(
    id: SummonCollectionId,
    name: String,
): SummonSettings = updating(id) { it.copy(name = name) }

fun SummonSettings.withCollectionIcon(
    id: SummonCollectionId,
    icon: IconPackDrawableRef?,
): SummonSettings = updating(id) { it.copy(icon = icon) }

/**
 * [slot] added to the end of collection [id], once. Favourites stays on the ring, and a collection is never put where
 * it would end up inside itself.
 */
fun SummonSettings.withCollectionItem(
    id: SummonCollectionId,
    slot: SummonSlot,
): SummonSettings {
    val target = collection(id) ?: return this
    val refused =
        slot == SummonSlot.Favourites ||
            slot in target.items ||
            (slot is SummonSlot.Collection && (slot.id == id || reaches(slot.id, id)))
    return if (refused) this else updating(id) { it.copy(items = it.items + slot) }
}

fun SummonSettings.withoutCollectionItem(
    id: SummonCollectionId,
    index: Int,
): SummonSettings = updating(id) { c -> c.copy(items = c.items.filterIndexed { i, _ -> i != index }) }

fun SummonSettings.movedCollectionItem(
    id: SummonCollectionId,
    from: Int,
    to: Int,
): SummonSettings =
    updating(id) { c ->
        if (from !in c.items.indices || to !in c.items.indices) {
            c
        } else {
            c.copy(items = c.items.toMutableList().apply { add(to, removeAt(from)) })
        }
    }

/** Collection [id] deleted, and taken off the ring and out of every other collection. */
fun SummonSettings.withoutCollection(id: SummonCollectionId): SummonSettings {
    val gone = SummonSlot.Collection(id)
    return copy(
        slots = slots - gone,
        collections = collections.filter { it.id != id }.map { it.copy(items = it.items - gone) },
    )
}

/** The id a Home folder's copy takes, so copying the same folder twice finds the first copy. */
fun homeFolderCollectionId(folderId: LauncherItemId): SummonCollectionId = SummonCollectionId("home-${folderId.value}")

/**
 * Each Home folder on the ring replaced by a copy of it as a collection (its apps, without deep shortcuts); folders
 * that no longer exist are dropped. Collections are Summon's own, so later changes to the Home folder do not follow.
 */
fun SummonSettings.withHomeFoldersCopied(layout: HomeLayout): SummonSettings {
    if (slots.none { it is SummonSlot.Folder }) return this
    val folders = layout.pages.flatMap { it.items }.filterIsInstance<FolderItem>().associateBy { it.id }
    var result = this
    val newSlots =
        slots.mapNotNull { slot ->
            if (slot !is SummonSlot.Folder) return@mapNotNull slot
            val folder = folders[slot.folderId] ?: return@mapNotNull null
            val id = homeFolderCollectionId(folder.id)
            if (result.collection(id) == null) {
                result = result.copy(collections = result.collections + folder.asCollection(id))
            }
            SummonSlot.Collection(id)
        }
    return result.copy(slots = newSlots.distinct())
}

/** A copy of a Home folder, ready to add to the ring as a collection. */
fun FolderItem.asCollection(id: SummonCollectionId = homeFolderCollectionId(this.id)): SummonCollection =
    SummonCollection(
        id = id,
        name = label,
        items =
            items
                .filter { it.appShortcutId == null }
                .map(AppShortcutItem::appIdentity)
                .distinct()
                .map(SummonSlot::App),
    )

/** Whether collection [from] holds [to], however deep. */
private fun SummonSettings.reaches(
    from: SummonCollectionId,
    to: SummonCollectionId,
    seen: Set<SummonCollectionId> = emptySet(),
): Boolean {
    if (from in seen) return false
    val inner = collection(from)?.items.orEmpty().filterIsInstance<SummonSlot.Collection>().map { it.id }
    return to in inner || inner.any { reaches(it, to, seen + from) }
}

private fun SummonSettings.updating(
    id: SummonCollectionId,
    change: (SummonCollection) -> SummonCollection,
): SummonSettings = copy(collections = collections.map { if (it.id == id) change(it) else it })
