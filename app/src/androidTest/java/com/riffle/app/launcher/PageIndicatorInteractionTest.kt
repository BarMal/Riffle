package com.riffle.app.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PageIndicatorInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun announcesPagePositionAndSelectsPagesThroughAccessibleProgress() {
        val selectedPages = mutableListOf<Int>()
        composeRule.setContent {
            var selectedPageIndex by remember { mutableIntStateOf(1) }
            MaterialTheme {
                PageIndicator(
                    pageCount = 5,
                    selectedPageIndex = selectedPageIndex,
                    reducedMotion = true,
                    haptics = NoopLauncherHaptics,
                    onPageSelected = { pageIndex ->
                        selectedPageIndex = pageIndex
                        selectedPages += pageIndex
                    },
                )
            }
        }

        val indicator = composeRule.onNodeWithContentDescription("Page selector")
        indicator.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Page 2 of 5"))
        indicator.assertHeightIsAtLeast(48.dp)
        indicator.performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
            assertTrue(setProgress(4f))
        }

        composeRule.runOnIdle {
            assertEquals(listOf(4), selectedPages)
        }
        indicator.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Page 5 of 5"))
    }

    @Test
    fun dragSelectsTheVisuallyLastPageInLtr() {
        assertDragSelectsVisuallyLastPage(
            layoutDirection = LayoutDirection.Ltr,
            startAtLeft = true,
        )
    }

    @Test
    fun dragSelectsTheVisuallyLastPageInRtl() {
        assertDragSelectsVisuallyLastPage(
            layoutDirection = LayoutDirection.Rtl,
            startAtLeft = false,
        )
    }

    private fun assertDragSelectsVisuallyLastPage(
        layoutDirection: LayoutDirection,
        startAtLeft: Boolean,
    ) {
        val selectedPages = mutableListOf<Int>()
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                MaterialTheme {
                    PageIndicator(
                        pageCount = 5,
                        selectedPageIndex = 0,
                        reducedMotion = true,
                        haptics = NoopLauncherHaptics,
                        onPageSelected = selectedPages::add,
                    )
                }
            }
        }

        // The handle's own touch target is the drag surface, resting on the currently selected
        // page's dot -- for page 0 that is the track's own visual start, so a drag from as near
        // that edge as the node allows, spanning the node's own reported width, reaches the last
        // page exactly the way a real thumb drag across the whole track would.
        composeRule
            .onNodeWithContentDescription("Page selector")
            .performTouchInput {
                val startX = if (startAtLeft) 1f else width - 1f
                val endX = if (startAtLeft) width - 1f else 1f
                swipe(
                    start = Offset(startX, height / 2f),
                    end = Offset(endX, height / 2f),
                )
            }

        composeRule.runOnIdle {
            assertEquals(listOf(4), selectedPages)
        }
    }

    @Test
    fun dragReportsEachCrossedPageLiveBeforeCommittingOnceOnRelease() {
        val liveIndices = mutableListOf<Int>()
        val committedIndices = mutableListOf<Int>()
        composeRule.setContent {
            MaterialTheme {
                PageIndicator(
                    pageCount = 5,
                    selectedPageIndex = 0,
                    reducedMotion = true,
                    haptics = NoopLauncherHaptics,
                    onPageSelected = committedIndices::add,
                    onLiveDragPageChanged = liveIndices::add,
                )
            }
        }

        composeRule
            .onNodeWithContentDescription("Page selector")
            .performTouchInput {
                swipe(
                    start = Offset(1f, height / 2f),
                    end = Offset(width - 1f, height / 2f),
                )
            }

        composeRule.runOnIdle {
            // The whole point of the live callback is that the pager can already be following the
            // drag before it ends -- if it only ever fired once, at the same moment as the commit,
            // scrubbing would be no faster than the old drag-then-jump behaviour it replaced.
            assertTrue(liveIndices.isNotEmpty())
            assertEquals(4, liveIndices.last())
            assertEquals(listOf(4), committedIndices)
        }
    }
}
