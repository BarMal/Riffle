package com.riffle.core.domain.launcher.search

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutsByApp
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.library.LibraryHit
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.stream.NotificationContent
import com.riffle.core.domain.launcher.stream.PinnedApp
import com.riffle.core.domain.launcher.stream.QuickAction
import com.riffle.core.domain.launcher.stream.StreamCardSizes
import com.riffle.core.domain.launcher.stream.StreamGroup
import com.riffle.core.domain.launcher.stream.StreamShortcutPages

/**
 * What an app has live, for the line on its card in the search stack: how many notifications it has now and, when the
 * Stream's own (redacted) reading of them is at hand, the newest one's line. [newestLine] is null when only the count
 * is known; [hidden] is true when the Privacy content level withholds the text (the card then says so, not the text).
 */
data class SearchStackNotification(
    val count: Int,
    val newestLine: String? = null,
    val hidden: Boolean = false,
)

/**
 * One card of the search stack (the Dock's search): the best match is the front card,
 * the next matches are strips behind it in rank order, and "Search web" is the last card. [hasMore] says whether the
 * card has something to show beyond its name (quick actions, a notification), which decides what a tap on its strip
 * does ([SearchStackTap]) and whether the strip carries the quiet mark.
 */
sealed interface SearchStackCard {
    /** Stable across keystrokes, so the stack keys its slots by it. */
    val key: String

    val hasMore: Boolean

    /** An app: its quick actions (its launcher shortcuts, as its Stream page has them) and its newest notification. */
    data class App(
        val app: InstalledApp,
        val quickActions: List<QuickAction>,
        val notification: SearchStackNotification?,
    ) : SearchStackCard {
        override val key: String = "app:${app.identity.stackKey()}"
        override val hasMore: Boolean get() = quickActions.isNotEmpty() || notification != null
    }

    /** A launcher shortcut that matched on its own: a tap launches it. */
    data class Shortcut(val shortcut: AppShortcut) : SearchStackCard {
        override val key: String = "shortcut:${shortcut.appIdentity.stackKey()}:${shortcut.id.value}"
        override val hasMore: Boolean get() = false
    }

    /** A setting that matched: a tap opens its page. */
    data class Setting(val result: LauncherSearchResult.Setting) : SearchStackCard {
        override val key: String = "setting:${result.key}"
        override val hasMore: Boolean get() = false
    }

    /** "Search web" for [query]; with nothing matched it is the only card ([noResults]), with the empty wording. */
    data class Web(val query: String, val noResults: Boolean) : SearchStackCard {
        override val key: String = "web"
        override val hasMore: Boolean get() = false
    }
}

private fun AppIdentity.stackKey(): String = "${packageName.value}/${activityName.value}/${profile.id.value}"

/** What a tap on a strip does: the rule that makes the stack predictable. */
enum class StripTapOutcome {
    /** The strip has more to show: it comes to the front and shows it; the app is not opened. */
    BRING_TO_FRONT,

    /** Nothing more than a name: the result opens straight away. */
    OPEN,
}

object SearchStackTap {
    fun onStrip(card: SearchStackCard): StripTapOutcome =
        if (card.hasMore) StripTapOutcome.BRING_TO_FRONT else StripTapOutcome.OPEN
}

/**
 * The ranking of the search stack. Pure, so what is front, what is a strip, which cards have more and where the web
 * card stands are tested without a screen.
 */
object SearchStack {
    /** The most quick actions a card shows: the same cap as an app's Stream page. */
    const val MAX_QUICK_ACTIONS = StreamShortcutPages.MAX_QUICK_ACTIONS

    /**
     * The cards for [hits] (already in the search's rank order, the first being the best match). An app card carries
     * the app's enabled shortcuts in the platform's rank order (at most [MAX_QUICK_ACTIONS]) and its entry of
     * [notificationsByApp] (keyed by [StreamShortcutPages.appKeyOf]); a shortcut that already is one of its app's quick
     * actions is not a strip of its own. "Search web" ends the stack when [webSearch] (the query is not blank), and is
     * the only card when nothing matched. An empty [query] makes no cards.
     */
    fun plan(
        query: String,
        hits: List<LibraryHit>,
        shortcutsByApp: AppShortcutsByApp = emptyMap(),
        notificationsByApp: Map<String, SearchStackNotification> = emptyMap(),
        webSearch: Boolean = true,
    ): List<SearchStackCard> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val apps =
            hits.filterIsInstance<LibraryHit.App>().associate { hit ->
                hit.app.identity to quickActionsOf(hit.app, shortcutsByApp[hit.app.identity].orEmpty())
            }
        val cards =
            hits.mapNotNull { hit ->
                when (hit) {
                    is LibraryHit.App ->
                        SearchStackCard.App(
                            app = hit.app,
                            quickActions = apps.getValue(hit.app.identity),
                            notification =
                                notificationsByApp[StreamShortcutPages.appKeyOf(hit.app.identity)]
                                    ?.takeIf { it.count > 0 },
                        )
                    is LibraryHit.Shortcut ->
                        SearchStackCard.Shortcut(hit.shortcut).takeUnless {
                            apps[hit.shortcut.appIdentity]?.any { own -> own.id == hit.shortcut.id.value } == true
                        }
                    is LibraryHit.Setting -> SearchStackCard.Setting(hit.result)
                }
            }
        return if (webSearch) cards + SearchStackCard.Web(trimmed, noResults = cards.isEmpty()) else cards
    }

    /** The app's quick actions exactly as its Stream page plans them (enabled, platform rank, capped). */
    private fun quickActionsOf(
        app: InstalledApp,
        shortcuts: List<AppShortcut>,
    ): List<QuickAction> =
        if (shortcuts.isEmpty()) {
            emptyList()
        } else {
            StreamShortcutPages.plan(
                pinned = listOf(PinnedApp(app.identity, app.label)),
                liveGroupKeys = emptySet(),
                shortcutsByApp = mapOf(app.identity to shortcuts),
                launcherRole = true,
            ).flatMap { page -> page.quickActions }
        }
}

/**
 * The stack's geometry, which does not depend on the results: the front card has one height and the stack one height
 * for a posture, so a query changes what the cards say and never where they stand or how big the front card is. Only
 * the room (the keyboard's) can shorten it, and then it shows fewer strips, never a smaller front card.
 */
object SearchStackLayout {
    /** The front card's height folded (an app: header, a row of quick actions, a notification line). */
    const val FRONT_HEIGHT_DP = 152f

    fun frontHeightDp(sizes: StreamCardSizes): Float = FRONT_HEIGHT_DP * sizes.fixedHeightFactor

    /** The height the stack stands at with all its strips (a posture's), above the Dock's side of the field. */
    fun desiredHeightDp(
        sizes: StreamCardSizes,
        bottomInsetDp: Float,
    ): Float = StreamCardSizes.TOP_ROOM_DP + sizes.maxPeeks * sizes.pitchDp + frontHeightDp(sizes) + bottomInsetDp

    /** How many strips stand above the front card in [areaHeightDp]: all of the posture's when they fit, else fewer. */
    fun peeksFor(
        sizes: StreamCardSizes,
        areaHeightDp: Float,
        bottomInsetDp: Float,
    ): Int {
        val room = areaHeightDp - bottomInsetDp - frontHeightDp(sizes) - StreamCardSizes.TOP_ROOM_DP
        return if (room <= 0f) 0 else (room / sizes.pitchDp).toInt().coerceIn(0, sizes.maxPeeks)
    }
}

/**
 * The notification entry of each app for the search stack, keyed by [StreamShortcutPages.appKeyOf]. The count comes
 * from the launcher's own groups (it is what the app's badge counts); the newest notification's line comes from the
 * Stream's reading of the notifications section, which is already redacted by the Privacy content level, the hide
 * rules and the profile's quiet state, so the stack can say nothing the Stream would not. Without the Stream there is
 * the count alone.
 */
object SearchStackNotifications {
    fun from(
        groups: List<AppNotificationGroup>,
        streamGroups: List<StreamGroup> = emptyList(),
    ): Map<String, SearchStackNotification> {
        val counts = groups.associate { group -> "${group.packageName.value}:${group.profileId.value}" to group.count }
        val newest =
            streamGroups.associate { group ->
                group.key to group.items.filterIsInstance<NotificationContent>().maxByOrNull { it.postedAtEpochMillis }
            }
        return (counts.keys + newest.keys).associateWith { key ->
            val content = newest[key]
            SearchStackNotification(
                count = maxOf(counts[key] ?: 0, if (content == null) 0 else 1),
                newestLine = content?.takeUnless { it.hidden }?.let(::lineOf),
                hidden = content?.hidden == true,
            )
        }
    }

    private fun lineOf(content: NotificationContent): String? =
        listOfNotNull(content.headline, content.text?.takeIf { it.isNotBlank() })
            .joinToString(": ")
            .takeIf { it.isNotBlank() }
}
