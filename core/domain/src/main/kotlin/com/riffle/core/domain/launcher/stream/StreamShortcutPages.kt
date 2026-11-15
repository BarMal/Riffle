package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutsByApp
import com.riffle.core.domain.launcher.workspace.ItemImageHandle
import com.riffle.core.domain.launcher.workspace.sources.ItemImageKeys

/** An app pinned in the Dock: its identity and the name the Dock shows. */
data class PinnedApp(
    val identity: AppIdentity,
    val label: String,
)

/**
 * One of an app's launcher shortcuts as a quick action of its page (ADR 0003, Amendment 3, "Quick actions"): the
 * shortcut's icon and short label, which the Dock's row (or the row under the stack) shows as a chip and a tap
 * launches. It carries the typed [shortcut] content, so the one action gateway launches it, profile included. It is a
 * shortcut of a page, not a card of its stack and not a new kind of content; never persisted.
 */
data class QuickAction(val shortcut: AppShortcutContent) {
    /** The shortcut's id (never null: the app's own "open" card is not a quick action). */
    val id: String get() = shortcut.shortcutId.orEmpty()

    /** The chip's text. */
    val label: String get() = shortcut.shortLabel.orEmpty()

    val icon: ItemImageHandle? get() = shortcut.icon
}

/**
 * The Stream pages of the Dock's pinned apps (ADR 0003, Amendment 3): an app's page is its live cards (notifications,
 * the player) plus its quick actions, the launcher shortcuts the long-press menu offers.
 *
 * Every pinned app has one page. Its shortcuts are never cards of its stack: they are [StreamPage.quickActions]
 * (static,
 * dynamic and pinned, in the platform's rank order, the disabled ones left out, at most [MAX_QUICK_ACTIONS]). An app
 * with nothing live gets a page of [StreamSection.SHORTCUTS] holding one quiet card for the app (its icon, its name,
 * "Nothing new"; a tap opens the app) and its quick actions; with no shortcuts, or without the launcher role (shortcuts
 * can only be read by the Home app), the page is that card alone, and without the role a gap says why. A pinned app
 * that is live keeps its notifications page and gains the quick actions ([merge]). Pure; the pages are transient.
 */
object StreamShortcutPages {
    /** The most quick actions a page carries; the row scrolls sideways when they do not all fit. */
    const val MAX_QUICK_ACTIONS = 8

    /** The group key of an app's page: `package:profile`, the same key its notifications group has. */
    fun appKeyOf(identity: AppIdentity): String = "${identity.packageName.value}:${identity.profile.id.value}"

    /** The key of the shortcuts page of the app keyed [appKey]. */
    fun pageKeyOf(appKey: String): String = StreamPagePlanner.keyOf(StreamSection.SHORTCUTS, appKey)

    /**
     * The pages of [pinned] (one per distinct app, in the Dock's order) that are not [liveGroupKeys], from
     * [shortcutsByApp] (empty without the role). [launcherRole] is whether the Home role is held.
     */
    fun plan(
        pinned: List<PinnedApp>,
        liveGroupKeys: Set<String>,
        shortcutsByApp: AppShortcutsByApp,
        launcherRole: Boolean,
    ): List<StreamPage> =
        pinned
            .distinctBy { appKeyOf(it.identity) }
            .filter { appKeyOf(it.identity) !in liveGroupKeys }
            .map { app -> page(app, shortcutsByApp[app.identity].orEmpty(), launcherRole) }

    /**
     * One page per pinned app: [shortcutPages] (from [plan] with no live keys, so every pinned app has one) folded into
     * the [planned] pages. A pinned app that is live keeps its notifications page, its cards untouched, and gains the
     * quick actions (and the gap that says why there are none); a pinned app with nothing live keeps its page. The
     * result is the pages of apps with nothing live followed by [planned].
     */
    fun merge(
        planned: List<StreamPage>,
        shortcutPages: List<StreamPage>,
    ): List<StreamPage> {
        val byApp = shortcutPages.associateBy { page -> page.groupKey }
        val liveKeys = planned.filter { page -> page.ref().isLiveAppPage }.mapNotNull { page -> page.groupKey }.toSet()
        val merged =
            planned.map { page ->
                val own = if (page.ref().isLiveAppPage) byApp[page.groupKey] else null
                if (own == null) page else page.withQuickActionsOf(own)
            }
        return shortcutPages.filter { page -> page.groupKey !in liveKeys } + merged
    }

    private fun StreamPage.withQuickActionsOf(own: StreamPage): StreamPage {
        val live = body as? StreamPageBody.Content
        val gaps = (own.body as? StreamPageBody.Content)?.gaps.orEmpty()
        return copy(
            quickActions = own.quickActions,
            body = if (live == null || gaps.isEmpty()) body else live.copy(gaps = live.gaps + gaps),
        )
    }

    private fun page(
        app: PinnedApp,
        shortcuts: List<AppShortcut>,
        launcherRole: Boolean,
    ): StreamPage {
        val appKey = appKeyOf(app.identity)
        val ref = StreamAppRef(app.identity.packageName.value, app.identity.profile.id.value)
        val quiet = quietCard(app, appKey, ref)
        val quickActions =
            if (launcherRole) {
                shortcuts
                    .filter { shortcut -> shortcut.enabled }
                    .sortedBy { it.rank }
                    .take(MAX_QUICK_ACTIONS)
                    .map { shortcut -> QuickAction(shortcutContent(app, appKey, ref, shortcut)) }
            } else {
                emptyList()
            }
        val gaps =
            if (launcherRole) {
                emptyList()
            } else {
                listOf(
                    StreamGap(
                        StreamSourceIds.SHORTCUTS,
                        StreamSectionResult.PermissionRequired(listOf(StreamSourceIds.SHORTCUTS)),
                    ),
                )
            }
        return StreamPage(
            key = pageKeyOf(appKey),
            section = StreamSection.SHORTCUTS,
            groupKey = appKey,
            label = app.label,
            count = 1,
            view = StreamView.STACK,
            body = StreamPageBody.Content(listOf(StreamGroup(appKey, app.label, listOf(quiet))), gaps),
            lead = quiet,
            quickActions = quickActions,
        )
    }

    private fun shortcutContent(
        app: PinnedApp,
        appKey: String,
        ref: StreamAppRef,
        shortcut: AppShortcut,
    ) = AppShortcutContent(
        id = StreamContentId("shortcut:$appKey/${shortcut.id.value}"),
        app = ref,
        appIdentity = app.identity,
        appLabel = app.label,
        shortcutId = shortcut.id.value,
        shortLabel = shortcut.shortLabel,
        longLabel = shortcut.longLabel?.takeIf { it.isNotBlank() && it != shortcut.shortLabel },
        icon = ItemImageKeys.shortcutIcon(app.identity, shortcut.id.value),
        enabled = shortcut.enabled,
        disabledMessage = shortcut.disabledMessage,
    )

    /** The quiet card of an app with nothing live: its icon and name, a tap opens the app. */
    private fun quietCard(
        app: PinnedApp,
        appKey: String,
        ref: StreamAppRef,
    ) = AppShortcutContent(
        id = StreamContentId("shortcut:$appKey/"),
        app = ref,
        appIdentity = app.identity,
        appLabel = app.label,
        shortcutId = null,
        shortLabel = app.label,
        longLabel = null,
        icon = ItemImageKeys.appIcon(app.identity),
    )
}
