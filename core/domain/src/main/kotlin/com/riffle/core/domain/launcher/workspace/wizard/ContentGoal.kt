package com.riffle.core.domain.launcher.workspace.wizard

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensFilter
import com.riffle.core.domain.launcher.workspace.LensGroup
import com.riffle.core.domain.launcher.workspace.LensSort
import com.riffle.core.domain.launcher.workspace.SourceId

/**
 * A user-facing content goal ("Notifications", "My apps"). [key] is stable (UI string keys, tests); goals are not
 * persisted, so a goal may be added or retired freely, but a key is never reused for a different goal.
 */
enum class ContentGoalId(val key: String) {
    MY_APPS("my_apps"),
    FAVOURITE_APPS("favourite_apps"),
    RECENT_APPS("recent_apps"),
    QUICK_ACTIONS("quick_actions"),
    NOTIFICATIONS("notifications"),
    MEDIA("media"),
    CALENDAR("calendar"),
    CALENDAR_FEEDS("calendar_feeds"),
    NEWS_FEEDS("news_feeds"),
    SEARCH("search"),
    HOME_ITEMS("home_items"),
    ;

    companion object {
        fun fromKey(key: String?): ContentGoalId? = entries.firstOrNull { it.key == key }
    }
}

/** Where a goal's content can go. [LENS_ONLY] saves the lens to the library without placing it anywhere. */
enum class Destination {
    PAGE,

    /** A page per group (notifications per app, a page per feed). */
    PAGE_SET,
    WIDGET,
    DOCK_SECTION,
    LENS_ONLY,
}

/** Extra input a goal's "what to show" step asks for. */
enum class GoalPicker {
    /** Which apps: the apps to place (placed goals) or "only these apps" (notifications). */
    APPS,

    /** Which feeds of an RSS or ICS source; none chosen means every feed. */
    FEEDS,

    /** The search text the lens keeps (per-lens query); empty follows the shared search box. */
    QUERY,
}

/** A "what to show" preset: a filter over the goal's items. [id] is unique within its goal. */
data class GoalFilter(
    val id: String,
    val label: String,
    val filter: LensFilter = LensFilter.All,
)

/**
 * A "how it looks" choice: an expression plus the lens shaping it needs (grouping, sort, limit). [placements] are the
 * destinations it suits, best first ([Destination.LENS_ONLY] is never listed: it is offered for every saveable look).
 * Null [sort] and [limit] keep the goal's own.
 */
data class GoalLook(
    val id: String,
    val label: String,
    val expression: ExpressionKind,
    val placements: List<Destination>,
    val group: LensGroup = LensGroup.None,
    val sort: LensSort? = null,
    val limit: Int? = null,
) {
    init {
        require(placements.isNotEmpty() && Destination.LENS_ONLY !in placements) { "A look needs a container." }
    }
}

/**
 * One recipe of the [ContentGoalCatalog]. [baseLens] reads [sources] with the goal's default sort; filters and looks
 * refine it ([ContentRecipes.lens]). The first of [filters] and of [looks] is the default.
 *
 * A [placedItems] goal is drawn from the layout's pool (`home.grid`): its apps are placed through the pool operations
 * rather than filtered, it only goes on a page, and it cannot be saved as a lens.
 */
data class ContentGoal(
    val id: ContentGoalId,
    val label: String,
    val description: String,
    val sources: List<SourceId>,
    val baseLens: Lens,
    val filters: List<GoalFilter>,
    val looks: List<GoalLook>,
    val pickers: Set<GoalPicker> = emptySet(),
    val placedItems: Boolean = false,
) {
    init {
        require(filters.isNotEmpty() && looks.isNotEmpty()) { "A goal needs a filter and a look." }
        require(filters.map { it.id }.toSet().size == filters.size) { "Filter ids must be unique." }
        require(looks.map { it.id }.toSet().size == looks.size) { "Look ids must be unique." }
    }

    val defaultFilter: GoalFilter get() = filters.first()

    val defaultLook: GoalLook get() = looks.first()

    /** The permissions this goal's sources need, in a stable order. */
    val permissions: List<SourcePermission>
        get() = sources.mapNotNull(SourcePermission::forSource).distinct().sortedBy { it.ordinal }

    fun filter(id: String?): GoalFilter? = filters.firstOrNull { it.id == id }

    fun look(id: String?): GoalLook? = looks.firstOrNull { it.id == id }
}

/** An app the user picked. Identity only, never content; [label] is for display and for a new pool item. */
data class AppPick(
    val identity: AppIdentity,
    val label: String,
) {
    /** The group key notifications use for this app (`package:profile`, see `NotificationItemMapper`). */
    val notificationGroupKey: String get() = "${identity.packageName.value}:${identity.profile.id.value}"
}

/** A feed the FEEDS picker offers: [key] is the feed's group key on items of [source]. */
data class FeedOption(
    val source: SourceId,
    val key: String,
    val label: String,
)
