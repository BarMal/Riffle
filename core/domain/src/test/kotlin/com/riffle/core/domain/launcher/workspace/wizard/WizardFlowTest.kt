package com.riffle.core.domain.launcher.workspace.wizard

import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.LibraryProblem
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceDock
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.editor.ContainerKind
import com.riffle.core.domain.launcher.workspace.homeGridLens
import com.riffle.core.domain.launcher.workspace.sources.SourceAccess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal val MAIN_ID = WorkspaceId("main")
internal val HOME_PAGE =
    PageContainer(ContainerId("home"), PageContent.Bound(LensBinding(homeGridLens("home"), ExpressionKind.ICON_GRID)))
internal val GRID_PAGE = PageContainer(ContainerId("grid"), PageContent.WidgetGrid(4, 6, emptyList()))

/** A workspace that shows the borrowed home and has an empty widget page. */
internal fun mainLayout(): LayoutWorkspaces =
    LayoutWorkspaces.single(
        Workspace(MAIN_ID, "Main", listOf(HOME_PAGE, GRID_PAGE)),
    )

internal fun wizardContext(
    entry: WizardEntry = WizardEntry.Editor(MAIN_ID),
    layout: LayoutWorkspaces = mainLayout(),
    access: Map<com.riffle.core.domain.launcher.workspace.SourceId, SourceAccess> = emptyMap(),
    sources: List<com.riffle.core.domain.launcher.workspace.editor.SourceChoice> = sourceChoices(access),
) = WizardContext(entry, sources, layout, feeds = listOf(FeedOption(SourceIds.RSS, "f1", "News")))

internal fun WizardContext.run(
    vararg actions: WizardAction,
    from: WizardState = WizardFlow.start(this),
): WizardState = actions.fold(from) { state, action -> WizardReducer.reduce(state, action, this) }

class WizardFlowTest {
    @Test
    fun theEditorEntryWalksEveryStepToDone() {
        val ctx = wizardContext(WizardEntry.Editor(MAIN_ID, pageId = ContainerId("grid")))
        val start = WizardFlow.start(ctx)
        assertEquals(WizardStep.SOURCE, start.step)
        assertTrue(start.saveToLibrary, "editor entries save by default")
        val content = ctx.run(WizardAction.ChooseGoal(ContentGoalId.NOTIFICATIONS), WizardAction.Next)
        assertEquals(WizardStep.CONTENT, content.step)
        val look = ctx.run(WizardAction.ChooseFilter("last_day"), WizardAction.Next, from = content)
        assertEquals(WizardStep.LOOK, look.step)
        val place = ctx.run(WizardAction.ChooseLook("list"), WizardAction.Next, from = look)
        assertEquals(WizardStep.PLACE, place.step)
        // The editor asked for its widget page, so the Place step starts on a widget there.
        assertEquals(Destination.WIDGET, place.destination)
        assertEquals(ContainerId("grid"), place.widgetTarget?.pageId)
        val save = ctx.run(WizardAction.Next, from = place)
        assertEquals(WizardStep.SAVE, save.step)
        assertEquals("Notifications, list", WizardFlow.name(save, ctx))
        val done = ctx.run(WizardAction.Next, from = save)
        assertEquals(WizardStep.DONE, done.step)
        assertEquals(WizardExit.Finished, done.exit)
        assertEquals(
            listOf(
                WizardStep.SOURCE,
                WizardStep.CONTENT,
                WizardStep.LOOK,
                WizardStep.PLACE,
                WizardStep.SAVE,
                WizardStep.DONE,
            ),
            WizardFlow.steps(done, ctx),
        )
    }

    @Test
    fun backGoesOneStepAndCancelsOnTheFirst() {
        val ctx = wizardContext()
        val atSave =
            ctx.run(
                WizardAction.ChooseGoal(ContentGoalId.NOTIFICATIONS),
                WizardAction.Next,
                WizardAction.Next,
                WizardAction.Next,
                WizardAction.Next,
            )
        assertEquals(WizardStep.SAVE, atSave.step)
        val steps = listOf(WizardStep.PLACE, WizardStep.LOOK, WizardStep.CONTENT, WizardStep.SOURCE)
        var state = atSave
        steps.forEach { expected ->
            state = ctx.run(WizardAction.Back, from = state)
            assertEquals(expected, state.step)
            assertNull(state.exit)
        }
        // The choices made so far survive going back.
        assertEquals(ContentGoalId.NOTIFICATIONS, state.setup?.goal)
        assertEquals(WizardExit.Cancelled, ctx.run(WizardAction.Back, from = state).exit)
    }

    @Test
    fun cancelWorksOnEveryStepAndClosesTheWizard() {
        val ctx = wizardContext()
        var state = ctx.run(WizardAction.ChooseGoal(ContentGoalId.NOTIFICATIONS))
        while (state.step != WizardStep.DONE) {
            val cancelled = ctx.run(WizardAction.Cancel, from = state)
            assertEquals(WizardExit.Cancelled, cancelled.exit, "at ${state.step}")
            // A closed wizard ignores everything.
            assertEquals(
                cancelled,
                ctx.run(WizardAction.Next, WizardAction.ChooseGoal(ContentGoalId.MY_APPS), from = cancelled),
            )
            state = ctx.run(WizardAction.Next, from = state)
        }
    }

    @Test
    fun useThisSourcePreselectsAndSkipsTheSourceStep() {
        val ctx = wizardContext(WizardEntry.FromSource(SourceIds.RSS, MAIN_ID))
        val start = WizardFlow.start(ctx)
        assertEquals(ContentGoalId.NEWS_FEEDS, start.setup?.goal)
        assertEquals(WizardStep.CONTENT, start.step)
        assertFalse(WizardStep.SOURCE in WizardFlow.steps(start, ctx))
        assertFalse(start.saveToLibrary, "saving is offered, off by default, from a source page")
        assertEquals(WizardExit.Cancelled, ctx.run(WizardAction.Back, from = start).exit)
        // A source whose goal has nothing to choose starts on Look.
        val media = wizardContext(WizardEntry.FromSource(SourceIds.MEDIA, MAIN_ID))
        assertEquals(WizardStep.LOOK, WizardFlow.start(media).step)
        // Its goals are listed first.
        assertEquals(ContentGoalId.NEWS_FEEDS, WizardFlow.goalChoices(ctx).first().goal.id)
    }

    @Test
    fun useThisSourceForAnUnavailableSourceStartsOnTheSourceStep() {
        val ctx =
            wizardContext(
                WizardEntry.FromSource(SourceIds.CALENDAR, MAIN_ID),
                access = mapOf(SourceIds.CALENDAR to SourceAccess.UNAVAILABLE),
            )
        val start = WizardFlow.start(ctx)
        assertEquals(WizardStep.SOURCE, start.step)
        assertNull(start.setup)
        assertNotNull(WizardFlow.handoff(start, ctx), "the advanced builder can still take the source")
    }

    @Test
    fun newSavedLensOnlySavesAndRequiresAName() {
        val ctx = wizardContext(WizardEntry.NewSavedLens)
        val start = WizardFlow.start(ctx)
        assertEquals(Destination.LENS_ONLY, start.destination)
        val choices = WizardFlow.goalChoices(ctx).associateBy { it.goal.id }
        assertEquals(GoalAvailability.UNAVAILABLE, choices.getValue(ContentGoalId.FAVOURITE_APPS).availability)
        assertEquals(GoalAvailability.UNAVAILABLE, choices.getValue(ContentGoalId.HOME_ITEMS).availability)
        val state =
            ctx.run(
                WizardAction.ChooseGoal(ContentGoalId.SEARCH),
                WizardAction.Next,
                WizardAction.SetQuery("news"),
                WizardAction.Next,
            )
        assertEquals(WizardStep.LOOK, state.step)
        assertFalse(WizardStep.PLACE in WizardFlow.steps(state, ctx))
        val save = ctx.run(WizardAction.Next, WizardAction.SetSaveToLibrary(false), from = state)
        assertEquals(WizardStep.SAVE, save.step)
        assertTrue(WizardFlow.saves(save), "a lens-only result is always saved")
        val blank = ctx.run(WizardAction.SetName(" "), WizardAction.Next, from = save)
        assertEquals(WizardStep.SAVE, blank.step)
        assertEquals(listOf(WizardProblem.Name(LibraryProblem.BLANK_NAME)), WizardFlow.problems(blank, ctx))
        assertEquals(
            WizardExit.Finished,
            ctx.run(WizardAction.SetName("Morning news"), WizardAction.Next, from = blank).exit,
        )
    }

    @Test
    fun aTakenNameIsRefused() {
        val layout =
            mainLayout().let {
                    l ->
                l.copy(
                    library =
                        l.library.tryAdd("Search, list", Lens(listOf(SourceIds.SEARCH))).let {
                            (it as com.riffle.core.domain.launcher.workspace.LibraryAdd.Added).library
                        },
                )
            }
        val ctx = wizardContext(WizardEntry.NewSavedLens, layout)
        val save =
            ctx.run(
                WizardAction.ChooseGoal(ContentGoalId.SEARCH),
                WizardAction.Next,
                WizardAction.Next,
                WizardAction.Next,
            )
        assertEquals("Search, list 2", WizardFlow.name(save, ctx), "the suggestion is unique")
        val taken = ctx.run(WizardAction.SetName("search, LIST"), WizardAction.Next, from = save)
        assertEquals(listOf(WizardProblem.Name(LibraryProblem.NAME_TAKEN)), WizardFlow.problems(taken, ctx))
        assertNull(taken.exit)
    }

    @Test
    fun invalidCombinationsAreRejected() {
        val ctx = wizardContext()
        // No goal: Next stays.
        assertEquals(WizardStep.SOURCE, ctx.run(WizardAction.Next).step)
        assertEquals(listOf(WizardProblem.NoGoal), WizardFlow.problems(WizardFlow.start(ctx), ctx))
        val chosen = ctx.run(WizardAction.ChooseGoal(ContentGoalId.NOTIFICATIONS))
        // Another step's choice is ignored.
        assertEquals(chosen, ctx.run(WizardAction.ChooseLook("list"), WizardAction.ChooseFilter("work"), from = chosen))
        val look = ctx.run(WizardAction.Next, WizardAction.Next, from = chosen)
        assertEquals(WizardStep.LOOK, look.step)
        assertEquals(look, ctx.run(WizardAction.ChooseLook("unknown"), from = look))
        val place = ctx.run(WizardAction.Next, from = look)
        assertEquals(Destination.PAGE_SET, place.destination)
        // "By app" only goes on a page per app: a widget or the dock is refused.
        assertEquals(place, ctx.run(WizardAction.ChooseDestination(Destination.WIDGET), from = place))
        assertEquals(place, ctx.run(WizardAction.ChooseDestination(Destination.DOCK_SECTION), from = place))
        val choices = WizardFlow.destinationChoices(place, ctx).map { it.destination }
        assertEquals(listOf(Destination.PAGE_SET, Destination.LENS_ONLY), choices)
    }

    @Test
    fun aGoalWhoseSourceIsMissingCannotBeChosenButOneNeedingAccessCan() {
        val ctx =
            wizardContext(
                access =
                    mapOf(
                        SourceIds.CALENDAR to SourceAccess.UNAVAILABLE,
                        SourceIds.NOTIFICATIONS to SourceAccess.REQUIRED,
                    ),
            )
        val choices = WizardFlow.goalChoices(ctx).associateBy { it.goal.id }
        assertEquals(GoalAvailability.UNAVAILABLE, choices.getValue(ContentGoalId.CALENDAR).availability)
        assertEquals(GoalAvailability.NEEDS_ACCESS, choices.getValue(ContentGoalId.NOTIFICATIONS).availability)
        assertEquals(listOf(SourcePermission.NOTIFICATION_ACCESS), choices.getValue(ContentGoalId.NOTIFICATIONS).needs)
        assertNull(ctx.run(WizardAction.ChooseGoal(ContentGoalId.CALENDAR)).setup)
        assertEquals(
            ContentGoalId.NOTIFICATIONS,
            ctx.run(WizardAction.ChooseGoal(ContentGoalId.NOTIFICATIONS)).setup?.goal,
        )
        // A source the registry does not list at all.
        val noIcs = wizardContext(sources = sourceChoices().filter { it.id != SourceIds.ICS })
        assertEquals(
            GoalAvailability.UNAVAILABLE,
            WizardFlow.goalChoice(ContentGoalCatalog.calendarFeeds, noIcs).availability,
        )
    }

    @Test
    fun aWorkspaceThatShowsItsHomeAlreadyHasIt() {
        val ctx = wizardContext()
        assertEquals(
            GoalAvailability.ALREADY_THERE,
            WizardFlow.goalChoice(ContentGoalCatalog.homeItems, ctx).availability,
        )
        val bare = LayoutWorkspaces.single(Workspace(MAIN_ID, "Main", listOf(GRID_PAGE)))
        assertEquals(
            GoalAvailability.READY,
            WizardFlow.goalChoice(ContentGoalCatalog.homeItems, wizardContext(layout = bare)).availability,
        )
    }

    @Test
    fun favouriteAppsNeedAnAppAndSkipTheSaveStep() {
        val ctx = wizardContext()
        val content = ctx.run(WizardAction.ChooseGoal(ContentGoalId.FAVOURITE_APPS), WizardAction.Next)
        assertEquals(WizardStep.CONTENT, content.step)
        assertEquals(listOf(WizardProblem.Setup(SetupProblem.PICK_AN_APP)), WizardFlow.problems(content, ctx))
        assertEquals(content, ctx.run(WizardAction.Next, from = content))
        val picked = ctx.run(WizardAction.ToggleApp(app("maps")), WizardAction.Next, WizardAction.Next, from = content)
        assertEquals(WizardStep.PLACE, picked.step)
        assertEquals(listOf(Destination.PAGE), WizardFlow.destinationChoices(picked, ctx).map { it.destination })
        assertFalse(WizardStep.SAVE in WizardFlow.steps(picked, ctx))
        assertFalse(WizardFlow.saves(picked))
        assertEquals(WizardExit.Finished, ctx.run(WizardAction.Next, from = picked).exit)
        assertTrue(WizardFlow.preview(picked)!!.placed)
    }

    @Test
    fun aContentChangeKeepsTheLookValid() {
        val ctx = wizardContext()
        val look =
            ctx.run(
                WizardAction.ChooseGoal(ContentGoalId.NEWS_FEEDS),
                WizardAction.Next,
                WizardAction.Next,
                WizardAction.ChooseLook("by_feed"),
            )
        assertEquals("by_feed", look.setup?.lookId)
        val back =
            ctx.run(
                WizardAction.Back,
                WizardAction.ToggleFeed("f1"),
                WizardAction.ToggleFeed("nope"),
                from = look,
            )
        assertEquals(listOf("f1"), back.setup?.feeds)
        assertEquals("by_feed", back.setup?.lookId)
        assertTrue(WizardFlow.lookChoices(back, ctx).all { it.enabled })
    }

    @Test
    fun theDockSectionSaysItReplacesTheCurrentOne() {
        val withDock =
            LayoutWorkspaces.single(
                Workspace(
                    MAIN_ID,
                    "Main",
                    listOf(GRID_PAGE),
                    WorkspaceDock(LensBinding(Lens(listOf(SourceIds.RECENT_APPS)), ExpressionKind.ICON_ROW)),
                ),
            )
        val ctx = wizardContext(layout = withDock)
        val place = ctx.run(WizardAction.ChooseGoal(ContentGoalId.RECENT_APPS), WizardAction.Next, WizardAction.Next)
        assertEquals(WizardStep.PLACE, place.step)
        val dock = WizardFlow.destinationChoices(place, ctx).first { it.destination == Destination.DOCK_SECTION }
        assertTrue(dock.enabled && dock.replaces)
    }

    @Test
    fun theAdvancedBuilderTakesTheDraft() {
        val ctx = wizardContext()
        assertEquals(WizardFlow.start(ctx), ctx.run(WizardAction.OpenAdvanced), "nothing to hand over yet")
        val place =
            ctx.run(
                WizardAction.ChooseGoal(ContentGoalId.NOTIFICATIONS),
                WizardAction.Next,
                WizardAction.Next,
                WizardAction.ChooseLook("cards"),
                WizardAction.Next,
            )
        val advanced = assertIs<WizardExit.Advanced>(ctx.run(WizardAction.OpenAdvanced, from = place).exit)
        assertEquals(ContentRecipes.lens(place.setup!!), advanced.handoff.draft.toLens())
        assertEquals(ExpressionKind.CARD_STACK, advanced.handoff.expression)
        assertEquals(ContainerKind.PAGE, advanced.handoff.container)
    }

    @Test
    fun thePreviewDrawsThePageSetPerGroup() {
        val ctx = wizardContext()
        val state = ctx.run(WizardAction.ChooseGoal(ContentGoalId.NOTIFICATIONS))
        val preview = assertNotNull(WizardFlow.preview(state))
        assertTrue(preview.perGroup)
        assertEquals(ExpressionKind.CARD_STACK, preview.binding.expression)
        assertNull(WizardFlow.preview(WizardFlow.start(ctx)))
    }
}
