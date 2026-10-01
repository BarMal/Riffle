package com.riffle.core.domain.launcher.workspace.preset

import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.LensGroup
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.notificationsLens

private const val TIMESCAPE = "timescape"

/**
 * TimeScape, per the worked example in docs/product/workspaces-sources-lenses.md.
 *
 * Compact (folded): Now widgets, an Inbox page-set (one CardStack per notifying app), Recents, with the
 * notifications as the dock's dynamic section. Expanded (unfolded): the container model has no
 * master/detail link, so the Index pane and the CardStack are two independent widgets side by side.
 * See docs/product/workspaces-presets.md for the gaps.
 */
internal object TimeScapePreset {
    fun preset() =
        WorkspacePreset(
            id = TIMESCAPE,
            name = "TimeScape",
            description = "Now widgets, a notification inbox of card stacks per app, and recents.",
            compact = compact(),
            expanded = expanded(),
            skinHintId = "skin.timescape",
        )

    private fun compact(): Workspace {
        val ids = PresetIds(TIMESCAPE, PresetPosture.COMPACT)
        return workspace(
            ids,
            "TimeScape",
            listOf(
                widgetPage(
                    ids,
                    "now",
                    4 to 5,
                    listOf(
                        widget(ids, "media", 0 to 0, 4 to 2, mediaCard()),
                        widget(ids, "event", 0 to 2, 4 to 2, nextEventCard()),
                        widget(ids, "actions", 0 to 4, 4 to 1, quickActionsRow()),
                    ),
                ),
                notificationsPageSet(ids),
                bound(ids, "recents", recentsList().lens, ExpressionKind.LIST),
                finderPage(ids, ExpressionKind.CATEGORIES),
            ),
            notificationDock(),
        )
    }

    private fun expanded(): Workspace {
        val ids = PresetIds(TIMESCAPE, PresetPosture.EXPANDED)
        return workspace(
            ids,
            "TimeScape",
            listOf(now(ids), inbox(ids), finderPage(ids, ExpressionKind.CATEGORIES)),
            notificationDock(),
        )
    }

    private fun now(ids: PresetIds) =
        widgetPage(
            ids,
            "now",
            8 to 6,
            listOf(
                widget(ids, "media", 0 to 0, 4 to 2, mediaCard()),
                widget(ids, "event", 4 to 0, 4 to 2, nextEventCard()),
                widget(ids, "actions", 0 to 2, 8 to 1, quickActionsRow()),
                widget(ids, "recents", 0 to 3, 8 to 3, recentsList()),
            ),
        )

    /** Notifications grouped by app as an Index (left), and all notifications as a CardStack (right). */
    private fun inbox(ids: PresetIds) =
        widgetPage(
            ids,
            "inbox",
            8 to 6,
            listOf(
                widget(
                    ids,
                    "index",
                    0 to 0,
                    3 to 6,
                    LensBinding(notificationsLens().copy(group = LensGroup.ByGroupKey), ExpressionKind.INDEX),
                ),
                widget(ids, "stack", 3 to 0, 5 to 6, LensBinding(notificationsLens(), ExpressionKind.CARD_STACK)),
            ),
        )
}
