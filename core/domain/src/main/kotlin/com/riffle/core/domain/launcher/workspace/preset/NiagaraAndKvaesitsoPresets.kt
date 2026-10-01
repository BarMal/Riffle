package com.riffle.core.domain.launcher.workspace.preset

import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.homeGridLens

private const val NIAGARA = "niagara"
private const val KVAESITSO = "kvaesitso"

/**
 * Niagara: list-first and minimal. The user's placed home items drawn as a text List (the pinned list),
 * Recents as a List, and an AlphaList Finder. No widgets, no dynamic dock section.
 */
internal object NiagaraPreset {
    fun preset() =
        WorkspacePreset(
            id = NIAGARA,
            name = "Niagara",
            description = "A minimal list-first home: pinned apps, recents and an alphabetical finder.",
            compact = build(PresetPosture.COMPACT),
            expanded = build(PresetPosture.EXPANDED),
            skinHintId = "skin.niagara",
        )

    private fun build(posture: PresetPosture): Workspace {
        val ids = PresetIds(NIAGARA, posture)
        return workspace(
            ids,
            "Niagara",
            listOf(
                bound(ids, "pinned", homeGridLens(HOME_PAGE_ID), ExpressionKind.LIST),
                bound(ids, "recents", recentsList().lens, ExpressionKind.LIST),
                finderPage(ids, ExpressionKind.ALPHA_LIST),
            ),
        )
    }
}

/**
 * Kvaesitso-style: search-forward. A widgets page (next event, media, recents) and a searchable Finder
 * (All apps is the searchable source, drawn as an AlphaList). The search query and the search source
 * are not part of the lens model; see docs/product/workspaces-presets.md.
 */
internal object KvaesitsoPreset {
    fun preset() =
        WorkspacePreset(
            id = KVAESITSO,
            name = "Kvaesitso",
            description = "Widgets and a search-forward finder.",
            compact = build(PresetPosture.COMPACT),
            expanded = build(PresetPosture.EXPANDED),
            skinHintId = "skin.kvaesitso",
        )

    private fun build(posture: PresetPosture): Workspace {
        val ids = PresetIds(KVAESITSO, posture)
        return workspace(ids, "Kvaesitso", listOf(widgets(ids, posture), finderPage(ids, ExpressionKind.ALPHA_LIST)))
    }

    private fun widgets(
        ids: PresetIds,
        posture: PresetPosture,
    ) = when (posture) {
        PresetPosture.COMPACT ->
            widgetPage(
                ids,
                "today",
                4 to 6,
                listOf(
                    widget(ids, "event", 0 to 0, 4 to 2, nextEventCard()),
                    widget(ids, "media", 0 to 2, 4 to 2, mediaCard()),
                    widget(ids, "recents", 0 to 4, 4 to 2, recentsList()),
                ),
            )
        PresetPosture.EXPANDED ->
            widgetPage(
                ids,
                "today",
                8 to 4,
                listOf(
                    widget(ids, "event", 0 to 0, 4 to 2, nextEventCard()),
                    widget(ids, "media", 0 to 2, 4 to 2, mediaCard()),
                    widget(ids, "actions", 4 to 0, 4 to 1, quickActionsRow()),
                    widget(ids, "recents", 4 to 1, 4 to 3, recentsList()),
                ),
            )
    }
}
