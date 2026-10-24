package com.riffle.core.domain.launcher.workspace.wizard

import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.LensFilter
import com.riffle.core.domain.launcher.workspace.LensGroup
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.WorkspaceSourceIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ContentRecipesTest {
    private val notifications = ContentRecipes.defaultSetup(ContentGoalCatalog.notifications)

    @Test
    fun theDefaultSetupIsTheFirstFilterAndLook() {
        assertEquals("all", notifications.filterId)
        assertEquals("by_app", notifications.lookId)
        val lens = ContentRecipes.lens(notifications)
        assertEquals(listOf(SourceIds.NOTIFICATIONS), lens.sources)
        assertEquals(LensGroup.ByGroupKey, lens.group)
        assertEquals(LensFilter.All, lens.filter)
    }

    @Test
    fun filtersAndPickersCombine() {
        val one = ContentRecipes.toggleApp(notifications, app("mail"))
        assertEquals(LensFilter.GroupKeyIs("mail:personal"), ContentRecipes.lens(one).filter)
        val two = ContentRecipes.withFilter(ContentRecipes.toggleApp(one, app("chat")), "work")
        val filter = assertIs<LensFilter.AllOf>(ContentRecipes.lens(two).filter)
        assertEquals(2, filter.filters.size)
        assertIs<LensFilter.ExtEquals>(filter.filters[0])
        assertEquals(
            LensFilter.AnyOf(listOf(LensFilter.GroupKeyIs("mail:personal"), LensFilter.GroupKeyIs("chat:personal"))),
            filter.filters[1],
        )
        // Toggling again removes it.
        assertEquals(notifications, ContentRecipes.toggleApp(one, app("mail")))
    }

    @Test
    fun aLookShapesTheLens() {
        val next = ContentRecipes.defaultSetup(ContentGoalCatalog.calendar)
        assertEquals(1, ContentRecipes.lens(next).limit)
        val byDay = next.copy(lookId = "by_day")
        assertEquals(LensGroup.ByDay, ContentRecipes.lens(byDay).group)
        assertEquals(null, ContentRecipes.lens(byDay).limit)
        assertEquals(ExpressionKind.INDEX, ContentRecipes.binding(byDay).expression)
    }

    @Test
    fun searchKeepsANormalizedQueryAndOnlySearchTakesOne() {
        val search = ContentRecipes.withQuery(ContentRecipes.defaultSetup(ContentGoalCatalog.search), "  weather  ")
        assertEquals("weather", search.query)
        assertEquals("weather", ContentRecipes.lens(search).parameters[SourceIds.SEARCH]?.text)
        assertEquals("", ContentRecipes.withQuery(search, "   ").query)
        assertEquals(notifications, ContentRecipes.withQuery(notifications, "x"))
    }

    @Test
    fun feedsAreTakenOnlyWhenOffered() {
        val rss = ContentRecipes.defaultSetup(ContentGoalCatalog.newsFeeds)
        val offered = listOf(FeedOption(SourceIds.RSS, "f1", "Feed"), FeedOption(SourceIds.ICS, "c1", "Cal"))
        assertEquals(listOf("f1"), ContentRecipes.toggleFeed(rss, "f1", offered).feeds)
        assertEquals(rss, ContentRecipes.toggleFeed(rss, "c1", offered))
        assertEquals(rss, ContentRecipes.toggleFeed(rss, "unknown", offered))
        assertEquals(
            LensFilter.GroupKeyIs("f1"),
            ContentRecipes.lens(ContentRecipes.toggleFeed(rss, "f1", offered)).filter,
        )
    }

    @Test
    fun unknownChoicesAreIgnored() {
        assertEquals(notifications, ContentRecipes.withFilter(notifications, "unread"))
        assertEquals(notifications, ContentRecipes.withLook(notifications, "nope"))
        // A page-set look cannot be a widget.
        assertEquals(
            "list",
            ContentRecipes.withLook(notifications.copy(lookId = "list"), "by_app", Destination.WIDGET).lookId,
        )
    }

    @Test
    fun reconcileFindsALookForTheDestination() {
        val widget = ContentRecipes.reconcile(notifications, Destination.WIDGET)
        assertEquals("list", widget.lookId)
        assertEquals(notifications, ContentRecipes.reconcile(notifications, Destination.PAGE_SET))
        assertEquals(listOf(Destination.PAGE_SET, Destination.LENS_ONLY), ContentRecipes.destinationsFor(notifications))
    }

    @Test
    fun placedGoalsReadTheHomeGridAndNeedAnApp() {
        val favourites = ContentRecipes.defaultSetup(ContentGoalCatalog.favouriteApps, listOf(app("a"), app("a")))
        assertEquals(1, favourites.apps.size)
        assertEquals(listOf(WorkspaceSourceIds.HOME_GRID), ContentRecipes.lens(favourites, pageKey = "p").sources)
        assertEquals(LensFilter.GroupKeyIs("p"), ContentRecipes.lens(favourites, pageKey = "p").filter)
        assertEquals(emptyList(), ContentRecipes.problems(favourites))
        assertEquals(listOf(SetupProblem.PICK_AN_APP), ContentRecipes.problems(favourites.copy(apps = emptyList())))
        assertTrue(ContentRecipes.defaultSetup(ContentGoalCatalog.myApps, listOf(app("a"))).apps.isEmpty())
    }
}
