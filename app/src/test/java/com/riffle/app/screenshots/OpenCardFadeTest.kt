package com.riffle.app.screenshots

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import com.riffle.app.launcher.GridFadeAlphaKey
import com.riffle.app.launcher.HOME_DOCK_SURFACE_TEST_TAG
import com.riffle.app.launcher.stream.STREAM_OPEN_CARD_TEST_TAG
import com.riffle.app.launcher.stream.STREAM_PAGER_TEST_TAG
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.settings.AppsBehindCards
import com.riffle.core.domain.launcher.stream.StreamGridFade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Device fixes, round 7, item H ("expanding a single card obscures apps it doesn't cover"): through the real shell on
 * the owner's unfolded window, one notification card open. The grid fades under what is drawn, with the short feather,
 * not under the column, which is taller than the card: a cell more than 32 dp from the card (and from the Dock) is
 * untouched, one under the card is hidden (Hidden, Around) or at the peek opacity (Behind and around).
 */
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = OWNER_UNFOLDED)
internal class OpenCardFadeTest : OpenCardPolishHarness(HomeLayoutDeviceClass.FOLDABLE, DockPosition.RIGHT) {
    private fun cells(): List<Pair<Rect, Float>> =
        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(GridFadeAlphaKey)).fetchSemanticsNodes()
            .map { it.boundsInRoot to it.config[GridFadeAlphaKey] }

    private fun Rect.distanceTo(other: Rect): Float {
        val dx = maxOf(left - other.right, other.left - right, 0f)
        val dy = maxOf(top - other.bottom, other.top - bottom, 0f)
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun assertTheFadeFollowsTheCard(
        setting: AppsBehindCards,
        what: String,
    ) {
        val card = checkNotNull(boundsOf(STREAM_OPEN_CARD_TEST_TAG)) { "$what: no open card" }
        val dock = checkNotNull(boundsOf(HOME_DOCK_SURFACE_TEST_TAG))
        val cells = cells()
        assertTrue("$what: the grid has cells (${cells.size})", cells.size > 8)
        val far =
            cells.filter { (rect, _) ->
                rect.distanceTo(card) > 32f * density && rect.distanceTo(dock) > 32f * density
            }
        assertTrue("$what: some cells are far from the card (${far.size})", far.isNotEmpty())
        far.forEach { (rect, alpha) ->
            assertEquals(
                "$what: a cell at ${rect.left / density},${rect.top / density} dp is more than 32 dp from the card " +
                    "(${card.left / density},${card.top / density} - ${card.right / density}," +
                    "${card.bottom / density}) and untouched",
                1f,
                alpha,
                0.001f,
            )
        }
        val deep = cells.filter { (rect, _) -> card.deflate(64f * density).contains(rect.center) }
        assertTrue("$what: some cells are under the card (${deep.size})", deep.isNotEmpty())
        val expected = if (setting == AppsBehindCards.BEHIND_AND_AROUND) StreamGridFade.PEEK_ALPHA else 0f
        deep.forEach { (rect, alpha) ->
            assertEquals("$what: a cell under the card at ${rect.center}", expected, alpha, 0.02f)
        }
    }

    private fun oneCardOpen(setting: AppsBehindCards) {
        show(landOn = mailPage, waitFor = "Ledger", mails = 1, appsBehind = setting)
        composeRule.setSystemBarInsets(28, 24)
        composeRule.waitForIdle()
        tapFront("Ledger")
        composeRule.waitForIdle()
        assertTheFadeFollowsTheCard(setting, "one card, $setting")
    }

    @Test
    fun withAppsBehindAndAroundOneOpenCardFadesOnlyWhatItCovers() = oneCardOpen(AppsBehindCards.BEHIND_AND_AROUND)

    @Test
    fun withAppsAroundTheCardsOneOpenCardFadesOnlyWhatItCovers() = oneCardOpen(AppsBehindCards.AROUND)

    @Test
    fun withAppsHiddenOneOpenCardFadesOnlyWhatItCovers() = oneCardOpen(AppsBehindCards.HIDDEN)

    @Test
    fun afterAFourCardPageThePageOfOneCardLeavesNoStaleRectangleBehind() {
        val setting = AppsBehindCards.BEHIND_AND_AROUND
        show(more = 1, mails = 1, appsBehind = setting)
        composeRule.setSystemBarInsets(28, 24)
        composeRule.waitForIdle()
        tapFront()
        composeRule.waitForIdle()
        composeRule.runOnUiThread { stream.dock.openCards.closeByPress() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(STREAM_PAGER_TEST_TAG, useUnmergedTree = true).performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        if (composeRule.onAllNodesWithText("Ledger").fetchSemanticsNodes().isEmpty()) {
            composeRule.onNodeWithTag(STREAM_PAGER_TEST_TAG, useUnmergedTree = true).performTouchInput { swipeRight() }
            composeRule.waitForIdle()
        }
        tapFront("Ledger")
        composeRule.waitForIdle()
        assertTheFadeFollowsTheCard(setting, "after the four-card page")
    }

    @Test
    fun unfoldedOneOpenCardWithAppsBehindAndAroundTheCardsLight() = shoot()

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun unfoldedOneOpenCardWithAppsBehindAndAroundTheCardsDark() = shoot()

    private fun shoot() {
        oneCardOpen(AppsBehindCards.BEHIND_AND_AROUND)
        composeRule.captureScreen()
    }
}
