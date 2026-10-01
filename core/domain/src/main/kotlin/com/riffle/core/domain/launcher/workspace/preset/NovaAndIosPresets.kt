package com.riffle.core.domain.launcher.workspace.preset

import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.Workspace

private const val NOVA = "nova"
private const val IOS = "ios"

/**
 * Nova-style, the default: the user's home grid page(s) plus a Finder (the app drawer) drawn as an
 * AlphaList, the Nova/Pixel convention (alphabetical list with a fast-scroll index and search). The
 * dock keeps its pinned items and has no dynamic section.
 */
internal object NovaPreset {
    fun preset() =
        WorkspacePreset(
            id = NOVA,
            name = "Nova",
            description = "Home screens plus an alphabetical app drawer. The familiar default.",
            compact = build(PresetPosture.COMPACT),
            expanded = build(PresetPosture.EXPANDED),
            skinHintId = "skin.nova",
        )

    private fun build(posture: PresetPosture): Workspace {
        val ids = PresetIds(NOVA, posture)
        return workspace(
            ids,
            "Nova",
            listOf(homePage(ids, ExpressionKind.ICON_GRID), finderPage(ids, ExpressionKind.ALPHA_LIST)),
        )
    }
}

/**
 * iOS-style: a Today page of widgets to the left of the home grid, and the App Library as the Finder
 * (All apps grouped by category, drawn as Categories). Recents show as an icon row on the Today page.
 */
internal object IosPreset {
    fun preset() =
        WorkspacePreset(
            id = IOS,
            name = "iOS",
            description = "Today widgets, home screens and a category-grouped App Library.",
            compact = build(PresetPosture.COMPACT),
            expanded = build(PresetPosture.EXPANDED),
            skinHintId = "skin.ios",
        )

    private fun build(posture: PresetPosture): Workspace {
        val ids = PresetIds(IOS, posture)
        return workspace(
            ids,
            "iOS",
            listOf(
                today(ids, posture),
                homePage(ids, ExpressionKind.ICON_GRID),
                finderPage(ids, ExpressionKind.CATEGORIES),
            ),
        )
    }

    private fun today(
        ids: PresetIds,
        posture: PresetPosture,
    ) = when (posture) {
        PresetPosture.COMPACT ->
            widgetPage(
                ids,
                "today",
                4 to 5,
                listOf(
                    widget(ids, "event", 0 to 0, 4 to 2, nextEventCard()),
                    widget(ids, "recents", 0 to 2, 4 to 1, recentsRow()),
                    widget(ids, "media", 0 to 3, 4 to 2, mediaCard()),
                ),
            )
        PresetPosture.EXPANDED ->
            widgetPage(
                ids,
                "today",
                8 to 4,
                listOf(
                    widget(ids, "event", 0 to 0, 4 to 2, nextEventCard()),
                    widget(ids, "media", 4 to 0, 4 to 2, mediaCard()),
                    widget(ids, "recents", 0 to 2, 8 to 1, recentsRow()),
                ),
            )
    }
}
