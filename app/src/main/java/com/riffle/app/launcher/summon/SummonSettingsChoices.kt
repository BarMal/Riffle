package com.riffle.app.launcher.summon

import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.glance.GlanceWidgetKind
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.summon.SummonCollectionId
import com.riffle.core.domain.launcher.summon.SummonHeader
import com.riffle.core.domain.launcher.summon.SummonSettings
import com.riffle.core.domain.launcher.summon.SummonSlot
import com.riffle.core.domain.launcher.summon.collection
import com.riffle.core.domain.launcher.summon.homeFolderCollectionId
import com.riffle.core.domain.launcher.summon.withCollectionItem

/**
 * What the "Add to the ring" picker offers, by group: only what is not on the ring already. Home folders are offered as
 * themselves; saving copies each into a collection, so one already copied onto the ring is not offered again.
 */
internal data class SummonAddChoices(
    val places: List<SummonSlot>,
    val glances: List<SummonSlot>,
    val apps: List<InstalledApp>,
)

internal fun summonAddChoices(
    settings: SummonSettings,
    layout: HomeLayout,
    installed: List<InstalledApp>,
): SummonAddChoices {
    val taken = settings.slots.toSet()
    val folders =
        layout.pages
            .flatMap { it.items }
            .filterIsInstance<FolderItem>()
            .filterNot { SummonSlot.Collection(homeFolderCollectionId(it.id)) in taken }
            .map { SummonSlot.Folder(it.id) }
    val collections = settings.collections.map { SummonSlot.Collection(it.id) }
    return SummonAddChoices(
        places = (listOf(SummonSlot.Favourites) + collections + folders).filterNot { it in taken },
        glances = GlanceWidgetKind.entries.map(SummonSlot::Glance).filterNot { it in taken },
        apps =
            installed
                .filter { it.enabled && SummonSlot.App(it.identity) !in taken }
                .sortedBy { it.label.lowercase() },
    )
}

/**
 * What can go into collection [id]: apps, glances and other collections it does not already hold, leaving out any
 * collection it sits inside (adding one of those would put it inside itself).
 */
internal fun summonCollectionAddChoices(
    settings: SummonSettings,
    id: SummonCollectionId,
    installed: List<InstalledApp>,
): SummonAddChoices {
    val held = settings.collection(id)?.items.orEmpty().toSet()
    val collections =
        settings.collections
            .map { SummonSlot.Collection(it.id) }
            .filter { it !in held && settings.withCollectionItem(id, it) != settings }
    return SummonAddChoices(
        places = collections,
        glances = GlanceWidgetKind.entries.map(SummonSlot::Glance).filterNot { it in held },
        apps =
            installed
                .filter { it.enabled && SummonSlot.App(it.identity) !in held }
                .sortedBy { it.label.lowercase() },
    )
}

/** One choice for the top of the screen; [widgetLabel] names a Home widget, which has no fixed string of its own. */
internal data class SummonHeaderChoice(
    val header: SummonHeader,
    val widgetLabel: String? = null,
)

internal fun summonHeaderChoices(layout: HomeLayout): List<SummonHeaderChoice> =
    listOf(SummonHeaderChoice(SummonHeader.ClockAndDate), SummonHeaderChoice(SummonHeader.Clock)) +
        GlanceWidgetKind.entries.map { SummonHeaderChoice(SummonHeader.Glance(it)) } +
        layout.pages
            .flatMap { it.items }
            .filterIsInstance<WidgetItem>()
            .filter { it.glance == null }
            .map { SummonHeaderChoice(SummonHeader.HomeWidget(it.id), widgetLabel = it.label) } +
        SummonHeaderChoice(SummonHeader.None)
