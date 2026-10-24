package com.riffle.core.domain.launcher.workspace.wizard

import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.ItemExtValue
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensFilter
import com.riffle.core.domain.launcher.workspace.LensGroup
import com.riffle.core.domain.launcher.workspace.LensSort
import com.riffle.core.domain.launcher.workspace.LensSortField
import com.riffle.core.domain.launcher.workspace.SortDirection
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.WorkspaceSourceIds
import com.riffle.core.domain.launcher.workspace.homeGridLens

private const val DAY_MILLIS = 24L * 60 * 60 * 1000
private const val ROW_LIMIT = 8
private const val DOCK_ROW_LIMIT = 5
private const val RECENT_GRID_LIMIT = 12

private val NEWEST = LensSort(LensSortField.TIME, SortDirection.DESCENDING)
private val SOONEST = LensSort(LensSortField.TIME, SortDirection.ASCENDING)
private val A_TO_Z = LensSort(LensSortField.TITLE, SortDirection.ASCENDING)

/** The page key a placed goal's lens names before it is applied (the presets' home page id). */
internal const val PLACEHOLDER_PAGE_KEY = "home"

private val ALL = GoalFilter("all", "Everything")
private val LAST_DAY = GoalFilter("last_day", "Last 24 hours", LensFilter.AgeAtMost(DAY_MILLIS))

private fun profile(
    id: String,
    label: String,
    value: String,
) = GoalFilter(id, label, LensFilter.ExtEquals(WorkspaceSourceIds.APP_PROFILE_EXT, ItemExtValue.Text(value)))

private val PERSONAL = profile("personal", "Personal apps only", WorkspaceSourceIds.PROFILE_PERSONAL)
private val WORK = profile("work", "Work apps only", WorkspaceSourceIds.PROFILE_WORK)

private fun lens(
    source: SourceId,
    sort: LensSort = LensSort(),
    limit: Int? = null,
) = Lens(listOf(source), sort = sort, limit = limit)

/**
 * The goals the wizard and the first-time walkthrough offer, in display order. Plain data, framework-free. Every
 * goal, filter, look and listed destination is a valid pairing under `ExpressionCatalog` (see
 * `ContentGoalCatalogTest`),
 * and every built-in source has at least one goal (so "Use this source" always has a recipe).
 *
 * Honest limits, by design: "today only" and "unread" filters do not exist (the lens model has no future time window
 * and notifications carry no read state); "Favourite apps" are the apps the user picks and places, because nothing sets
 * the `launcher.pinned` flag yet (design 12).
 */
object ContentGoalCatalog {
    val myApps =
        ContentGoal(
            id = ContentGoalId.MY_APPS,
            label = "My apps",
            description = "Every app on this phone, as a grid, an A–Z list or by category.",
            sources = listOf(SourceIds.ALL_APPS),
            baseLens = lens(SourceIds.ALL_APPS, A_TO_Z),
            filters = listOf(GoalFilter("all", "All apps"), PERSONAL, WORK),
            looks =
                listOf(
                    GoalLook("grid", "Grid", ExpressionKind.ICON_GRID, listOf(Destination.PAGE, Destination.WIDGET)),
                    GoalLook(
                        "a_to_z",
                        "A–Z list",
                        ExpressionKind.ALPHA_LIST,
                        listOf(Destination.PAGE, Destination.WIDGET),
                    ),
                    GoalLook(
                        "categories",
                        "Categories",
                        ExpressionKind.CATEGORIES,
                        listOf(Destination.PAGE, Destination.WIDGET),
                        group = LensGroup.ByGroupKey,
                    ),
                ),
        )

    val favouriteApps =
        ContentGoal(
            id = ContentGoalId.FAVOURITE_APPS,
            label = "Favourite apps",
            description = "The apps you pick, as icons on your home screen.",
            sources = listOf(WorkspaceSourceIds.HOME_GRID),
            baseLens = homeGridLens(PLACEHOLDER_PAGE_KEY),
            filters = listOf(ALL),
            looks = listOf(GoalLook("icons", "Icons", ExpressionKind.ICON_GRID, listOf(Destination.PAGE))),
            pickers = setOf(GoalPicker.APPS),
            placedItems = true,
        )

    val recentApps =
        ContentGoal(
            id = ContentGoalId.RECENT_APPS,
            label = "Recent apps",
            description = "The apps you used last, newest first.",
            sources = listOf(SourceIds.RECENT_APPS),
            baseLens = lens(SourceIds.RECENT_APPS, NEWEST),
            filters = listOf(ALL),
            looks =
                listOf(
                    GoalLook(
                        "row",
                        "Row of icons",
                        ExpressionKind.ICON_ROW,
                        listOf(Destination.WIDGET, Destination.DOCK_SECTION, Destination.PAGE),
                        limit = ROW_LIMIT,
                    ),
                    GoalLook("list", "List", ExpressionKind.LIST, listOf(Destination.PAGE, Destination.WIDGET)),
                    GoalLook(
                        "grid",
                        "Grid",
                        ExpressionKind.ICON_GRID,
                        listOf(Destination.PAGE, Destination.WIDGET),
                        limit = RECENT_GRID_LIMIT,
                    ),
                ),
        )

    val quickActions =
        ContentGoal(
            id = ContentGoalId.QUICK_ACTIONS,
            label = "Quick actions",
            description = "Shortcuts into your apps, such as a new message or a route home.",
            sources = listOf(SourceIds.QUICK_ACTIONS),
            baseLens = lens(SourceIds.QUICK_ACTIONS),
            filters = listOf(ALL),
            looks =
                listOf(
                    GoalLook(
                        "row",
                        "Row of icons",
                        ExpressionKind.ICON_ROW,
                        listOf(Destination.WIDGET, Destination.DOCK_SECTION),
                        limit = ROW_LIMIT,
                    ),
                    GoalLook("list", "List", ExpressionKind.LIST, listOf(Destination.PAGE, Destination.WIDGET)),
                ),
        )

    val notifications =
        ContentGoal(
            id = ContentGoalId.NOTIFICATIONS,
            label = "Notifications",
            description = "What your apps tell you, by app, as a list or as cards.",
            sources = listOf(SourceIds.NOTIFICATIONS),
            baseLens = lens(SourceIds.NOTIFICATIONS, NEWEST),
            filters = listOf(ALL, LAST_DAY, PERSONAL, WORK),
            looks =
                listOf(
                    GoalLook(
                        "by_app",
                        "By app",
                        ExpressionKind.CARD_STACK,
                        listOf(Destination.PAGE_SET),
                        group = LensGroup.ByGroupKey,
                    ),
                    GoalLook("list", "List", ExpressionKind.LIST, listOf(Destination.PAGE, Destination.WIDGET)),
                    GoalLook("cards", "Cards", ExpressionKind.CARD_STACK, listOf(Destination.PAGE, Destination.WIDGET)),
                    GoalLook(
                        "grouped",
                        "Grouped list",
                        ExpressionKind.INDEX,
                        listOf(Destination.PAGE, Destination.WIDGET),
                        group = LensGroup.ByGroupKey,
                    ),
                    GoalLook(
                        "icons",
                        "App icons",
                        ExpressionKind.ICON_ROW,
                        listOf(Destination.DOCK_SECTION, Destination.WIDGET),
                        limit = DOCK_ROW_LIMIT,
                    ),
                ),
            pickers = setOf(GoalPicker.APPS),
        )

    val media =
        ContentGoal(
            id = ContentGoalId.MEDIA,
            label = "Media controls",
            description = "What is playing now, with play and pause.",
            sources = listOf(SourceIds.MEDIA),
            baseLens = lens(SourceIds.MEDIA, limit = 1),
            filters = listOf(ALL),
            looks = listOf(GoalLook("card", "Card", ExpressionKind.CARD, listOf(Destination.WIDGET, Destination.PAGE))),
        )

    val calendar =
        ContentGoal(
            id = ContentGoalId.CALENDAR,
            label = "Calendar",
            description = "Your next events, from the calendars on this phone.",
            sources = listOf(SourceIds.CALENDAR),
            baseLens = lens(SourceIds.CALENDAR, SOONEST),
            filters = listOf(ALL),
            looks =
                listOf(
                    GoalLook(
                        "next",
                        "Next event",
                        ExpressionKind.CARD,
                        listOf(Destination.WIDGET, Destination.PAGE),
                        limit = 1,
                    ),
                    GoalLook("list", "Upcoming", ExpressionKind.LIST, listOf(Destination.WIDGET, Destination.PAGE)),
                    GoalLook(
                        "by_day",
                        "By day",
                        ExpressionKind.INDEX,
                        listOf(Destination.PAGE, Destination.WIDGET),
                        group = LensGroup.ByDay,
                    ),
                ),
        )

    val calendarFeeds =
        ContentGoal(
            id = ContentGoalId.CALENDAR_FEEDS,
            label = "Calendar feeds",
            description = "Events from calendar links (ICS) you added in Settings.",
            sources = listOf(SourceIds.ICS),
            baseLens = lens(SourceIds.ICS, SOONEST),
            filters = listOf(ALL),
            looks =
                listOf(
                    GoalLook(
                        "by_day",
                        "By day",
                        ExpressionKind.INDEX,
                        listOf(Destination.PAGE, Destination.WIDGET),
                        group = LensGroup.ByDay,
                    ),
                    GoalLook("list", "Upcoming", ExpressionKind.LIST, listOf(Destination.PAGE, Destination.WIDGET)),
                    GoalLook("next", "Next event", ExpressionKind.CARD, listOf(Destination.WIDGET), limit = 1),
                    GoalLook(
                        "by_feed",
                        "A page per calendar",
                        ExpressionKind.LIST,
                        listOf(Destination.PAGE_SET),
                        group = LensGroup.ByGroupKey,
                    ),
                ),
            pickers = setOf(GoalPicker.FEEDS),
        )

    val newsFeeds =
        ContentGoal(
            id = ContentGoalId.NEWS_FEEDS,
            label = "News and feeds",
            description = "Stories from the RSS feeds you follow.",
            sources = listOf(SourceIds.RSS),
            baseLens = lens(SourceIds.RSS, NEWEST),
            filters = listOf(ALL, LAST_DAY),
            looks =
                listOf(
                    GoalLook("cards", "Cards", ExpressionKind.CARD_STACK, listOf(Destination.PAGE, Destination.WIDGET)),
                    GoalLook("list", "Headlines", ExpressionKind.LIST, listOf(Destination.PAGE, Destination.WIDGET)),
                    GoalLook(
                        "by_feed",
                        "A page per feed",
                        ExpressionKind.CARD_STACK,
                        listOf(Destination.PAGE_SET),
                        group = LensGroup.ByGroupKey,
                    ),
                    GoalLook("top", "Top story", ExpressionKind.CARD, listOf(Destination.WIDGET), limit = 1),
                ),
            pickers = setOf(GoalPicker.FEEDS),
        )

    val search =
        ContentGoal(
            id = ContentGoalId.SEARCH,
            label = "Search",
            description = "Results for a search you keep, or for what you type in the search box.",
            sources = listOf(SourceIds.SEARCH),
            baseLens = lens(SourceIds.SEARCH),
            filters = listOf(ALL),
            looks =
                listOf(
                    GoalLook("list", "List", ExpressionKind.LIST, listOf(Destination.PAGE, Destination.WIDGET)),
                    GoalLook("cards", "Cards", ExpressionKind.CARD_STACK, listOf(Destination.PAGE, Destination.WIDGET)),
                ),
            pickers = setOf(GoalPicker.QUERY),
        )

    val homeItems =
        ContentGoal(
            id = ContentGoalId.HOME_ITEMS,
            label = "Widgets from my home",
            description = "Keep your current home screen: its icons, folders and widgets.",
            sources = listOf(WorkspaceSourceIds.HOME_GRID),
            baseLens = homeGridLens(PLACEHOLDER_PAGE_KEY),
            filters = listOf(ALL),
            looks = listOf(GoalLook("home", "As it is", ExpressionKind.ICON_GRID, listOf(Destination.PAGE))),
            placedItems = true,
        )

    /** Every goal, in display order. */
    val all: List<ContentGoal> =
        listOf(
            myApps,
            favouriteApps,
            recentApps,
            notifications,
            media,
            calendar,
            calendarFeeds,
            newsFeeds,
            search,
            quickActions,
            homeItems,
        )

    fun goal(id: ContentGoalId): ContentGoal = all.first { it.id == id }

    /** The goals that read [source], in display order (the "Use this source" entry pre-selects these). */
    fun goalsFor(source: SourceId): List<ContentGoal> = all.filter { source in it.sources }
}
