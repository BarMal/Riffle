package com.riffle.core.domain.launcher.workspace.wizard

import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ContainerValidation
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.LensExpressionValidity
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.PageSetContainer
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.WidgetContainer
import com.riffle.core.domain.launcher.workspace.WidgetPlacement
import com.riffle.core.domain.launcher.workspace.WorkspaceSourceIds
import com.riffle.core.domain.launcher.workspace.editor.WidgetSlots
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContentGoalCatalogTest {
    private val descriptors = APP_DESCRIPTORS + SourceDescriptor(WorkspaceSourceIds.HOME_GRID)

    /** Every setup variant the pickers can produce for [goal]: none, one and several picks. */
    private fun variants(goal: ContentGoal): List<GoalSetup> {
        val base = ContentRecipes.defaultSetup(goal)
        return buildList {
            add(base)
            if (GoalPicker.APPS in goal.pickers) {
                add(base.copy(apps = listOf(app("a"))))
                add(base.copy(apps = listOf(app("a"), app("b"))))
            }
            if (GoalPicker.FEEDS in goal.pickers) {
                add(base.copy(feeds = listOf("feed-1")))
                add(base.copy(feeds = listOf("feed-1", "feed-2")))
            }
            if (GoalPicker.QUERY in goal.pickers) add(base.copy(query = "weather"))
        }
    }

    /** The container [destination] would hold [binding], validated as stored data would be. */
    private fun containerIssues(
        binding: LensBinding,
        destination: Destination,
    ): List<Any> =
        when (destination) {
            Destination.PAGE ->
                ContainerValidation.validate(
                    PageContainer(ContainerId("p"), PageContent.Bound(binding)),
                    descriptors,
                )
            Destination.PAGE_SET ->
                ContainerValidation.validate(
                    PageSetContainer(ContainerId("s"), binding),
                    descriptors,
                )
            Destination.WIDGET -> {
                val span = WidgetSlots.defaultSpan(binding.expression)
                val grid =
                    PageContent.WidgetGrid(
                        4,
                        6,
                        listOf(WidgetPlacement(WidgetContainer(ContainerId("w"), span, binding), 0, 0)),
                    )
                ContainerValidation.validate(PageContainer(ContainerId("g"), grid), descriptors)
            }
            Destination.DOCK_SECTION ->
                (LensExpressionValidity.check(binding.lens, binding.expression, descriptors).takeUnless { it.isValid })
                    ?.let(::listOf).orEmpty()
            Destination.LENS_ONLY -> {
                val whole = LensExpressionValidity.check(binding.lens, binding.expression, descriptors)
                val perGroup = LensExpressionValidity.checkPerGroup(binding.lens, binding.expression, descriptors)
                if (whole.isValid || perGroup.isValid) emptyList() else listOf(whole, perGroup)
            }
        }

    /** Every (goal, setup, look, destination) the catalog can produce. */
    private fun combinations(): List<Triple<GoalSetup, GoalLook, Destination>> =
        ContentGoalCatalog.all.flatMap { goal ->
            val setups = variants(goal).flatMap { variant -> goal.filters.map { variant.copy(filterId = it.id) } }
            val extra = if (goal.placedItems) emptyList() else listOf(Destination.LENS_ONLY)
            setups.flatMap { setup ->
                goal.looks.flatMap {
                        look ->
                    (look.placements + extra).map { Triple(setup.copy(lookId = look.id), look, it) }
                }
            }
        }

    @Test
    fun everyRecipeFilterLookAndDestinationIsValidUnderTheExpressionCatalog() {
        val all = combinations()
        all.forEach { (setup, look, destination) ->
            val where = "${setup.goal}/${setup.filterId}/${look.id}/$destination/${setup.apps.size}/${setup.feeds.size}"
            assertTrue(ContentRecipes.fits(setup, look, destination, descriptors), where)
            assertEquals(emptyList(), containerIssues(ContentRecipes.binding(setup), destination), where)
        }
        assertTrue(all.size > 100, "checked ${all.size} combinations")
    }

    @Test
    fun aDestinationALookDoesNotListIsNeverOffered() {
        ContentGoalCatalog.all.forEach { goal ->
            goal.looks.forEach { look ->
                val setup = ContentRecipes.defaultSetup(goal).copy(lookId = look.id)
                val unlisted = Destination.entries - look.placements.toSet() - Destination.LENS_ONLY
                unlisted.forEach {
                    assertTrue(
                        !ContentRecipes.fits(setup, look, it, descriptors),
                        "${goal.id}/${look.id}/$it",
                    )
                }
            }
        }
    }

    @Test
    fun everyBuiltInSourceHasARecipe() {
        (SourceIds.BUILT_IN + WorkspaceSourceIds.HOME_GRID).forEach { source ->
            assertTrue(ContentGoalCatalog.goalsFor(source).isNotEmpty(), "no goal reads $source")
        }
    }

    @Test
    fun goalsDeclareTheirPermissions() {
        assertEquals(listOf(SourcePermission.NOTIFICATION_ACCESS), ContentGoalCatalog.notifications.permissions)
        assertEquals(listOf(SourcePermission.NOTIFICATION_ACCESS), ContentGoalCatalog.media.permissions)
        assertEquals(listOf(SourcePermission.CALENDAR), ContentGoalCatalog.calendar.permissions)
        assertEquals(listOf(SourcePermission.USAGE_ACCESS), ContentGoalCatalog.recentApps.permissions)
        listOf(
            ContentGoalCatalog.myApps,
            ContentGoalCatalog.newsFeeds,
            ContentGoalCatalog.calendarFeeds,
            ContentGoalCatalog.search,
        )
            .forEach { assertEquals(emptyList(), it.permissions, it.id.name) }
    }

    @Test
    fun placedGoalsOnlyGoOnAPageAndAreNeverSavedAsLenses() {
        ContentGoalCatalog.all.filter { it.placedItems }.forEach { goal ->
            assertEquals(listOf(WorkspaceSourceIds.HOME_GRID), goal.sources)
            goal.looks.forEach { assertEquals(listOf(Destination.PAGE), it.placements) }
            val setup = ContentRecipes.defaultSetup(goal)
            assertTrue(!ContentRecipes.fits(setup, ContentRecipes.look(setup), Destination.LENS_ONLY))
        }
    }

    @Test
    fun keysLabelsAndOrderAreStable() {
        assertEquals(ContentGoalId.entries.toSet(), ContentGoalCatalog.all.map { it.id }.toSet())
        assertEquals(ContentGoalCatalog.all.size, ContentGoalCatalog.all.map { it.label }.toSet().size)
        ContentGoalCatalog.all.forEach {
            assertEquals(it.id, ContentGoalId.fromKey(it.id.key))
            assertTrue(it.description.length <= 90, "${it.id} description is long")
        }
        assertEquals(ContentGoalId.MY_APPS, ContentGoalCatalog.all.first().id)
    }
}
