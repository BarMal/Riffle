package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.settings.HomeBehaviourSettings
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.workspace.ReturnBehavior
import com.riffle.core.domain.launcher.workspace.ReturnEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HomeReturnTest {
    private val grid = GridDimensions(columns = 4, rows = 5)

    private fun layout(
        pageCount: Int,
        selected: Int,
        editMode: HomeEditMode = HomeEditMode.Browsing,
    ): HomeLayout {
        val pages = (0 until pageCount).map { LauncherPage(id = LauncherPageId("p$it"), grid = grid) }
        return HomeLayout(
            viewMode = LauncherViewMode.STANDARD_APP_DRAWER,
            pages = pages,
            selectedPageId = pages[selected].id,
            dock = DockModel(capacity = 5, iconSizeDp = 44, itemSpacingDp = 8),
            editMode = editMode,
        )
    }

    private fun land(
        layout: HomeLayout,
        behavior: ReturnBehavior,
        event: ReturnEvent = ReturnEvent.RETURN,
        atTopLevel: Boolean = true,
    ) = HomeReturn.landingPage(layout, behavior, event, atTopLevel)

    @Test
    fun restoreOnReturnNeverMovesAnyPage() {
        (0..3).forEach { selected ->
            assertNull(land(layout(4, selected), ReturnBehavior.RESTORE), "page $selected")
        }
    }

    @Test
    fun restoreIsTheDefaultSoTheDefaultIsANoOp() {
        assertNull(HomeReturn.landingPage(layout(3, 2), ReturnBehavior.RESTORE))
    }

    @Test
    fun firstPageOnReturnLandsOnTheFirstPage() {
        assertEquals(LauncherPageId("p0"), land(layout(4, 2), ReturnBehavior.FIRST_PAGE))
    }

    @Test
    fun startPageMeansTheFirstPageInTheStandardShell() {
        assertEquals(LauncherPageId("p0"), land(layout(4, 3), ReturnBehavior.START_PAGE))
    }

    @Test
    fun alreadyOnTheFirstPageIsANoOp() {
        assertNull(land(layout(4, 0), ReturnBehavior.FIRST_PAGE))
        assertNull(land(layout(4, 0), ReturnBehavior.START_PAGE))
    }

    @Test
    fun homePressLandsOnTheFirstPageWhateverTheSetting() {
        ReturnBehavior.entries.forEach { behavior ->
            assertEquals(LauncherPageId("p0"), land(layout(4, 2), behavior, ReturnEvent.HOME_PRESS), "$behavior")
        }
    }

    @Test
    fun homePressAgreesWithTodaysResetToTheFirstPage() {
        // LauncherHomePageEditReducer.withDefaultHomeOpened selects pages.first(); the rule must agree.
        val layout = layout(5, 3)
        val today = layout.pages.first().id
        ReturnBehavior.entries.forEach { behavior ->
            assertEquals(today, land(layout, behavior, ReturnEvent.HOME_PRESS))
        }
    }

    @Test
    fun nothingMovesWhenNotAtTheTopLevel() {
        ReturnBehavior.entries.forEach { behavior ->
            ReturnEvent.entries.forEach { event ->
                assertNull(land(layout(4, 2), behavior, event, atTopLevel = false), "$behavior $event")
            }
        }
    }

    @Test
    fun anEditModeIsNotTheTopLevel() {
        val managing = layout(4, 2, HomeEditMode.ManagingPages)
        val editing = layout(4, 2, HomeEditMode.EditingPage(LauncherPageId("p2")))
        assertNull(HomeReturn.landingPage(managing, ReturnBehavior.FIRST_PAGE))
        assertNull(HomeReturn.landingPage(editing, ReturnBehavior.FIRST_PAGE))
    }

    @Test
    fun aSinglePageLayoutNeverMoves() {
        ReturnBehavior.entries.forEach { behavior ->
            assertNull(land(layout(1, 0), behavior), "$behavior")
            assertNull(land(layout(1, 0), behavior, ReturnEvent.HOME_PRESS), "$behavior")
        }
    }

    @Test
    fun blankPageIdsDoNotBreakTheRule() {
        val base = layout(3, 2)
        val blank = LauncherPageId("")
        val odd = base.copy(pages = base.pages.map { it.copy(id = blank) }, selectedPageId = blank)
        // Every id is the same blank value, so the selected page is the first match and nothing moves.
        assertNull(HomeReturn.landingPage(odd, ReturnBehavior.FIRST_PAGE))
    }

    private fun state(
        behavior: ReturnBehavior,
        destination: ShellDestination = ShellDestination.HOME,
        selected: Int = 2,
    ) = LauncherShellState(
        destination = destination,
        homeLayout = layout(4, selected),
        launcherSettings = LauncherSettings(home = HomeBehaviourSettings(behavior)),
    )

    @Test
    fun theDefaultStateNeverMoves() {
        assertNull(LauncherShellState(homeLayout = layout(4, 2)).homeReturnTarget())
    }

    @Test
    fun shellStateAppliesTheSettingAtTheTopLevel() {
        assertNull(state(ReturnBehavior.RESTORE).homeReturnTarget())
        assertEquals(LauncherPageId("p0"), state(ReturnBehavior.FIRST_PAGE).homeReturnTarget())
        assertEquals(LauncherPageId("p0"), state(ReturnBehavior.START_PAGE).homeReturnTarget())
    }

    @Test
    fun shellStateLeavesEveryOverlayAlone() {
        ShellDestination.entries.filter { it != ShellDestination.HOME }.forEach { destination ->
            ReturnBehavior.entries.forEach { behavior ->
                assertNull(state(behavior, destination).homeReturnTarget(), "$destination $behavior")
            }
        }
    }
}
