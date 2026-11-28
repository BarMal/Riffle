package com.riffle.core.domain.launcher.summon

import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.LauncherItemId

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
