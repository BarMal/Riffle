package com.riffle.core.domain.launcher.workspace.wizard

import com.riffle.core.domain.launcher.settings.LauncherGestureAction
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.sources.SourceAccess
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal fun walkthroughContext(
    origin: OnboardingOrigin = OnboardingOrigin.FRESH_INSTALL,
    access: Map<SourceId, SourceAccess> = emptyMap(),
    gestures: Collection<LauncherGestureAction> = emptyList(),
    firstRun: Boolean = true,
) = WalkthroughContext(
    origin = origin,
    sources = sourceChoices(access),
    layout = if (origin == OnboardingOrigin.FRESH_INSTALL) freshLayout() else existingLayout(),
    pool = if (origin == OnboardingOrigin.FRESH_INSTALL) freshPool() else existingPool(),
    suggestedApps = listOf(app("phone"), app("mail")),
    suggestedDock = listOf(app("phone"), app("camera")),
    globalGestureActions = gestures,
    firstRun = firstRun,
)

internal fun WalkthroughContext.run(
    vararg actions: WalkthroughAction,
    from: WalkthroughState = WalkthroughFlow.start(this),
): WalkthroughState = actions.fold(from) { state, action -> WalkthroughReducer.reduce(state, action, this) }

/** From the start to the Review step, accepting every default. */
internal fun WalkthroughContext.toReview(from: WalkthroughState = WalkthroughFlow.start(this)): WalkthroughState {
    var state = from
    repeat(30) { if (state.step != WalkthroughStep.REVIEW) state = run(WalkthroughAction.Next, from = state) }
    assertEquals(WalkthroughStep.REVIEW, state.step)
    return state
}

class WalkthroughFlowTest {
    @Test
    fun aFreshInstallPreselectsAHomeOfItsOwn() {
        val ctx = walkthroughContext()
        val start = WalkthroughFlow.start(ctx)
        assertEquals(WalkthroughStep.WELCOME, start.step)
        assertEquals(
            listOf(
                ContentGoalId.FAVOURITE_APPS,
                ContentGoalId.CALENDAR,
                ContentGoalId.MY_APPS,
                ContentGoalId.NOTIFICATIONS,
            ),
            start.chosen,
            "placed first, then widgets, then pages",
        )
        assertEquals(GoalRole.WIDGET, start.roleOf(ContentGoalId.CALENDAR))
        assertEquals(listOf(app("phone"), app("mail")), start.setups.getValue(ContentGoalId.FAVOURITE_APPS).apps)
        assertEquals(listOf(app("phone"), app("camera")), start.dockPins)
    }

    @Test
    fun anExistingUserKeepsTheirHomeAndTheirDock() {
        val ctx = walkthroughContext(OnboardingOrigin.EXISTING_USER)
        val start = WalkthroughFlow.start(ctx)
        assertEquals(listOf(ContentGoalId.HOME_ITEMS, ContentGoalId.MY_APPS, ContentGoalId.NOTIFICATIONS), start.chosen)
        assertEquals(emptyList(), start.dockPins)
    }

    @Test
    fun unavailableGoalsAreNeitherPreselectedNorChoosable() {
        val ctx =
            walkthroughContext(
                access =
                    mapOf(
                        com.riffle.core.domain.launcher.workspace.SourceIds.CALENDAR to SourceAccess.UNAVAILABLE,
                    ),
            )
        val start = WalkthroughFlow.start(ctx)
        assertFalse(ContentGoalId.CALENDAR in start.chosen)
        val goals = ctx.run(WalkthroughAction.Next, from = start)
        assertEquals(goals, ctx.run(WalkthroughAction.ToggleGoal(ContentGoalId.CALENDAR), from = goals))
    }

    @Test
    fun goalsFollowTheProposalUntilArrangedByHand() {
        val ctx = walkthroughContext()
        val goals = ctx.run(WalkthroughAction.Next)
        val withNews =
            ctx.run(
                WalkthroughAction.ToggleGoal(ContentGoalId.NEWS_FEEDS),
                WalkthroughAction.ToggleGoal(ContentGoalId.HOME_ITEMS),
                from = goals,
            )
        assertEquals(
            listOf(
                ContentGoalId.FAVOURITE_APPS,
                ContentGoalId.HOME_ITEMS,
                ContentGoalId.CALENDAR,
                ContentGoalId.MY_APPS,
                ContentGoalId.NOTIFICATIONS,
                ContentGoalId.NEWS_FEEDS,
            ),
            withNews.chosen,
        )
        val none = ctx.run(*withNews.chosen.map { WalkthroughAction.ToggleGoal(it) }.toTypedArray(), from = withNews)
        assertEquals(emptyList(), none.chosen)
        assertEquals(listOf(WalkthroughProblem.NoGoals), WalkthroughFlow.problems(none, ctx))
        assertEquals(WalkthroughStep.GOALS, ctx.run(WalkthroughAction.Next, from = none).step)
    }

    @Test
    fun arrangeChangesRolesOrderAndStartPageWithPlacedPagesFirst() {
        val ctx = walkthroughContext()
        val arrange = ctx.run(WalkthroughAction.Next, WalkthroughAction.Next)
        assertEquals(WalkthroughStep.ARRANGE, arrange.step)
        // Notifications by app is a page-set; as a widget it takes a look that fits one.
        val widget = ctx.run(WalkthroughAction.SetRole(ContentGoalId.NOTIFICATIONS, GoalRole.WIDGET), from = arrange)
        assertEquals(GoalRole.WIDGET, widget.roleOf(ContentGoalId.NOTIFICATIONS))
        assertEquals("list", widget.setups.getValue(ContentGoalId.NOTIFICATIONS).lookId)
        assertTrue(widget.arranged)
        // Favourite apps are placed icons: only a page.
        assertEquals(
            widget,
            ctx.run(WalkthroughAction.SetRole(ContentGoalId.FAVOURITE_APPS, GoalRole.WIDGET), from = widget),
        )
        // Nothing moves above the placed page, and the placed page cannot move down.
        val moved =
            ctx.run(
                WalkthroughAction.MoveGoal(ContentGoalId.MY_APPS, 0),
                WalkthroughAction.MoveGoal(ContentGoalId.FAVOURITE_APPS, 9),
                from = widget,
            )
        assertEquals(ContentGoalId.FAVOURITE_APPS, moved.chosen.first())
        assertEquals(ContentGoalId.MY_APPS, moved.chosen[1])
        val start =
            ctx.run(
                WalkthroughAction.SetStartGoal(ContentGoalId.MY_APPS),
                WalkthroughAction.SetStartGoal(ContentGoalId.SEARCH),
                from = moved,
            )
        assertEquals(ContentGoalId.MY_APPS, start.startGoal, "a goal that is not chosen cannot be the start page")
        // Choices of another step are ignored here.
        assertEquals(
            start,
            ctx.run(
                WalkthroughAction.ToggleGoal(ContentGoalId.SEARCH),
                WalkthroughAction.ToggleDockPin(app("x")),
                from = start,
            ),
        )
    }

    @Test
    fun optionsWalkThroughTheGoalsThatHaveSomethingToChoose() {
        val ctx = walkthroughContext()
        val options = ctx.run(WalkthroughAction.Next, WalkthroughAction.Next, WalkthroughAction.Next)
        assertEquals(WalkthroughStep.OPTIONS, options.step)
        val goals = WalkthroughFlow.optionGoals(options, ctx)
        assertEquals(
            listOf(
                ContentGoalId.FAVOURITE_APPS,
                ContentGoalId.CALENDAR,
                ContentGoalId.MY_APPS,
                ContentGoalId.NOTIFICATIONS,
            ),
            goals,
        )
        assertEquals(ContentGoalId.FAVOURITE_APPS, WalkthroughFlow.currentOptionGoal(options, ctx))
        // Unpicking every favourite blocks Next with a reason.
        val empty =
            ctx.run(
                WalkthroughAction.ToggleApp(ContentGoalId.FAVOURITE_APPS, app("phone")),
                WalkthroughAction.ToggleApp(ContentGoalId.FAVOURITE_APPS, app("mail")),
                from = options,
            )
        assertEquals(
            listOf(WalkthroughProblem.Setup(ContentGoalId.FAVOURITE_APPS, SetupProblem.PICK_AN_APP)),
            WalkthroughFlow.problems(empty, ctx),
        )
        assertEquals(empty, ctx.run(WalkthroughAction.Next, from = empty))
        val calendar = ctx.run(WalkthroughAction.Next, from = options)
        assertEquals(ContentGoalId.CALENDAR, WalkthroughFlow.currentOptionGoal(calendar, ctx))
        // A widget can only take the looks that fit a widget.
        assertEquals(
            listOf(
                "next",
                "list",
                "by_day",
            ),
            WalkthroughFlow.looksFor(calendar, ContentGoalId.CALENDAR, ctx).map {
                it.id
            },
        )
        val byDay = ctx.run(WalkthroughAction.SetLook(ContentGoalId.CALENDAR, "by_day"), from = calendar)
        assertEquals("by_day", byDay.setups.getValue(ContentGoalId.CALENDAR).lookId)
        val notifications = ctx.run(WalkthroughAction.Next, WalkthroughAction.Next, from = byDay)
        assertEquals(ContentGoalId.NOTIFICATIONS, WalkthroughFlow.currentOptionGoal(notifications, ctx))
        assertEquals(
            notifications,
            ctx.run(WalkthroughAction.SetFilter(ContentGoalId.NOTIFICATIONS, "unread"), from = notifications),
        )
        val work = ctx.run(WalkthroughAction.SetFilter(ContentGoalId.NOTIFICATIONS, "work"), from = notifications)
        assertEquals("work", work.setups.getValue(ContentGoalId.NOTIFICATIONS).filterId)
        // Back walks the goals backwards, then leaves the step.
        val back = ctx.run(WalkthroughAction.Back, WalkthroughAction.Back, WalkthroughAction.Back, from = work)
        assertEquals(0, back.optionsIndex)
        assertEquals(WalkthroughStep.ARRANGE, ctx.run(WalkthroughAction.Back, from = back).step)
        val dock = ctx.run(WalkthroughAction.Next, from = work)
        assertEquals(WalkthroughStep.DOCK, dock.step)
        assertEquals(
            ContentGoalId.NOTIFICATIONS,
            WalkthroughFlow.currentOptionGoal(ctx.run(WalkthroughAction.Back, from = dock), ctx),
        )
    }

    @Test
    fun theDockHidesOnlyWhileAGestureOpensTheMenu() {
        val ctx = walkthroughContext()
        val dock =
            ctx.run(
                WalkthroughAction.Next,
                WalkthroughAction.Next,
                WalkthroughAction.Next,
                WalkthroughAction.Next,
                WalkthroughAction.Next,
                WalkthroughAction.Next,
                WalkthroughAction.Next,
            )
        assertEquals(WalkthroughStep.DOCK, dock.step)
        assertFalse(WalkthroughFlow.canHideDock(ctx))
        assertFalse(ctx.run(WalkthroughAction.SetDockHidden(true), from = dock).dockHidden)
        val withMenu = walkthroughContext(gestures = listOf(LauncherGestureAction.OPEN_WORKSPACE_MENU))
        assertTrue(withMenu.run(WalkthroughAction.SetDockHidden(true), from = dock).dockHidden)
        // Pins: toggle, and never past the dock's capacity.
        val pins =
            ctx.run(
                WalkthroughAction.ToggleDockPin(app("camera")),
                WalkthroughAction.ToggleDockPin(app("a")),
                WalkthroughAction.ToggleDockPin(app("b")),
                WalkthroughAction.ToggleDockPin(app("c")),
                WalkthroughAction.ToggleDockPin(app("d")),
                WalkthroughAction.ToggleDockPin(app("e")),
                from = dock,
            )
        assertEquals(listOf("phone", "a", "b", "c", "d"), pins.dockPins.map { it.label })
    }

    @Test
    fun permissionsAreExplainedBeforeEachRequestAndSkippable() {
        val ctx = walkthroughContext(access = NOTHING_GRANTED)
        var state = WalkthroughFlow.start(ctx)
        while (state.step != WalkthroughStep.PERMISSIONS) state = ctx.run(WalkthroughAction.Next, from = state)
        assertEquals(
            listOf(SourcePermission.NOTIFICATION_ACCESS, SourcePermission.CALENDAR),
            WalkthroughFlow.neededPermissions(state, ctx),
        )
        // No system dialog without the explanation first.
        assertEquals(state, ctx.run(WalkthroughAction.RequestPermission(SourcePermission.CALENDAR), from = state))
        val explained = ctx.run(WalkthroughAction.ExplainPermission(SourcePermission.CALENDAR), from = state)
        assertEquals(
            PermissionStatus.EXPLAINING,
            WalkthroughFlow.permissionStatus(explained, SourcePermission.CALENDAR, ctx),
        )
        val requested = ctx.run(WalkthroughAction.RequestPermission(SourcePermission.CALENDAR), from = explained)
        assertEquals(SourcePermission.CALENDAR, requested.pendingRequest)
        // While the system flow is open, nothing else is asked and the step does not move.
        assertEquals(
            requested,
            ctx.run(
                WalkthroughAction.ExplainPermission(SourcePermission.NOTIFICATION_ACCESS),
                WalkthroughAction.Next,
                WalkthroughAction.Back,
                from = requested,
            ),
        )
        val denied =
            ctx.run(
                WalkthroughAction.PermissionResult(SourcePermission.CALENDAR, granted = false),
                from = requested,
            )
        assertNull(denied.pendingRequest)
        assertEquals(PermissionStatus.DENIED, WalkthroughFlow.permissionStatus(denied, SourcePermission.CALENDAR, ctx))
        // A permission this walkthrough does not need is ignored.
        assertEquals(denied, ctx.run(WalkthroughAction.ExplainPermission(SourcePermission.USAGE_ACCESS), from = denied))
        val review = ctx.run(WalkthroughAction.Next, from = denied)
        assertEquals(WalkthroughStep.REVIEW, review.step)
        assertEquals(
            PermissionStatus.SKIPPED,
            review.permissions[SourcePermission.NOTIFICATION_ACCESS],
            "leaving the step skips the rest",
        )
        val summary = WalkthroughFlow.review(review, ctx)
        assertEquals(listOf(ContentGoalId.CALENDAR, ContentGoalId.NOTIFICATIONS), summary.needsAccess)
    }

    @Test
    fun noPermissionsStepWhenEverythingIsGranted() {
        val ctx = walkthroughContext()
        val review = ctx.toReview()
        assertFalse(WalkthroughStep.PERMISSIONS in WalkthroughFlow.steps(review, ctx))
        val summary = WalkthroughFlow.review(review, ctx)
        assertEquals(emptyList(), summary.needsAccess)
        assertTrue(summary.replacesDefault)
        assertEquals(4, summary.pages.size)
        assertTrue(summary.pages.first().placed && summary.pages.first().isStart)
        assertEquals(GoalRole.WIDGET, summary.pages[1].role)
    }

    @Test
    fun backOnTheWelcomeCancelsAndCancelClosesEverywhere() {
        val ctx = walkthroughContext()
        assertEquals(WalkthroughExit.Cancelled, ctx.run(WalkthroughAction.Back).exit)
        var state = WalkthroughFlow.start(ctx)
        while (state.exit == null) {
            assertEquals(
                WalkthroughExit.Cancelled,
                ctx.run(WalkthroughAction.Cancel, from = state).exit,
                "at ${state.step}",
            )
            state = ctx.run(WalkthroughAction.Next, from = state)
        }
        assertEquals(WalkthroughExit.Finished, state.exit)
        assertEquals(WalkthroughStep.DONE, state.step)
    }
}
