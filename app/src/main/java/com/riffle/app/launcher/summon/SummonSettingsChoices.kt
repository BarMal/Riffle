package com.riffle.app.launcher.summon

import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.glance.GlanceWidgetKind
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.summon.SummonHeader
import com.riffle.core.domain.launcher.summon.SummonSettings
import com.riffle.core.domain.launcher.summon.SummonSlot

/** What the "Add to the ring" picker offers, by group: only what is not on the ring already. */
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
    val folders = layout.pages.flatMap { it.items }.filterIsInstance<FolderItem>().map { SummonSlot.Folder(it.id) }
    return SummonAddChoices(
        places = (listOf(SummonSlot.Favourites) + folders).filterNot { it in taken },
        glances = GlanceWidgetKind.entries.map(SummonSlot::Glance).filterNot { it in taken },
        apps =
            installed
                .filter { it.enabled && SummonSlot.App(it.identity) !in taken }
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
